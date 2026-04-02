package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.manifestautofiller;

import com.fasterxml.jackson.databind.JsonNode;

public interface ManifestAutoFiller {
    
    JsonNode autofillManifest(JsonNode manifestContent, String blueprintName);
}
