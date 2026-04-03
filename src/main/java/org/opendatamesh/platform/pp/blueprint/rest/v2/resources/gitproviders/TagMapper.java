package org.opendatamesh.platform.pp.blueprint.rest.v2.resources.gitproviders;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.opendatamesh.platform.git.model.Tag;

@Mapper(componentModel = "spring")
public interface TagMapper {
    @Mapping(source = "author", target = "authorName")
    TagRes toRes(Tag tag);
}
