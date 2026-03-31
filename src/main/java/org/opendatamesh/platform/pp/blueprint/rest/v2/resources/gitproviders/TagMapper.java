package org.opendatamesh.platform.pp.blueprint.rest.v2.resources.gitproviders;

import org.mapstruct.Mapper;
import org.opendatamesh.platform.git.model.Tag;

@Mapper(componentModel = "spring")
public interface TagMapper {
    TagRes toRes(Tag tag);
}
