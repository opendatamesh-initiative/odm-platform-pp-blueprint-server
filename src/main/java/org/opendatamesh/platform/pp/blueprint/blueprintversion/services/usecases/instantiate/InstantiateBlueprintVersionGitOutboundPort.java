package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate;

import org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint;

import java.nio.file.Path;
import java.util.function.BiConsumer;

/**
 * Git capabilities used while creating the initial checkpoint of a blueprint instantiation.
 * <p>
 * The port exposes focused, single-purpose Git operations so that the checkpoint workflow
 * (orphan branch, commit, tag, merge, push) stays orchestrated in the use case and remains
 * readable. Repository cloning lifecycle is handled by {@link #withClonedSourceAndTarget}:
 * the granular operations below are meant to be invoked on the target path provided inside
 * that callback.
 */
interface InstantiateBlueprintVersionGitOutboundPort {

    void init(Blueprint blueprint);

    /**
     * Clones the source repository (frozen at its tag) and the target repository (at the
     * integration branch), then invokes {@code operation} with the local {@code (sourcePath,
     * targetPath)}. Both working directories are cleaned up after the callback returns.
     */
    void withClonedSourceAndTarget(
            SourceRepositoryDto source,
            TargetRepositoryDto target,
            String integrationBranch,
            BiConsumer<Path, Path> operation);

    void createAndCheckoutOrphanBranch(Path targetRepository, String branchName);

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

    void mergeBranch(Path targetRepository, String sourceBranch, String targetBranch);

    void pushBranch(Path targetRepository, String branchName);

    void pushTag(Path targetRepository, String tagName);
}
