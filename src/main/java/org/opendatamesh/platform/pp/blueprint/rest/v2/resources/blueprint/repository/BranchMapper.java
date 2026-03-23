package org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.repository;

import org.mapstruct.Mapper;
import org.opendatamesh.platform.git.model.Branch;

@Mapper(componentModel = "spring")
public interface BranchMapper {
    BranchRes toRes(Branch branch);
}
