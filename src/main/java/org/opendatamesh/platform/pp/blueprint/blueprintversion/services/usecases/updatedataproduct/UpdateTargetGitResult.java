package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.updatedataproduct;

/**
 * Outcome of a single-target checkpoint update (branch, next checkpoint tag, commit SHA)
 * before any optional pull-request open.
 */
record UpdateTargetGitResult(
        String updateBranchName,
        String checkpointTag,
        String commitHash
) {
}
