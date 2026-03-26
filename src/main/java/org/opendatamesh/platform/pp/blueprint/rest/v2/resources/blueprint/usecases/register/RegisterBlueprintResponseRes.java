package org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.usecases.register;

import io.swagger.v3.oas.annotations.media.Schema;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintRes;

@Schema(name = "register_blueprint_response")
public class RegisterBlueprintResponseRes {

    @Schema(description = "The blueprint created by registration")
    private BlueprintRes blueprint;

    public BlueprintRes getBlueprint() {
        return blueprint;
    }

    public void setBlueprint(BlueprintRes blueprint) {
        this.blueprint = blueprint;
    }
}
