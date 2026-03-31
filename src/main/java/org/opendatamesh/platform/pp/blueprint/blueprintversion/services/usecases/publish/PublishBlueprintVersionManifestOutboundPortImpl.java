package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.publish;

import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.manifestvalidator.ManifestValidator;

import com.fasterxml.jackson.databind.JsonNode;

class PublishBlueprintVersionManifestOutboundPortImpl implements PublishBlueprintVersionManifestOutboundPort {

    private final ManifestValidator manifestValidator;

    PublishBlueprintVersionManifestOutboundPortImpl(ManifestValidator manifestValidator) {
        this.manifestValidator = manifestValidator;
    }

    @Override
    public JsonNode autofillManifest(JsonNode content, BlueprintVersion blueprintVersion) {
        return manifestValidator.autofill(content, blueprintVersion);
    }

    @Override
    public void validateManifest(JsonNode content) {
        manifestValidator.validate(content);
    }

    @Override
    public void setVersionFieldsFromManifestContent(BlueprintVersion blueprintVersion) {
        manifestValidator.setVersionFieldsOnBlueprintVersion(blueprintVersion);
    }
}
