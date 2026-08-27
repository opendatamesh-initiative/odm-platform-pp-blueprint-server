package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.updatedataproduct;

import com.fasterxml.jackson.databind.JsonNode;
import org.opendatamesh.platform.git.exceptions.GitException;
import org.opendatamesh.platform.git.model.Repository;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.BlueprintRepo;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.BlueprintGitNamingConventions;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate.SourceRepositoryDto;
import org.opendatamesh.platform.pp.blueprint.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.blueprint.exceptions.InternalException;
import org.opendatamesh.platform.pp.blueprint.exceptions.NotFoundException;
import org.opendatamesh.platform.pp.blueprint.utils.usecases.UseCase;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

class UpdateDataProductFromBlueprintVersion implements UseCase {

    static final String PARENT_SOURCE_ID = "__parent__";

    private final UpdateDataProductCommand command;
    private final UpdateDataProductPresenter presenter;
    private final UpdateDataProductPersistencyOutboundPort persistencyPort;
    private final UpdateDataProductManifestOutboundPort manifestPort;
    private final UpdateDataProductTemplatingOutboundPort templatingPort;
    private final UpdateDataProductGitOutboundPort gitPort;

    private final List<String> warnings = new ArrayList<>();

    UpdateDataProductFromBlueprintVersion(
            UpdateDataProductCommand command,
            UpdateDataProductPresenter presenter,
            UpdateDataProductPersistencyOutboundPort persistencyPort,
            UpdateDataProductManifestOutboundPort manifestPort,
            UpdateDataProductTemplatingOutboundPort templatingPort,
            UpdateDataProductGitOutboundPort gitPort
    ) {
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

        BlueprintVersion currentVersion = persistencyPort.findByBlueprintNameAndVersion(command.blueprintName(), command.currentVersionNumber());
        BlueprintVersion nextVersion = persistencyPort.findByBlueprintNameAndVersion(command.blueprintName(), command.nextVersionNumber());

        if (!currentVersion.getBlueprint().getUuid().equals(nextVersion.getBlueprint().getUuid())) {
            throw new BadRequestException(
                    "Current and next blueprint versions must belong to the same blueprint");
        }

        collectAndThrowIfAnyIssues(manifestPort.collectValidationIssues(currentVersion, nextVersion, command.parameters(), command.targetRepositories()));

        Map<String, JsonNode> nextParentParameters = manifestPort.enrichRequestParametersWithDefaultsIfNeeded(nextVersion.getContent(), command.parameters());

        Map<String, BlueprintVersion> modulesByAlias = retrieveModulesBlueprintVersions(nextVersion);
        validateModulesBlueprintVersions(nextVersion, modulesByAlias);

        collectAndThrowIfAnyIssues(manifestPort.collectProviderMismatchIssues(nextVersion, modulesByAlias));
        collectAndThrowIfAnyIssues(manifestPort.collectModuleParameterResolutionIssues(nextVersion.getContent(), nextParentParameters));

        Map<String, Map<String, JsonNode>> modulesParameters = manifestPort.resolveModuleParameters(nextVersion.getContent(), nextParentParameters);

        Map<String, UpdateDataProductTargetRepositoryDto> targetsByKey = indexTargetRepositoriesByKey(command.targetRepositories());
        List<UpdateRoute> routes = manifestPort.flattenRoutes(nextVersion.getContent());
        Map<String, List<UpdateRoute>> routesByTargetKey = groupRoutesByTargetRepository(routes);
        String rootTargetRepositoryKey = manifestPort.retrieveRootTargetRepositoryKey(nextVersion.getContent());
        List<SourceRepositoryDto> sourceRepositories = sourceRepositoriesForRoutes(nextVersion, modulesByAlias, routes);

        List<UpdateDataProductTargetResult> results = new ArrayList<>();
        gitPort.openSources(nextVersion.getBlueprint(), sourceRepositories, sourcePaths -> {
            for (Map.Entry<String, List<UpdateRoute>> entry : routesByTargetKey.entrySet()) {
                UpdateDataProductTargetRepositoryDto targetRepository = requireTargetRepository(targetsByKey, entry.getKey());
                results.add(updateTargetRepository(
                        nextVersion,
                        nextParentParameters,
                        modulesParameters,
                        rootTargetRepositoryKey,
                        sourcePaths,
                        targetRepository,
                        entry.getValue()));
            }
        });

        presenter.presentResult(new UpdateDataProductResult(results, warnings));
    }

