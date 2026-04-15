package org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.usecases.updatedocumentationfields;

import io.swagger.v3.oas.annotations.media.Schema;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintRes;

@Schema(name = "update_blueprint_documentation_fields_response")
public class UpdateBlueprintDocumentationFieldsResponseRes {

    @Schema(description = "The blueprint updated by documentation fields update")
    private BlueprintRes blueprint;

    public BlueprintRes getBlueprint() {
        return blueprint;
    }

    public void setBlueprint(BlueprintRes blueprint) {
        this.blueprint = blueprint;
    }
}
