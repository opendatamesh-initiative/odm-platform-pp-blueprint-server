package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate;

import com.fasterxml.jackson.databind.JsonNode;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.BlueprintRepo;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.BlueprintGitNamingConventions;
import org.opendatamesh.platform.pp.blueprint.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.blueprint.exceptions.InternalException;
import org.opendatamesh.platform.pp.blueprint.exceptions.NotFoundException;
import org.opendatamesh.platform.pp.blueprint.utils.usecases.UseCase;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

class InstantiateBlueprintVersion implements UseCase {

    static final String PARENT_SOURCE_ID = "__parent__";

    private final InstantiateBlueprintVersionCommand command;
    private final InstantiateBlueprintVersionPresenter presenter;
    private final InstantiateBlueprintVersionPersistencyOutboundPort persistencyPort;
    private final InstantiateBlueprintVersionManifestOutboundPort manifestPort;
    private final InstantiateBlueprintVersionTemplatingOutboundPort templatingPort;
    private final InstantiateBlueprintVersionGitOutboundPort gitPort;

    InstantiateBlueprintVersion(
            InstantiateBlueprintVersionCommand command,
            InstantiateBlueprintVersionPresenter presenter,
            InstantiateBlueprintVersionPersistencyOutboundPort persistencyPort,
            InstantiateBlueprintVersionManifestOutboundPort manifestPort,
            InstantiateBlueprintVersionTemplatingOutboundPort templatingPort,
            InstantiateBlueprintVersionGitOutboundPort gitPort) {
        this.command = command;
        this.presenter = presenter;
        this.persistencyPort = persistencyPort;
        this.manifestPort = manifestPort;
        this.templatingPort = templatingPort;
        this.gitPort = gitPort;
    }

    @Override
    public void execute() {
        validateCommand(command);

        BlueprintVersion parentBlueprintVersion = persistencyPort.findByBlueprintNameAndVersion(command.blueprintName(), command.blueprintVersion());
        validateBlueprintManifest(parentBlueprintVersion);

        Map<String, JsonNode> parentParameters = enrichRequestParametersWithDefaultsIfNeeded(parentBlueprintVersion);
        Map<String, BlueprintVersion> modulesByAlias = retrieveModulesBlueprintVersions(parentBlueprintVersion);
        validateModulesBlueprintVersions(parentBlueprintVersion, modulesByAlias);
        throwIfAnyIssues(manifestPort.collectModuleParameterResolutionIssues(
                parentBlueprintVersion.getContent(), parentParameters));
        Map<String, Map<String, JsonNode>> modulesParameters = manifestPort.resolveModuleParameters(
                parentBlueprintVersion.getContent(), parentParameters);

        Map<String, TargetRepositoryDto> targetsByKey = indexTargetRepositoriesByKey(command.targetRepositories());
        List<InstantiationRoute> routes = manifestPort.flattenRoutes(parentBlueprintVersion.getContent());
        Map<String, List<InstantiationRoute>> routesByTargetKey = groupRoutesByTargetRepository(routes);
        String rootTargetRepositoryKey = manifestPort.retrieveRootTargetRepositoryKey(parentBlueprintVersion.getContent());
        List<SourceRepositoryDto> sourceRepositories = sourceRepositoriesForRoutes(parentBlueprintVersion, modulesByAlias, routes);

        gitPort.openSources(parentBlueprintVersion.getBlueprint(), sourceRepositories, sourcePaths -> {
            for (Map.Entry<String, List<InstantiationRoute>> entry : routesByTargetKey.entrySet()) {
                TargetRepositoryDto targetRepository = requireTargetRepository(targetsByKey, entry.getKey());
                instantiateTargetRepository(
                        parentBlueprintVersion,
                        parentParameters,
                        modulesParameters,
                        modulesByAlias,
                        rootTargetRepositoryKey,
                        sourcePaths,
                        targetRepository,
                        entry.getValue());
            }
        });

        presenter.presentResults(new InstantiateBlueprintVersionResult());
    }

    private Map<String, JsonNode> enrichRequestParametersWithDefaultsIfNeeded(BlueprintVersion parentBlueprintVersion) {
        return manifestPort.enrichRequestParametersWithDefaultsIfNeeded(
                parentBlueprintVersion.getContent(), command.blueprintParameters());
    }

