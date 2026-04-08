package org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.instantiate;

import io.swagger.v3.oas.annotations.media.Schema;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate.BlueprintRepositoryLogicalType;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.gitproviders.RepositoryRes;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;

@Schema(name = "instantiate_blueprint_version_target_repository")
public class InstantiateBlueprintVersionTargetRepositoryRes {

    @Schema(description = "Target repository logical type", example = "root")
    private BlueprintRepositoryLogicalType type;

    @Schema(description = "The branch where the instantiation files will be committed. If not specified, the default branch of the target repository will be used.", requiredMode = NOT_REQUIRED)
    private String branch;

    @Schema(description = "Target repository reference")
    private RepositoryRes repository;

    public BlueprintRepositoryLogicalType getType() {
        return type;
    }

    public void setType(BlueprintRepositoryLogicalType type) {
        this.type = type;
    }

    public RepositoryRes getRepository() {
        return repository;
    }

    public void setRepository(RepositoryRes repository) {
        this.repository = repository;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }
}
