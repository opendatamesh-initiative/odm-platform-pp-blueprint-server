package org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.updatedocumentationfields;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "update_blueprint_version_documentation_fields_command")
public class UpdateBlueprintVersionDocumentationFieldsCommandRes {

    @Schema(description = "The uuid of the blueprint version")
    private String uuid;

    @Schema(description = "The name of the blueprint version")
    private String name;

    @Schema(description = "The description of the blueprint version")
    private String description;

    @Schema(description = "The user id performing the update (stored as updatedBy)")
    private String updatedBy;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
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

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