    private Map<String, UpdateDataProductTargetRepositoryDto> indexTargetRepositoriesByKey(
            List<UpdateDataProductTargetRepositoryDto> targetRepositories) {
        Map<String, UpdateDataProductTargetRepositoryDto> targetsByKey = new LinkedHashMap<>();
        for (UpdateDataProductTargetRepositoryDto target : targetRepositories) {
            targetsByKey.put(target.targetId(), target);
        }
        return targetsByKey;
    }

    private Map<String, List<UpdateRoute>> groupRoutesByTargetRepository(List<UpdateRoute> routes) {
        Map<String, List<UpdateRoute>> routesByTargetKey = new LinkedHashMap<>();
        for (UpdateRoute route : routes) {
            routesByTargetKey.computeIfAbsent(route.repositoryKey(), ignored -> new ArrayList<>()).add(route);
        }
        return routesByTargetKey;
    }

    private UpdateDataProductTargetResult updateTargetRepository(
            BlueprintVersion nextVersion,
            Map<String, JsonNode> nextParentParameters,
            Map<String, Map<String, JsonNode>> modulesParameters,
            String rootTargetRepositoryKey,
            Map<String, Path> sourcePaths,
            UpdateDataProductTargetRepositoryDto targetRepository,
            List<UpdateRoute> routes) {
        if (routes.isEmpty()) {
            throw new InternalException(
                    "Update target loop invoked with no routes for key '%s'".formatted(targetRepository.targetId()));
        }

        String targetRepositoryKey = targetRepository.targetId();

        String currentCheckpointTag = BlueprintGitNamingConventions.checkpointTag(command.currentVersionNumber());
        String nextCheckpointTag = BlueprintGitNamingConventions.checkpointTag(command.nextVersionNumber());
        String updateBranchName = BlueprintGitNamingConventions.updateBranchName(command.nextVersionNumber());
        String commitMessage = "Update data product from blueprint %s@%s -> %s".formatted(command.blueprintName(), command.currentVersionNumber(), command.nextVersionNumber());
        AtomicReference<String> createdCommitHash = new AtomicReference<>();

        gitPort.openTargetAtCheckpoint(
                targetRepository,
                currentCheckpointTag,
                targetPath -> {
                    gitPort.createAndCheckoutBranch(targetPath, updateBranchName);
                    gitPort.cleanWorkingTreePreservingGit(targetPath);
                    renderRoutedSources(targetRepositoryKey, routes, sourcePaths, targetPath, nextParentParameters, modulesParameters);
                    renderDescriptorAndLineageOnRootRepository(nextVersion, nextParentParameters, rootTargetRepositoryKey, targetRepositoryKey, sourcePaths, targetPath);

                    String commitHash = gitPort.commitAll(targetPath, updateBranchName, commitMessage, command.commitAuthorName(), command.commitAuthorEmail());
                    gitPort.createCheckpointTag(targetPath, nextCheckpointTag, commitHash, command.commitAuthorName(), command.commitAuthorEmail());
                    gitPort.pushBranch(targetPath, updateBranchName);
                    gitPort.pushTag(targetPath, nextCheckpointTag);
                    createdCommitHash.set(commitHash);
                });

        String commitHash = createdCommitHash.get();
        if (commitHash == null) {
            throw new InternalException("Update from checkpoint completed without producing a commit hash");
        }

        UpdateTargetGitResult updateGitResult = new UpdateTargetGitResult(updateBranchName, nextCheckpointTag, commitHash);
        String pullRequestWebUrl = null;
        if (command.createPullRequest()) {
            pullRequestWebUrl = tryOpenPullRequest(targetRepository, updateGitResult, currentCheckpointTag, nextCheckpointTag);
        }

        return new UpdateDataProductTargetResult(targetRepository.targetId(), targetRepository.repository(), updateGitResult.updateBranchName(), updateGitResult.checkpointTag(), updateGitResult.commitHash(), pullRequestWebUrl);
    }

