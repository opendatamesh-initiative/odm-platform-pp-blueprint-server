package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate;

import com.fasterxml.jackson.databind.JsonNode;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.BlueprintGitNamingConventions;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.InstantiationScenario;
import org.opendatamesh.platform.pp.blueprint.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.blueprint.exceptions.InternalException;
import org.opendatamesh.platform.pp.blueprint.manifest.model.Manifest;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestInstantiation;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestParameter;
import org.opendatamesh.platform.pp.blueprint.manifest.parser.ManifestParserFactory;
import org.opendatamesh.platform.pp.blueprint.utils.usecases.UseCase;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class InstantiateBlueprintVersion implements UseCase {

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
        BlueprintVersion blueprintVersion = persistencyPort.findByBlueprintNameAndVersion(
                command.blueprintName(), command.blueprintVersion());
        Manifest manifest = parseManifest(blueprintVersion);
        InstantiationScenario scenario = resolveScenario(manifest);

        manifestPort.validateManifestAndParameters(
                blueprintVersion.getSpec(),
                blueprintVersion.getSpecVersion(),
                blueprintVersion.getContent(),
                command.blueprintParameters());

        switch (scenario) {
            case MONOREPO_NO_COMPOSITION -> instantiateMonorepoNoComposition(blueprintVersion, manifest);
            case MONOREPO_WITH_COMPOSITION -> throw new UnsupportedOperationException(
                    "Monorepo with composition (N→1) instantiation is not supported yet");
            case POLYREPO_NO_COMPOSITION -> throw new UnsupportedOperationException(
                    "Polyrepo without composition (1→N) instantiation is not supported yet");
            case POLYREPO_WITH_COMPOSITION -> throw new UnsupportedOperationException(
                    "Polyrepo with composition (N→N) instantiation is not supported yet");
        }

        presenter.presentResults(new InstantiateBlueprintVersionResult());
    }

    private InstantiationScenario resolveScenario(Manifest manifest) {
        if (manifest.getInstantiation() == null || manifest.getInstantiation().getStrategy() == null) {
            throw new BadRequestException("Manifest instantiation.strategy is required");
        }
        boolean hasComposition = !CollectionUtils.isEmpty(manifest.getComposition());
        ManifestInstantiation.InstantiationStrategy strategy = manifest.getInstantiation().getStrategy();
        return switch (strategy) {
            case MONOREPO -> hasComposition
                    ? InstantiationScenario.MONOREPO_WITH_COMPOSITION
                    : InstantiationScenario.MONOREPO_NO_COMPOSITION;
            case POLYREPO -> hasComposition
                    ? InstantiationScenario.POLYREPO_WITH_COMPOSITION
                    : InstantiationScenario.POLYREPO_NO_COMPOSITION;
        };
    }

    /**
     * 1→1: one ROOT blueprint source rendered into one ROOT target repository.
     */
    private void instantiateMonorepoNoComposition(BlueprintVersion blueprintVersion, Manifest manifest) {
        manifestPort.validateTargetRepositories(blueprintVersion, command.targetRepositories());
        gitPort.init(blueprintVersion.getBlueprint());

        SourceRepositoryDto source = resolveRootSource(manifestPort.retrieveAllSourceRepositories(blueprintVersion, blueprintVersion.getContent()));
        TargetRepositoryDto target = resolveRootTarget(command.targetRepositories());
        Map<String, JsonNode> resolvedParameters = mergeParametersForLineage(manifest, command.blueprintParameters());
        String checkpointTag = BlueprintGitNamingConventions.checkpointTag(command.blueprintVersion());

        String integrationBranch = resolveTargetBranchName(target);
        String orphanBranch = BlueprintGitNamingConventions.orphanInitBranchName();
        String commitMessage = "Populate repository from blueprint "
                + command.blueprintName() + "@" + command.blueprintVersion();

        /*
         * Creates the initial "pure" checkpoint on a single target repository: it
         * renders the blueprint on a fresh orphan branch, commits and tags that
         * snapshot,
         * then merges it into the integration branch and publishes both the branch and
         * the checkpoint tag.
         */
        gitPort.withClonedSourceAndTarget(source, target, integrationBranch, (sourcePath, targetPath) -> {
            gitPort.createAndCheckoutOrphanBranch(targetPath, orphanBranch);
            templatingPort.monorepoNoCompositionRenderAndCopy(blueprintVersion, command.blueprintParameters(), sourcePath, targetPath);
            templatingPort.enrichDescriptorWithBlueprintMetadata(targetPath, blueprintVersion, resolvedParameters);
            String commitHash = gitPort.commitAll(targetPath, orphanBranch, commitMessage, command.commitAuthorName(), command.commitAuthorEmail());
            gitPort.createCheckpointTag(targetPath, checkpointTag, commitHash, command.commitAuthorName(), command.commitAuthorEmail());
            gitPort.mergeBranch(targetPath, orphanBranch, integrationBranch);
            gitPort.pushBranch(targetPath, integrationBranch);
            gitPort.pushTag(targetPath, checkpointTag);
        });
    }

    private String resolveTargetBranchName(TargetRepositoryDto target) {
        return StringUtils.hasText(target.branch())
                ? target.branch()
                : target.repository().getDefaultBranch();
    }

    private SourceRepositoryDto resolveRootSource(List<SourceRepositoryDto> sources) {
        return sources.stream()
                .filter(source -> source.type() == BlueprintRepositoryLogicalType.ROOT)
                .findFirst()
                .orElseThrow(() -> new InternalException(
                        "Blueprint instantiation requires a source repository with type 'root'; none found"));
    }

    private TargetRepositoryDto resolveRootTarget(List<TargetRepositoryDto> targets) {
        return targets.stream()
                .filter(target -> target.type() == BlueprintRepositoryLogicalType.ROOT)
                .findFirst()
                .orElseThrow(() -> new InternalException(
                        "Blueprint instantiation requires a target repository with type 'root'; none found"));
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
            if (target.type() == null) {
                throw new BadRequestException("Target repository type is required at index %s".formatted(i));
            }
            if (target.repository() == null) {
                throw new BadRequestException("Target repository reference is required at index %s".formatted(i));
            }
        }
    }

    private Manifest parseManifest(BlueprintVersion blueprintVersion) {
        JsonNode raw = blueprintVersion.getContent();
        try {
            return ManifestParserFactory.getParser().deserialize(raw);
        } catch (IOException e) {
            throw new InternalException(
                    "Could not parse manifest content for blueprint version '%s' (versionNumber=%s)"
                            .formatted(blueprintVersion.getName(), blueprintVersion.getVersionNumber()),
                    e);
        }
    }

    private Map<String, JsonNode> mergeParametersForLineage(Manifest manifest,
                                                            Map<String, JsonNode> requestParameters) {
        Map<String, JsonNode> out = new LinkedHashMap<>();
        if (manifest.getParameters() == null) {
            return out;
        }
        for (ManifestParameter p : manifest.getParameters()) {
            String key = p.getKey();
            JsonNode fromRequest = requestParameters.get(key);
            if (fromRequest != null && !fromRequest.isNull()) {
                out.put(key, fromRequest);
            } else if (p.getDefaultValue() != null && !p.getDefaultValue().isNull()) {
                out.put(key, p.getDefaultValue());
            }
        }
        return out;
    }
}
