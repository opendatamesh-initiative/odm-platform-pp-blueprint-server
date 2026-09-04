package org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.instantiate;

import io.swagger.v3.oas.annotations.media.Schema;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.gitproviders.RepositoryRes;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.manifestautofiller.OdmBlueprintManifestAutoFiller;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;

@Schema(name = "instantiate_blueprint_version_target_repository")
public class InstantiateBlueprintVersionTargetRepositoryRes {

    @Schema(description = "The repository Id specified in the blueprint manifest. The default repository id is " + OdmBlueprintManifestAutoFiller.DEFAULT_REPOSITORY_KEY + ".", example = OdmBlueprintManifestAutoFiller.DEFAULT_REPOSITORY_KEY)
    private String targetId;

    @Schema(description = "The branch where the instantiation files will be committed. If not specified, the default branch of the target repository will be used.", requiredMode = NOT_REQUIRED)
    private String branch;

    @Schema(description = "Target repository reference")
    private RepositoryRes repository;

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
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
