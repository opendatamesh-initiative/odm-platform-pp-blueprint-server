package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.updatedataproduct;

import org.opendatamesh.platform.git.model.Repository;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate.BlueprintRepositoryLogicalType;

/**
 * Domain description of one data-product Git repository to update (use-case-internal {@code Dto}).
 * Includes logical type, optional integration branch, repository metadata, and optional PR target branch.
 */
public record UpdateDataProductTargetRepositoryDto(
        BlueprintRepositoryLogicalType type,
        String branch,
        Repository repository,
        String pullRequestTargetBranch
) {
}
