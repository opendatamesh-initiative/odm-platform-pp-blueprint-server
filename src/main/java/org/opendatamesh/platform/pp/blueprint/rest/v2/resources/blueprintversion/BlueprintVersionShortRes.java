package org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion;

import io.swagger.v3.oas.annotations.media.Schema;
import org.opendatamesh.platform.pp.blueprint.utils.resources.VersionedRes;

@Schema(name = "blueprints_versions_short")
public class BlueprintVersionShortRes extends VersionedRes {

    @Schema(description = "The unique identifier of the blueprint version")
    private String uuid;

    @Schema(description = "The UUID of the parent blueprint")
    private String blueprintUuid;

    @Schema(description = "The name of the blueprint version")
    private String name;

    @Schema(description = "The description of the blueprint version")
    private String description;

    @Schema(description = "The readme of the blueprint version")
    private String readme;

    @Schema(description = "The tag of the blueprint version")
    private String tag;

    @Schema(description = "The manifest version number")
    private String versionNumber;

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

    public String getBlueprintUuid() {
        return blueprintUuid;
    }

    public void setBlueprintUuid(String blueprintUuid) {
        this.blueprintUuid = blueprintUuid;
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

    public String getReadme() {
        return readme;
    }

    public void setReadme(String readme) {
        this.readme = readme;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(String versionNumber) {
        this.versionNumber = versionNumber;
    }
}
