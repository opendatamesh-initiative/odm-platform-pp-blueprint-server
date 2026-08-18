package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.updatedataproduct;

import org.opendatamesh.platform.git.model.Repository;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate.BlueprintRepositoryLogicalType;

/**
 * Domain result for one updated target repository.
 * Carries the update branch, next checkpoint tag, commit hash, and optional PR web URL.
 */
public record UpdateDataProductTargetResult(
        BlueprintRepositoryLogicalType type,
        Repository repository,
        String updateBranchName,
        String checkpointTag,
        String commitHash,
        String pullRequestWebUrl
) {
}
