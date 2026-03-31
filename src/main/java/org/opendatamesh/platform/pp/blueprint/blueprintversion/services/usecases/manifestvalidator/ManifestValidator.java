package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.manifestvalidator;

import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;

import com.fasterxml.jackson.databind.JsonNode;

public interface ManifestValidator {

    JsonNode autofill(JsonNode content, BlueprintVersion blueprintVersion);

    void validate(JsonNode content);

    void setVersionFieldsOnBlueprintVersion(BlueprintVersion blueprintVersion);
}
