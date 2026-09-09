package org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.updatedataproduct;

import io.swagger.v3.oas.annotations.media.Schema;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.gitproviders.RepositoryRes;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.manifestautofiller.OdmBlueprintManifestAutoFiller;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;

@Schema(name = "update_data_product_target_repository")
public class UpdateDataProductTargetRepositoryRes {

    @Schema(description = "The repository Id specified in the blueprint manifest. The default repository id is " + OdmBlueprintManifestAutoFiller.DEFAULT_REPOSITORY_KEY + ".", example = OdmBlueprintManifestAutoFiller.DEFAULT_REPOSITORY_KEY)
    private String targetId;

    @Schema(description = "Integration branch used as context for the data product repository. If not specified, the repository default branch is used.", requiredMode = NOT_REQUIRED)
    private String branch;

    @Schema(description = "Target repository reference")
    private RepositoryRes repository;

    @Schema(description = "Pull request base branch for this repository when global createPullRequest is true. Defaults to the repository default branch.", requiredMode = NOT_REQUIRED)
    private String pullRequestTargetBranch;

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public RepositoryRes getRepository() {
        return repository;
    }

    public void setRepository(RepositoryRes repository) {
        this.repository = repository;
    }

    public String getPullRequestTargetBranch() {
        return pullRequestTargetBranch;
    }

    public void setPullRequestTargetBranch(String pullRequestTargetBranch) {
        this.pullRequestTargetBranch = pullRequestTargetBranch;
    }
}
