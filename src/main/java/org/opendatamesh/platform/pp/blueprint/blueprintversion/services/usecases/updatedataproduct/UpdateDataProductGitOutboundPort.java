package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.updatedataproduct;

import org.opendatamesh.platform.git.model.Repository;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint;

import java.nio.file.Path;
import java.util.function.BiConsumer;

/**
 * Git capabilities used while updating a data-product repository from a checkpoint.
 * <p>
 * The port exposes focused, single-purpose Git operations so that the update workflow
 * (branch from checkpoint, clean, render, commit, tag, push) stays orchestrated in the
 * use case and remains readable. Repository cloning lifecycle is handled by
 * {@link #withClonedSourceAndTargetAtCheckpoint}: the granular operations below are meant
 * to be invoked on the target path provided inside that callback.
 */
interface UpdateDataProductGitOutboundPort {

    void init(Blueprint blueprint);

    /**
     * Clones the target repository at {@code currentCheckpointTag} and the source repository
     * at {@code sourceTag}, then invokes {@code operation} with local {@code (sourcePath, targetPath)}.
     * Both working directories are cleaned up after the callback returns.
     */
    void withClonedSourceAndTargetAtCheckpoint(
            Repository sourceRepository,
            String sourceTag,
            Repository targetRepository,
            String currentCheckpointTag,
            BiConsumer<Path, Path> operation);

    void createAndCheckoutBranch(Path targetRepository, String branchName);

    void cleanWorkingTreePreservingGit(Path targetRepository);

    /**
     * Stages every change, commits it on {@code branchName} and returns the resulting commit SHA.
     */
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
