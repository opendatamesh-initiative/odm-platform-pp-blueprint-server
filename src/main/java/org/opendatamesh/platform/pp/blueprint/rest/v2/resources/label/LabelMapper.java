package org.opendatamesh.platform.pp.blueprint.rest.v2.resources.label;

import org.mapstruct.Mapper;
import org.opendatamesh.platform.pp.blueprint.label.entities.Label;

@Mapper(componentModel = "spring")
public interface LabelMapper {
    LabelRes toRes(Label entity);

    Label toEntity(LabelRes res);
}