    private UpdateDataProductTargetRepositoryDto requireTargetRepository(
            Map<String, UpdateDataProductTargetRepositoryDto> targetsByKey,
            String targetRepositoryKey) {
        UpdateDataProductTargetRepositoryDto targetRepository = targetsByKey.get(targetRepositoryKey);
        if (targetRepository == null) {
            throw new InternalException(
                    "Missing target repository mapping for key '%s' after validation".formatted(targetRepositoryKey));
        }
        return targetRepository;
    }

    private List<SourceRepositoryDto> sourceRepositoriesForRoutes(
            BlueprintVersion nextVersion,
            Map<String, BlueprintVersion> modulesByAlias,
            List<UpdateRoute> routes) {
        Set<String> requiredSourceIds = routes.stream()
                .map(UpdateRoute::sourceId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return manifestPort.retrieveAllSourceRepositories(nextVersion, nextVersion.getContent(), modulesByAlias)
                .stream()
                .filter(source -> source != null && requiredSourceIds.contains(source.id()))
                .toList();
    }

    private void renderRoutedSources(
            String targetRepositoryKey,
            List<UpdateRoute> routes,
            Map<String, Path> sourcePaths,
            Path targetPath,
            Map<String, JsonNode> nextParentParameters,
            Map<String, Map<String, JsonNode>> modulesParameters) {
        for (UpdateRoute route : routes) {
            Path sourceRoot = sourcePaths.get(route.sourceId());
            if (sourceRoot == null) {
                throw new InternalException(
                        "Source workspace missing for sourceId '%s' when updating repository key '%s'"
                                .formatted(route.sourceId(), targetRepositoryKey));
            }
            Map<String, JsonNode> params = route.fromParent()
                    ? nextParentParameters
                    : modulesParameters.getOrDefault(route.sourceId(), Map.of());
            templatingPort.applyRoute(sourceRoot, route.sourcePath(), targetPath, route.destinationPath(), params);
        }
    }

    private void renderDescriptorAndLineageOnRootRepository(
            BlueprintVersion nextVersion,
            Map<String, JsonNode> nextParentParameters,
            String rootTargetRepositoryKey,
            String targetRepositoryKey,
            Map<String, Path> sourcePaths,
            Path targetPath) {
        BlueprintRepo parentRepo = nextVersion.getBlueprint().getBlueprintRepo();
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
        templatingPort.renderDescriptorToRoot(parentSourceRoot, parentRepo.getDescriptorTemplatePath(), targetPath, nextParentParameters);
        templatingPort.recordParentLineage(targetPath, nextVersion, nextParentParameters);
    }

    private Map<String, BlueprintVersion> retrieveModulesBlueprintVersions(BlueprintVersion nextVersion) {
        Map<String, BlueprintVersion> modulesByAlias = new HashMap<>();
        List<UpdateValidationIssue> retrieveIssues = new ArrayList<>();

        for (UpdateCompositionIdentity composition : manifestPort.listCompositionIdentities(nextVersion.getContent())) {
            try {
                modulesByAlias.put(
                        composition.moduleAlias(),
                        persistencyPort.findModuleBlueprintVersion(
                                composition.blueprintName(), composition.blueprintVersion()));
            } catch (NotFoundException e) {
                retrieveIssues.add(new UpdateValidationIssue(
                        composition.fieldPath(),
                        e.getMessage(),
                        "Publish the module version first."));
            }
        }
        collectAndThrowIfAnyIssues(retrieveIssues);
        return modulesByAlias;
    }

    private void validateModulesBlueprintVersions(
            BlueprintVersion nextVersion,
            Map<String, BlueprintVersion> modulesByAlias) {
        List<UpdateValidationIssue> validationIssues = new ArrayList<>();

        for (UpdateCompositionIdentity composition : manifestPort.listCompositionIdentities(nextVersion.getContent())) {
            String alias = composition.moduleAlias();
            BlueprintVersion moduleBlueprintVersion = modulesByAlias.get(alias);
            if (moduleBlueprintVersion == null) {
                continue;
            }
            if (!manifestPort.isMonorepoNoComposition(moduleBlueprintVersion.getContent())) {
                validationIssues.add(new UpdateValidationIssue(
                        composition.fieldPath(),
                        "Composition module '%s' (%s@%s) is not a monorepo with no composition"
                                .formatted(alias, composition.blueprintName(), composition.blueprintVersion()),
                        "Composition modules must be monorepo with no composition (one repository key, empty composition)."));
            }
        }
        collectAndThrowIfAnyIssues(validationIssues);
    }

    private void collectAndThrowIfAnyIssues(List<UpdateValidationIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return;
        }
        String message = "Blueprint update validation failed:\n  "
                + issues.stream().map(UpdateValidationIssue::format).collect(Collectors.joining("\n  "));
        throw new BadRequestException(message);
    }

