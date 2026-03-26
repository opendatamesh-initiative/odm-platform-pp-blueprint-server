package org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint;

import org.mapstruct.Mapper;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint;

@Mapper(componentModel = "spring", uses = BlueprintRepoMapper.class)
public interface BlueprintMapper {
    BlueprintRes toRes(Blueprint entity);

    Blueprint toEntity(BlueprintRes res);
}
