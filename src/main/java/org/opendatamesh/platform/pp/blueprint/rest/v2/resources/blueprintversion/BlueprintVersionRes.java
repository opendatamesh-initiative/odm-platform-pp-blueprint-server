package org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintRes;
import org.opendatamesh.platform.pp.blueprint.utils.resources.VersionedRes;

@Schema(name = "blueprint_versions")
public class BlueprintVersionRes extends VersionedRes {

    @Schema(description = "The unique identifier of the blueprint version")
    private String uuid;

    @Schema(description = "The parent blueprint details")
    private BlueprintRes blueprint;

    @Schema(description = "The name of the blueprint version")
    private String name;

    @Schema(description = "The description of the blueprint version")
    private String description;

    @Schema(description = "The tag of the blueprint version")
    private String tag;

    @Schema(description = "The descriptor specification")
    private String spec;

    @Schema(description = "The descriptor specification version")
    private String specVersion;

    @Schema(description = "The descriptor version")
    private String versionNumber;

    @Schema(description = "The descriptor content")
    private JsonNode content;

    @Schema(description = "The user id who created the blueprint version")
    private String createdBy;

    @Schema(description = "The user id who last updated the blueprint version")
    private String updatedBy;

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public BlueprintRes getBlueprint() {
        return blueprint;
    }

    public void setBlueprint(BlueprintRes blueprint) {
        this.blueprint = blueprint;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getSpec() {
        return spec;
    }

    public void setSpec(String spec) {
        this.spec = spec;
    }

    public String getSpecVersion() {
        return specVersion;
    }

    public void setSpecVersion(String specVersion) {
        this.specVersion = specVersion;
    }

    public String getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(String versionNumber) {
        this.versionNumber = versionNumber;
    }

    public JsonNode getContent() {
        return content;
    }

    public void setContent(JsonNode content) {
        this.content = content;
    }
}
