package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.updatedataproduct;

import org.opendatamesh.platform.git.model.Repository;

/**
 * Domain description of one data-product Git repository to update (use-case-internal {@code Dto}).
 * Includes logical key ({@code targetId}), optional integration branch, repository metadata, and optional PR target branch.
 */
public record UpdateDataProductTargetRepositoryDto(
        String targetId,
        String branch,
        Repository repository,
        String pullRequestTargetBranch
) {
}
