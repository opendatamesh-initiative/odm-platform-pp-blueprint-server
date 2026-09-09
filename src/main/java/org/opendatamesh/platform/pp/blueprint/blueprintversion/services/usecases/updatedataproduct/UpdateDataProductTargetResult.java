package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.updatedataproduct;

import org.opendatamesh.platform.git.model.Repository;

/**
 * Domain result for one updated target repository.
 * Carries the update branch (null when content was unchanged), next checkpoint tag,
 * commit hash, optional PR web URL, and whether the next render matched the current checkpoint.
 */
public record UpdateDataProductTargetResult(
        String targetId,
        Repository repository,
        String updateBranchName,
        String checkpointTag,
        String commitHash,
        String pullRequestWebUrl,
        boolean contentUnchanged
) {
}
