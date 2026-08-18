package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate;

import org.opendatamesh.platform.git.model.Repository;

/**
 * Domain description of one data-product Git repository that receives rendered blueprint content
 * (use-case-internal {@code Dto}).
 * Carries logical role, optional integration branch override, and repository metadata.
 */
public record TargetRepositoryDto(
        String id,  //ID not used, will be used for multi repository instantiations
        BlueprintRepositoryLogicalType type,
        String branch, //To select branch different from the default one
        Repository repository
) {
}