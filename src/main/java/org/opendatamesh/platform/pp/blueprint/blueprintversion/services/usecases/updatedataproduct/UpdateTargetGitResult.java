package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.updatedataproduct;

/**
 * Outcome of a single-target checkpoint update (branch, next checkpoint tag, commit SHA)
 * before any optional pull-request open.
 * When {@code contentUnchanged} is true, {@code updateBranchName} is null because no
 * update branch was created or pushed.
 */
record UpdateTargetGitResult(
        String updateBranchName,
        String checkpointTag,
        String commitHash,
        boolean contentUnchanged
) {
}
