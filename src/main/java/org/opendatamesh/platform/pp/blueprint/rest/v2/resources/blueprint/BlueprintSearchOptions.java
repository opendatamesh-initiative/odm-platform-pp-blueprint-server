package org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;

public class BlueprintSearchOptions {

    @Parameter(
            description = "Filter blueprints by name. Exact match (case-insensitive).",
            schema = @Schema(type = "string")
    )
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
