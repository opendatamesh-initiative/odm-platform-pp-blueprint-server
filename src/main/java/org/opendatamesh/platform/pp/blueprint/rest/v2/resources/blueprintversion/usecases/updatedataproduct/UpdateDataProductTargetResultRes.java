package org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.updatedataproduct;

import io.swagger.v3.oas.annotations.media.Schema;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate.BlueprintRepositoryLogicalType;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.gitproviders.RepositoryRes;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;

@Schema(name = "update_data_product_target_result")
public class UpdateDataProductTargetResultRes {

    @Schema(description = "Target repository logical type", example = "root")
    private BlueprintRepositoryLogicalType type;

    @Schema(description = "Target repository reference")
    private RepositoryRes repository;

    @Schema(description = "Temporary update branch created from the current checkpoint", example = "update/blueprint-v2.0.0")
    private String updateBranchName;

    @Schema(description = "Next pure checkpoint tag created on the update branch tip", example = "blueprint-v2.0.0")
    private String checkpointTag;

    @Schema(description = "Commit SHA of the pure next-version render", example = "abc123def456")
    private String commitHash;

    @Schema(description = "Provider web URL of the opened pull request, when createPullRequest succeeded", requiredMode = NOT_REQUIRED)
    private String pullRequestWebUrl;

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

    public String getUpdateBranchName() {
        return updateBranchName;
    }

    public void setUpdateBranchName(String updateBranchName) {
        this.updateBranchName = updateBranchName;
    }

    public String getCheckpointTag() {
        return checkpointTag;
    }

    public void setCheckpointTag(String checkpointTag) {
        this.checkpointTag = checkpointTag;
    }

    public String getCommitHash() {
        return commitHash;
    }

    public void setCommitHash(String commitHash) {
        this.commitHash = commitHash;
    }

    public String getPullRequestWebUrl() {
        return pullRequestWebUrl;
    }

    public void setPullRequestWebUrl(String pullRequestWebUrl) {
        this.pullRequestWebUrl = pullRequestWebUrl;
    }
}
