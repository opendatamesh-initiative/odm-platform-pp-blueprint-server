package org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.instantiate;

import io.swagger.v3.oas.annotations.media.Schema;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.gitproviders.RepositoryRes;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.manifestautofiller.OdmBlueprintManifestAutoFiller;

@Schema(name = "instantiate_blueprint_version_target_repository_result")
public class InstantiateBlueprintVersionTargetRepositoryResultRes {

    @Schema(description = "The repository Id specified in the blueprint manifest. The default repository id is " + OdmBlueprintManifestAutoFiller.DEFAULT_REPOSITORY_KEY + ".", example = OdmBlueprintManifestAutoFiller.DEFAULT_REPOSITORY_KEY)
    private String targetId;

    @Schema(description = "Target repository reference")
    private RepositoryRes repository;

    @Schema(description = "Commit SHA produced by instantiation", example = "abc123def456")
    private String commitSha;

    @Schema(description = "Number of changed files produced by instantiation", example = "12")
    private Integer changedFiles;

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

    public String getCommitSha() {
        return commitSha;
    }

    public void setCommitSha(String commitSha) {
        this.commitSha = commitSha;
    }

    public Integer getChangedFiles() {
        return changedFiles;
    }

    public void setChangedFiles(Integer changedFiles) {
        this.changedFiles = changedFiles;
    }
}
