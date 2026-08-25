package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.updatedataproduct;

import com.fasterxml.jackson.databind.JsonNode;
import org.opendatamesh.platform.git.exceptions.GitException;
import org.opendatamesh.platform.git.model.Repository;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.BlueprintGitNamingConventions;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.InstantiationScenario;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.InstantiationScenarioResolver;
import org.opendatamesh.platform.pp.blueprint.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.blueprint.exceptions.InternalException;
import org.opendatamesh.platform.pp.blueprint.manifest.model.Manifest;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestParameter;
import org.opendatamesh.platform.pp.blueprint.manifest.parser.ManifestParserFactory;
import org.opendatamesh.platform.pp.blueprint.utils.usecases.UseCase;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

class UpdateDataProductFromBlueprintVersion implements UseCase {

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

        manifestPort.validateManifestAndParameters(
                nextVersion.getSpec(),
                nextVersion.getSpecVersion(),
                nextVersion.getContent(),
                command.parameters());

        Manifest manifest = parseManifest(nextVersion);
        InstantiationScenario scenario = resolveScenario(manifest);

        List<UpdateDataProductTargetResult> results = switch (scenario) {
            case MONOREPO_NO_COMPOSITION -> updateMonorepoNoComposition(nextVersion, manifest);
            case MONOREPO_WITH_COMPOSITION -> throw new UnsupportedOperationException(
                    "Monorepo with composition (N→1) update is not supported yet");
            case POLYREPO_NO_COMPOSITION -> throw new UnsupportedOperationException(
                    "Polyrepo without composition (1→N) update is not supported yet");
            case POLYREPO_WITH_COMPOSITION -> throw new UnsupportedOperationException(
                    "Polyrepo with composition (N→N) update is not supported yet");
        };

        presenter.presentResult(new UpdateDataProductResult(results, warnings));
    }

    private InstantiationScenario resolveScenario(Manifest manifest) {
        return InstantiationScenarioResolver.resolve(manifest);
    }

    /**
     * 1→1: one ROOT blueprint source re-rendered into one ROOT target from the current checkpoint.
     */
    private List<UpdateDataProductTargetResult> updateMonorepoNoComposition(
            BlueprintVersion nextVersion,
            Manifest manifest
    ) {
        // Validate the request and resolve the source, target, and rendering parameters.
        manifestPort.validateTargetRepositories(nextVersion, command.targetRepositories());
        gitPort.init(nextVersion.getBlueprint());

        Repository blueprintSourceRepository = manifestPort.resolveSourceRepository(nextVersion);
        UpdateDataProductTargetRepositoryDto rootTargetRepository =
                resolveRootTarget(command.targetRepositories());
        Map<String, JsonNode> resolvedLineageParameters =
                mergeParametersForLineage(manifest, command.parameters());

        String currentCheckpointTag = BlueprintGitNamingConventions.checkpointTag(command.currentVersionNumber());
        String nextCheckpointTag = BlueprintGitNamingConventions.checkpointTag(command.nextVersionNumber());
        String updateBranchName = BlueprintGitNamingConventions.updateBranchName(command.nextVersionNumber());

        String nextBlueprintSourceTag = nextVersion.getTag();
        String commitMessage = "Update data product from blueprint %s@%s -> %s".formatted(command.blueprintName(), command.currentVersionNumber(), command.nextVersionNumber());
        AtomicReference<String> createdCommitHash = new AtomicReference<>();

        // Clone both repositories, branch from the current checkpoint, and render the next blueprint version.
        gitPort.withClonedSourceAndTargetAtCheckpoint(
                blueprintSourceRepository,
                nextBlueprintSourceTag,
                rootTargetRepository.repository(),
                currentCheckpointTag,
                (sourceRepositoryPath, targetRepositoryPath) -> {
                    gitPort.createAndCheckoutBranch(targetRepositoryPath, updateBranchName);
                    gitPort.cleanWorkingTreePreservingGit(targetRepositoryPath);
                    templatingPort.monorepoNoCompositionRenderAndCopy(nextVersion, command.parameters(), sourceRepositoryPath, targetRepositoryPath);
                    templatingPort.enrichDescriptorWithBlueprintMetadata(targetRepositoryPath, nextVersion, resolvedLineageParameters);

                    // Commit the rendered files, create the new checkpoint, and publish both references.
                    String commitHash = gitPort.commitAll(targetRepositoryPath, updateBranchName, commitMessage, command.commitAuthorName(), command.commitAuthorEmail());
                    gitPort.createCheckpointTag(targetRepositoryPath, nextCheckpointTag, commitHash, command.commitAuthorName(), command.commitAuthorEmail());
                    gitPort.pushBranch(targetRepositoryPath, updateBranchName);
                    gitPort.pushTag(targetRepositoryPath, nextCheckpointTag);
                    createdCommitHash.set(commitHash);
                });

        String commitHash = createdCommitHash.get();
        if (commitHash == null) {
            throw new InternalException("Update from checkpoint completed without producing a commit hash");
        }
        UpdateTargetGitResult updateGitResult = new UpdateTargetGitResult(updateBranchName, nextCheckpointTag, commitHash);

        // Open the pull request only after the branch and checkpoint tag have been published.
        String pullRequestWebUrl = null;
        if (command.createPullRequest()) {
            pullRequestWebUrl = tryOpenPullRequest(
                    rootTargetRepository,
                    updateGitResult,
                    currentCheckpointTag,
                    nextCheckpointTag);
        }

        return List.of(new UpdateDataProductTargetResult(
                rootTargetRepository.targetId(),
                rootTargetRepository.repository(),
                updateGitResult.updateBranchName(),
                updateGitResult.checkpointTag(),
                updateGitResult.commitHash(),
                pullRequestWebUrl));
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

    private UpdateDataProductTargetRepositoryDto resolveRootTarget(List<UpdateDataProductTargetRepositoryDto> targets) {
        if (targets == null || targets.isEmpty()) {
            throw new InternalException("Blueprint update requires a target repository; none found");
        }
        return targets.getFirst();
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

    private Map<String, JsonNode> mergeParametersForLineage(Manifest manifest, Map<String, JsonNode> requestParameters) {
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
