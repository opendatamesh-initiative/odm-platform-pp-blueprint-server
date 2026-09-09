package org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public class BlueprintSearchOptions {

    @Parameter(
            description = "Filter blueprints by name. Exact match (case-insensitive).",
            schema = @Schema(type = "string")
    )
    private String name;

    @Parameter(
            description = "Filter blueprints by UUID. Exact match (case-insensitive).",
            schema = @Schema(type = "string")
    )
    private String uuid;

    @Parameter(
            description = "Filter blueprints by blueprint type. Exact match. BLUEPRINT is the root Blueprint; MODULE is the Blueprint module (component).",
            schema = @Schema(implementation = BlueprintTypeRes.class, allowableValues = {"BLUEPRINT", "MODULE"})
    )
    private BlueprintTypeRes blueprintType;

    @Parameter(
            description = "Filter blueprints that have any of these label UUIDs (match any)."
    )
    private List<String> labelUuids;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public BlueprintTypeRes getBlueprintType() {
        return blueprintType;
    }

    public void setBlueprintType(BlueprintTypeRes blueprintType) {
        this.blueprintType = blueprintType;
    }

    public List<String> getLabelUuids() {
        return labelUuids;
    }

    public void setLabelUuids(List<String> labelUuids) {
        this.labelUuids = labelUuids;
    }
}
