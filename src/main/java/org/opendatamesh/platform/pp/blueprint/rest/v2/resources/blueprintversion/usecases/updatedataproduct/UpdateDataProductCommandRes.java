package org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.updatedataproduct;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;

@Schema(name = "update_data_product_command")
public class UpdateDataProductCommandRes {

    @Schema(description = "Blueprint unique name")
    private String blueprintName;

    @Schema(description = "Current blueprint version number (checkpoint baseline)")
    private String currentVersionNumber;

    @Schema(description = "Next blueprint version number to render onto the update branch")
    private String nextVersionNumber;

    @Schema(description = "Parameter values used for rendering the next blueprint version")
    private Map<String, JsonNode> parameters = new LinkedHashMap<>();

    @Schema(description = "One entry per instantiation.repositories[].key of the next blueprint version")
    private List<UpdateDataProductTargetRepositoryRes> targetRepositories = new ArrayList<>();

    @Schema(description = "Optional commit author name", example = "ODM Platform", requiredMode = NOT_REQUIRED)
    private String commitAuthorName;

    @Schema(description = "Optional commit author email", example = "odm-platform@example.org", requiredMode = NOT_REQUIRED)
    private String commitAuthorEmail;

    @Schema(description = "Global switch: when true, open a pull request for every successfully updated target", requiredMode = NOT_REQUIRED)
    private Boolean createPullRequest;

    public String getBlueprintName() {
        return blueprintName;
    }

    public void setBlueprintName(String blueprintName) {
        this.blueprintName = blueprintName;
    }

    public String getCurrentVersionNumber() {
        return currentVersionNumber;
    }

    public void setCurrentVersionNumber(String currentVersionNumber) {
        this.currentVersionNumber = currentVersionNumber;
    }

    public String getNextVersionNumber() {
        return nextVersionNumber;
    }

    public void setNextVersionNumber(String nextVersionNumber) {
        this.nextVersionNumber = nextVersionNumber;
    }

    public Map<String, JsonNode> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, JsonNode> parameters) {
        this.parameters = parameters;
    }

    public List<UpdateDataProductTargetRepositoryRes> getTargetRepositories() {
        return targetRepositories;
    }

    public void setTargetRepositories(List<UpdateDataProductTargetRepositoryRes> targetRepositories) {
        this.targetRepositories = targetRepositories;
    }

    public String getCommitAuthorName() {
        return commitAuthorName;
    }

    public void setCommitAuthorName(String commitAuthorName) {
        this.commitAuthorName = commitAuthorName;
    }

    public String getCommitAuthorEmail() {
        return commitAuthorEmail;
    }

    public void setCommitAuthorEmail(String commitAuthorEmail) {
        this.commitAuthorEmail = commitAuthorEmail;
    }

    public Boolean getCreatePullRequest() {
        return createPullRequest;
    }

    public void setCreatePullRequest(Boolean createPullRequest) {
        this.createPullRequest = createPullRequest;
    }
}
