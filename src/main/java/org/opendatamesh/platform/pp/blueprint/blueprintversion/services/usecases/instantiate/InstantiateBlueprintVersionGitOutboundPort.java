package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate;

import org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

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
         * Clones each unique source at its release tag and the target at the
         * integration branch,
         * then invokes {@code operation} with
         * {@code (sourceId → local path, target path)}.
         * Temporary directories are always cleaned up after the callback returns.
         * <p>
         * Binds the Git provider from the parent blueprint's {@code BlueprintRepo} on
         * first use.
         */
        void openSourcesAndTarget(
                        Blueprint parentBlueprint,
                        List<SourceRepositoryDto> sources,
                        TargetRepositoryDto target,
                        String integrationBranch,
                        BiConsumer<Map<String, Path>, Path> operation);

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