    private Map<String, TargetRepositoryDto> indexTargetRepositoriesByKey(List<TargetRepositoryDto> targetRepositories) {
        Map<String, TargetRepositoryDto> targetsByKey = new LinkedHashMap<>();
        for (TargetRepositoryDto target : targetRepositories) {
            targetsByKey.put(target.targetId(), target);
        }
        return targetsByKey;
    }

    private Map<String, List<InstantiationRoute>> groupRoutesByTargetRepository(List<InstantiationRoute> routes) {
        Map<String, List<InstantiationRoute>> routesByTargetKey = new LinkedHashMap<>();
        for (InstantiationRoute route : routes) {
            routesByTargetKey.computeIfAbsent(route.repositoryKey(), ignored -> new ArrayList<>()).add(route);
        }
        return routesByTargetKey;
    }

    /**
     * Instantiates a single target repository, one manifest repository key at a
     * time.
     * <p>
     * The rendered content is always built on a throwaway orphan branch, tagged as
     * the blueprint checkpoint and only then merged into the integration branch, so
     * the checkpoint stays a faithful snapshot of the blueprint output regardless
     * of
     * what the target repository already contains.
     */
    private void instantiateTargetRepository(
            BlueprintVersion parentBlueprintVersion,
            Map<String, JsonNode> parentParameters,
            Map<String, Map<String, JsonNode>> modulesParameters,
            Map<String, BlueprintVersion> modulesByAlias,
            String rootTargetRepositoryKey,
            Map<String, Path> sourcePaths,
            TargetRepositoryDto targetRepository,
            List<InstantiationRoute> routes) {
        if (routes.isEmpty()) {
            return;
        }

        String targetRepositoryKey = targetRepository.targetId();
        String integrationBranch = resolveIntegrationBranch(targetRepository);
        String checkpointBranch = BlueprintGitNamingConventions.orphanInitBranchName();

        gitPort.openTarget(
                targetRepository,
                integrationBranch,
                targetPath -> {
                    gitPort.createAndCheckoutOrphanBranch(targetPath, checkpointBranch);
                    renderRoutedSources(targetRepositoryKey, routes, sourcePaths, targetPath, parentParameters, modulesParameters);
                    relocateModuleReferencedFiles(targetPath, routes, sourcePaths, modulesByAlias);
                    renderDescriptorAndLineageOnRootRepository(parentBlueprintVersion, parentParameters, rootTargetRepositoryKey, targetRepositoryKey, sourcePaths, targetPath);
                    String checkpointTag = commitAndTagCheckpoint(targetPath, checkpointBranch);
                    publishCheckpointOnIntegrationBranch(targetPath, checkpointBranch, integrationBranch, checkpointTag);
                });
    }

    private TargetRepositoryDto requireTargetRepository(
            Map<String, TargetRepositoryDto> targetsByKey,
            String targetRepositoryKey) {
        TargetRepositoryDto targetRepository = targetsByKey.get(targetRepositoryKey);
        if (targetRepository == null) {
            throw new InternalException(
                    "Missing target repository mapping for key '%s' after validation".formatted(targetRepositoryKey));
        }
        return targetRepository;
    }

