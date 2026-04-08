package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate;

import org.opendatamesh.platform.git.model.Repository;

public record TargetRepositoryDto(
        String id,  //ID not used, will be used for multi repository instantiations
        BlueprintRepositoryLogicalType type,
        String branch, //To select branch different from the default one
        Repository repository
) {
}