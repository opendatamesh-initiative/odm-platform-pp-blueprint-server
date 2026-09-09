package org.opendatamesh.platform.pp.blueprint.rest.v2.resources.label;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;

public class LabelSearchOptions {

    @Parameter(
            description = "Filter labels by name. Case-insensitive substring match (LIKE).",
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