    private List<SourceRepositoryDto> sourceRepositoriesForRoutes(
            BlueprintVersion parentBlueprintVersion,
            Map<String, BlueprintVersion> modulesByAlias,
            List<InstantiationRoute> routes) {
        Set<String> requiredSourceIds = routes.stream()
                .map(InstantiationRoute::sourceId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return manifestPort
                .retrieveAllSourceRepositories(
                        parentBlueprintVersion, parentBlueprintVersion.getContent(), modulesByAlias)
                .stream()
                .filter(source -> source != null && requiredSourceIds.contains(source.id()))
                .toList();
    }

    private String resolveIntegrationBranch(TargetRepositoryDto targetRepository) {
        return StringUtils.hasText(targetRepository.branch())
                ? targetRepository.branch()
                : targetRepository.repository().getDefaultBranch();
    }

    private void renderRoutedSources(
            String targetRepositoryKey,
            List<InstantiationRoute> routes,
            Map<String, Path> sourcePaths,
            Path targetPath,
            Map<String, JsonNode> parentParameters,
            Map<String, Map<String, JsonNode>> modulesParameters) {
        for (InstantiationRoute route : routes) {
            Path sourceRoot = sourcePaths.get(route.sourceId());
            if (sourceRoot == null) {
                throw new InternalException(
                        "Source workspace missing for sourceId '%s' when instantiating repository key '%s'"
                                .formatted(route.sourceId(), targetRepositoryKey));
            }
            Map<String, JsonNode> params = isParentRoute(route)
                    ? parentParameters
                    : modulesParameters.getOrDefault(route.sourceId(), Map.of());
            templatingPort.applyRoute(sourceRoot, route.sourcePath(), targetPath, route.destinationPath(), params);
        }
    }

    private void relocateModuleReferencedFiles(
            Path targetPath,
            List<InstantiationRoute> routes,
            Map<String, Path> sourcePaths,
            Map<String, BlueprintVersion> modulesByAlias) {
        Set<String> relocatedAliases = new LinkedHashSet<>();
        for (InstantiationRoute route : routes) {
            if (isParentRoute(route) || !relocatedAliases.add(route.sourceId())) {
                continue;
            }
            BlueprintVersion moduleVersion = modulesByAlias.get(route.sourceId());
            if (moduleVersion == null) {
                continue;
            }
            List<String> destinationPaths = routes.stream()
                    .filter(candidate -> route.sourceId().equals(candidate.sourceId()))
                    .map(InstantiationRoute::destinationPath)
                    .toList();
            templatingPort.relocateModuleReferencedFiles(
                    targetPath,
                    route.sourceId(),
                    moduleVersion,
                    sourcePaths.get(route.sourceId()),
                    destinationPaths);
        }
    }

    /**
     * The descriptor and its blueprint lineage metadata belong only to the target
     * mapped to {@code instantiation.root.repository}, and the descriptor is
     * rendered at the same path it occupies in the blueprint source repository
     * ({@code descriptorTemplatePath}).
     */
    private void renderDescriptorAndLineageOnRootRepository(
            BlueprintVersion parentBlueprintVersion,
            Map<String, JsonNode> parentParameters,
            String rootTargetRepositoryKey,
            String targetRepositoryKey,
            Map<String, Path> sourcePaths,
            Path targetPath) {
        BlueprintRepo parentRepo = parentBlueprintVersion.getBlueprint().getBlueprintRepo();
        if (!targetRepositoryKey.equals(rootTargetRepositoryKey)
                || !StringUtils.hasText(parentRepo.getDescriptorTemplatePath())) {
            return;
        }

        Path parentSourceRoot = sourcePaths.get(PARENT_SOURCE_ID);
        if (parentSourceRoot == null) {
            throw new InternalException(
                    "Parent source workspace missing when rendering descriptor on root key '%s'"
                            .formatted(targetRepositoryKey));
        }
        templatingPort.renderDescriptorToRoot(parentSourceRoot, parentRepo.getDescriptorTemplatePath(), targetPath,
                parentParameters);
        templatingPort.recordParentLineage(targetPath, parentBlueprintVersion, parentParameters);
    }

    private boolean isParentRoute(InstantiationRoute route) {
        return PARENT_SOURCE_ID.equals(route.sourceId());
    }

    private String commitAndTagCheckpoint(Path targetPath, String checkpointBranch) {
        String commitMessage = "Populate repository from blueprint " + command.blueprintName() + "@" + command.blueprintVersion();
        String checkpointTag = BlueprintGitNamingConventions.checkpointTag(command.blueprintVersion());
        String commitHash = gitPort.commitAll(targetPath, checkpointBranch, commitMessage, command.commitAuthorName(), command.commitAuthorEmail());
        gitPort.createCheckpointTag(targetPath, checkpointTag, commitHash, command.commitAuthorName(), command.commitAuthorEmail());
        return checkpointTag;
    }

    private void publishCheckpointOnIntegrationBranch(
            Path targetPath,
            String checkpointBranch,
            String integrationBranch,
            String checkpointTag) {
        gitPort.mergeBranch(targetPath, checkpointBranch, integrationBranch);
        gitPort.pushBranch(targetPath, integrationBranch);
        gitPort.pushTag(targetPath, checkpointTag);
    }

    private Map<String, BlueprintVersion> retrieveModulesBlueprintVersions(BlueprintVersion rootBlueprintVersion) {
        Map<String, BlueprintVersion> modulesByAlias = new HashMap<>();
        List<InstantiationValidationIssue> retrieveIssues = new ArrayList<>();

        for (InstantiationCompositionIdentity composition : manifestPort
                .listCompositionIdentities(rootBlueprintVersion.getContent())) {
            try {
                modulesByAlias.put(
                        composition.moduleAlias(),
                        persistencyPort.findModuleBlueprintVersion(
                                composition.blueprintName(), composition.blueprintVersion()));
            } catch (NotFoundException e) {
                retrieveIssues.add(new InstantiationValidationIssue(
                        composition.fieldPath(),
                        e.getMessage(),
                        "Publish the module version first."));
            }
        }
        throwIfAnyIssues(retrieveIssues);
        return modulesByAlias;
    }

    private void validateModulesBlueprintVersions(BlueprintVersion rootBlueprintVersion, Map<String, BlueprintVersion> modulesByAlias) {
        List<InstantiationValidationIssue> validationIssues = new ArrayList<>();

        for (InstantiationCompositionIdentity composition : manifestPort.listCompositionIdentities(rootBlueprintVersion.getContent())) {
            String alias = composition.moduleAlias();
            BlueprintVersion moduleBlueprintVersion = modulesByAlias.get(alias);
            if (moduleBlueprintVersion == null) {
                continue;
            }
            if (!manifestPort.isMonorepoNoComposition(moduleBlueprintVersion.getContent())) {
                validationIssues.add(new InstantiationValidationIssue(
                        composition.fieldPath(),
                        "Composition module '%s' (%s@%s) is not a monorepo with no composition"
                                .formatted(alias, composition.blueprintName(), composition.blueprintVersion()),
                        "Composition modules must be monorepo with no composition (one repository key, empty composition)."));
            }
            if (hasDescriptorTemplatePath(moduleBlueprintVersion)) {
                validationIssues.add(new InstantiationValidationIssue(
                        composition.fieldPath(),
                        "Composition module '%s' (%s@%s) declares descriptorTemplatePath"
                                .formatted(alias, composition.blueprintName(), composition.blueprintVersion()),
                        "Only the parent (root) blueprint may have descriptorTemplatePath; remove it from the module."));
            }
        }
        throwIfAnyIssues(validationIssues);
    }

    private boolean hasDescriptorTemplatePath(BlueprintVersion version) {
        if (version == null || version.getBlueprint() == null || version.getBlueprint().getBlueprintRepo() == null) {
            return false;
        }
        return StringUtils.hasText(version.getBlueprint().getBlueprintRepo().getDescriptorTemplatePath());
    }

    private void validateBlueprintManifest(BlueprintVersion parentVersion) {
        List<InstantiationValidationIssue> manifestValidationIssues = manifestPort.collectValidationIssues(
                parentVersion.getSpec(),
                parentVersion.getSpecVersion(),
                parentVersion.getContent(),
                command.blueprintParameters(),
                command.targetRepositories());
        throwIfAnyIssues(manifestValidationIssues);
    }

    private void throwIfAnyIssues(List<InstantiationValidationIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return;
        }
        String message = "Blueprint instantiation validation failed:\n  "
                + issues.stream().map(InstantiationValidationIssue::format).collect(Collectors.joining("\n  "));
        throw new BadRequestException(message);
    }

    private void validateCommand(InstantiateBlueprintVersionCommand command) {
        if (command == null) {
            throw new BadRequestException("Instantiate command is required");
        }
        if (!StringUtils.hasText(command.blueprintName())) {
            throw new BadRequestException("Blueprint name is required");
        }
        if (!StringUtils.hasText(command.blueprintVersion())) {
            throw new BadRequestException("Blueprint version is required");
        }
        if (command.targetRepositories() == null || command.targetRepositories().isEmpty()) {
            throw new BadRequestException("At least one target repository is required");
        }
        if (command.blueprintParameters() == null) {
            throw new BadRequestException("Blueprint parameters are required");
        }
        for (int i = 0; i < command.targetRepositories().size(); i++) {
            TargetRepositoryDto target = command.targetRepositories().get(i);
            if (target == null) {
                throw new BadRequestException("Target repository at index %s is required".formatted(i));
            }
            if (!StringUtils.hasText(target.targetId())) {
                throw new BadRequestException("Target repository targetId is required at index %s".formatted(i));
            }
            if (target.repository() == null) {
                throw new BadRequestException("Target repository reference is required at index %s".formatted(i));
            }
        }
    }

}
