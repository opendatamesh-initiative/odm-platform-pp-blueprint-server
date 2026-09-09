package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.updatedataproduct;

import org.opendatamesh.platform.git.model.Repository;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate.SourceRepositoryDto;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Git capabilities used while updating a data-product repository from a checkpoint.
 */
interface UpdateDataProductGitOutboundPort {

    /**
     * Opens each unique source at its release tag for the duration of
     * {@code operation}. This lets one source workspace serve every target that
     * consumes it. Temporary directories are cleaned up after the callback returns.
     */
    void openSources(
            Blueprint parentBlueprint,
            List<SourceRepositoryDto> sources,
            Consumer<Map<String, Path>> operation);

    /**
     * Opens one target at {@code currentCheckpointTag} for the duration of
     * {@code operation}. The temporary directory is cleaned up afterwards.
     */
    void openTargetAtCheckpoint(
            UpdateDataProductTargetRepositoryDto target,
            String currentCheckpointTag,
            Consumer<Path> operation);

    void createAndCheckoutBranch(Path targetRepository, String branchName);

    void cleanWorkingTreePreservingGit(Path targetRepository);

    /**
     * Returns {@code true} when the working tree has staged or unstaged changes
     * relative to the currently checked-out commit.
     */
    boolean hasWorkingTreeChanges(Path targetRepository);

    /**
     * Returns the SHA of the commit currently checked out (branch tip or detached
     * HEAD after a checkpoint tag checkout). Does not require an update branch.
     */
    String resolveCheckedOutCommitSha(Path targetRepository);

    String commitAll(
            Path targetRepository,
            String branchName,
            String commitMessage,
            String commitAuthorName,
            String commitAuthorEmail);

    void createCheckpointTag(
            Path targetRepository,
            String checkpointTag,
            String commitHash,
            String commitAuthorName,
            String commitAuthorEmail);

    void pushBranch(Path targetRepository, String branchName);

    void pushTag(Path targetRepository, String tagName);

    String openPullRequest(
            Repository repository,
            String sourceBranch,
            String targetBranch,
            String title,
            String body);
}
