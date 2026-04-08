package org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.instantiate;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Schema(name = "instantiate_blueprint_version_command")
public class InstantiateBlueprintVersionCommandRes {

    @Schema(description = "Blueprint unique name")
    private String blueprintName;

    @Schema(description = "Blueprint version number")
    private String blueprintVersionNumber;

    @Schema(description = "Target repositories for instantiation")
    private List<InstantiateBlueprintVersionTargetRepositoryRes> targetRepositories = new ArrayList<>();

    @Schema(description = "Parameter values used for rendering")
    private Map<String, JsonNode> parameters = new LinkedHashMap<>();

    @Schema(description = "Optional commit author name used for target repository commit", example = "ODM Platform")
    private String commitAuthorName;

    @Schema(description = "Optional commit author email used for target repository commit", example = "odm-platform@example.org")
    private String commitAuthorEmail;

    public InstantiateBlueprintVersionCommandRes() {
    }

    public String getBlueprintName() {
        return blueprintName;
    }

    public void setBlueprintName(String blueprintName) {
        this.blueprintName = blueprintName;
    }

    public String getBlueprintVersionNumber() {
        return blueprintVersionNumber;
    }

    public void setBlueprintVersionNumber(String blueprintVersionNumber) {
        this.blueprintVersionNumber = blueprintVersionNumber;
    }

    public List<InstantiateBlueprintVersionTargetRepositoryRes> getTargetRepositories() {
        return targetRepositories;
    }

    public void setTargetRepositories(List<InstantiateBlueprintVersionTargetRepositoryRes> targetRepositories) {
        this.targetRepositories = targetRepositories;
    }

    public Map<String, JsonNode> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, JsonNode> parameters) {
        this.parameters = parameters;
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


}
