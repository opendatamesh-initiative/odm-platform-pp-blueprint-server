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

    public List<String> getLabelUuids() {
        return labelUuids;
    }

    public void setLabelUuids(List<String> labelUuids) {
        this.labelUuids = labelUuids;
    }
}
