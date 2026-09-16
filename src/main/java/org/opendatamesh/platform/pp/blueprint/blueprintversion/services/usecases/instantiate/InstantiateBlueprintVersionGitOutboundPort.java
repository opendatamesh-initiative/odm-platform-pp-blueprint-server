package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate;

import org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Git capabilities used while creating the initial checkpoint of a blueprint
 * instantiation.
 * <p>
 * The use case orders checkpoint policy steps; this port materializes
 * workspaces and performs
 * the granular Git operations on the target path supplied inside the workspace
 * callback.
 */
interface InstantiateBlueprintVersionGitOutboundPort {

    /**
     * Opens each unique source at its release tag for the duration of
     * {@code operation}. This lets one source workspace serve every target that
     * consumes it. Temporary directories are cleaned up after the callback returns.
     * <p>
     * Binds the Git provider from the parent blueprint's {@code BlueprintRepo} on
     * first use.
     */
    void openSources(
            Blueprint parentBlueprint,
            List<SourceRepositoryDto> sources,
            Consumer<Map<String, Path>> operation);

    /**
     * Opens one target at its integration branch for the duration of
     * {@code operation}. The temporary directory is cleaned up afterwards.
     */
    void openTarget(
            TargetRepositoryDto target,
            String integrationBranch,
            Consumer<Path> operation);

        void createAndCheckoutOrphanBranch(Path targetRepository, String branchName);

        /**
         * Stages every change, commits it on {@code branchName} and returns the
         * resulting commit SHA.
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
