package org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.BlueprintRepo;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.BlueprintRepoProviderType;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.BlueprintRepoOwnerType;

@Mapper(componentModel = "spring")
public interface BlueprintRepoMapper {
    
    @Mapping(target = "blueprint", ignore = true)
    BlueprintRepo toEntity(BlueprintRes.BlueprintRepoRes res);

    default BlueprintRepoProviderType map(BlueprintRepoProviderTypeRes res) {
        if (res == null) {
            return null;
        }
        return BlueprintRepoProviderType.valueOf(res.name());
    }

    default BlueprintRepoOwnerType map(BlueprintRepoOwnerTypeRes res) {
        if (res == null) {
            return null;
        }
        return BlueprintRepoOwnerType.valueOf(res.name());
    }
}
