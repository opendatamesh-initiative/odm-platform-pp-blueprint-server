package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.updatedataproduct;

import org.opendatamesh.platform.git.model.Repository;

/**
 * Domain result for one updated target repository.
 * Carries the update branch, next checkpoint tag, commit hash, and optional PR web URL.
 */
public record UpdateDataProductTargetResult(
        String targetId,
        Repository repository,
        String updateBranchName,
        String checkpointTag,
        String commitHash,
        String pullRequestWebUrl
) {
}
