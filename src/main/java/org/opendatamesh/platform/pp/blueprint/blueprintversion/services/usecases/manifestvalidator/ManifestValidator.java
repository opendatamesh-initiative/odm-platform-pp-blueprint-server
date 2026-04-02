package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.manifestvalidator;

import com.fasterxml.jackson.databind.JsonNode;

public interface ManifestValidator {

    void validateManifest(JsonNode content);

}
