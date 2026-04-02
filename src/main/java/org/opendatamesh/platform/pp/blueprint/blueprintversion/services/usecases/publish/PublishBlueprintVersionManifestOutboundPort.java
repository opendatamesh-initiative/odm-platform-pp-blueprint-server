package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.publish;


import com.fasterxml.jackson.databind.JsonNode;

interface PublishBlueprintVersionManifestOutboundPort {

    JsonNode autofillManifest(String manifestSpec, String manifestSpecVersion, JsonNode content);

    void validateManifest(String manifestSpec, String manifestSpecVersion, JsonNode content);

    String extractVersionNumber(JsonNode content);

    String extractSpecNumber(JsonNode content);

    String extractSpecVersion(JsonNode content);

    // void setVersionFieldsFromManifestContent(BlueprintVersion blueprintVersion);
}

