package org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.publish;

import io.swagger.v3.oas.annotations.media.Schema;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.BlueprintVersionRes;

@Schema(name = "publish_blueprint_version_response")
public class PublishBlueprintVersionResponseRes {

    @Schema(description = "The blueprint version created by publication")
    private BlueprintVersionRes blueprintVersion;

    public BlueprintVersionRes getBlueprintVersion() {
        return blueprintVersion;
    }

    public void setBlueprintVersion(BlueprintVersionRes blueprintVersion) {
        this.blueprintVersion = blueprintVersion;
    }
}
