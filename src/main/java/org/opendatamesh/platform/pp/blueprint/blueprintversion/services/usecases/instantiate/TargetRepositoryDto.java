package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate;

import org.opendatamesh.platform.git.model.Repository;

/**
 * Domain description of one data-product Git repository that receives rendered blueprint content
 * (use-case-internal {@code Dto}).
 * Carries logical repository key ({@code targetId}), optional integration branch override, and repository metadata.
 */
public record TargetRepositoryDto(
        String targetId,
        String branch,
        Repository repository
) {
}
