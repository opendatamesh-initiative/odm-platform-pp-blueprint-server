package org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion;

import org.mapstruct.Mapper;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersionShort;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintMapper;

@Mapper(componentModel = "spring", uses = BlueprintMapper.class)
public interface BlueprintVersionMapper {

    BlueprintVersion toEntity(BlueprintVersionRes res);

    BlueprintVersionRes toRes(BlueprintVersion entity);

    BlueprintVersionShortRes toShortResFromShort(BlueprintVersionShort entity);
}
