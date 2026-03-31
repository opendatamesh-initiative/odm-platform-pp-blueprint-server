package org.opendatamesh.platform.pp.blueprint.rest.v2.resources.gitproviders;

import org.mapstruct.Mapper;
import org.opendatamesh.platform.git.model.Commit;

@Mapper(componentModel = "spring")
public interface CommitMapper {
    CommitRes toRes(Commit commit);
}
