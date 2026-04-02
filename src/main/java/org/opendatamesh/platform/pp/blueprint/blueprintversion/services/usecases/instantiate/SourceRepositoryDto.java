package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate;

import org.opendatamesh.platform.git.model.Repository;

public record SourceRepositoryDto(
        String id,  //ID not used, will be used for multi repository instantiations
        BlueprintRepositoryLogicalType type,
        String tag,
        Repository repository
) {
}