    private String tryOpenPullRequest(
            UpdateDataProductTargetRepositoryDto target,
            UpdateTargetGitResult gitResult,
            String currentTag,
            String nextTag
    ) {
        String prTarget = StringUtils.hasText(target.pullRequestTargetBranch())
                ? target.pullRequestTargetBranch()
                : target.repository().getDefaultBranch();
        try {
            return gitPort.openPullRequest(
                    target.repository(),
                    gitResult.updateBranchName(),
                    prTarget,
                    "Update data product to blueprint %s@%s"
                            .formatted(command.blueprintName(), command.nextVersionNumber()),
                    "Automated update from blueprint checkpoint %s to %s."
                            .formatted(currentTag, nextTag));
        } catch (GitException e) {
            warnings.add(buildPullRequestWarning(target.repository(), gitResult, e));
            return null;
        }
    }

    private String buildPullRequestWarning(Repository repository, UpdateTargetGitResult gitResult, RuntimeException e) {
        String repoIdentity = StringUtils.hasText(repository.getName())
                ? repository.getName()
                : (StringUtils.hasText(repository.getCloneUrlHttp()) ? repository.getCloneUrlHttp() : repository.getId());
        String cause = StringUtils.hasText(e.getMessage()) ? e.getMessage() : e.getClass().getSimpleName();
        return ("Pull request creation failed for repository '%s' after update branch '%s' and checkpoint tag '%s' were already pushed: %s")
                .formatted(repoIdentity, gitResult.updateBranchName(), gitResult.checkpointTag(), cause);
    }

    private void validateCommand(UpdateDataProductCommand command) {
        if (command == null) {
            throw new BadRequestException("Update data product command is required");
        }
        if (!StringUtils.hasText(command.blueprintName())) {
            throw new BadRequestException("Blueprint name is required");
        }
        if (!StringUtils.hasText(command.currentVersionNumber())) {
            throw new BadRequestException("Current blueprint version number is required");
        }
        if (!StringUtils.hasText(command.nextVersionNumber())) {
            throw new BadRequestException("Next blueprint version number is required");
        }
        if (command.currentVersionNumber().equals(command.nextVersionNumber())) {
            throw new BadRequestException("Current and next blueprint version numbers must be different");
        }
        if (command.parameters() == null) {
            throw new BadRequestException("Blueprint parameters are required");
        }
        if (command.targetRepositories() == null || command.targetRepositories().isEmpty()) {
            throw new BadRequestException("At least one target repository is required");
        }
        for (int i = 0; i < command.targetRepositories().size(); i++) {
            UpdateDataProductTargetRepositoryDto target = command.targetRepositories().get(i);
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
