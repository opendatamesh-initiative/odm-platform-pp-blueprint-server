package org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.updatedocumentationfields;

import io.swagger.v3.oas.annotations.media.Schema;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.BlueprintVersionRes;

@Schema(name = "update_blueprint_version_documentation_fields_response")
public class UpdateBlueprintVersionDocumentationFieldsReponseRes {

    @Schema(description = "The blueprint version updated by documentation fields update")
    private BlueprintVersionRes blueprintVersion;

    public BlueprintVersionRes getBlueprintVersion() {
        return blueprintVersion;
    }

    public void setBlueprintVersion(BlueprintVersionRes blueprintVersion) {
        this.blueprintVersion = blueprintVersion;
    }
}
