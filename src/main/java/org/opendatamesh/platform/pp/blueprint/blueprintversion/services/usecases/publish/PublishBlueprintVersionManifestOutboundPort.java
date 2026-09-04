package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.publish;


import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

interface PublishBlueprintVersionManifestOutboundPort {

    JsonNode autofillManifest(String manifestSpec, String manifestSpecVersion, JsonNode content, String blueprintName);

    void validateManifest(String manifestSpec, String manifestSpecVersion, JsonNode content);

    List<PublishCompositionIdentity> listCompositionIdentities(JsonNode content);

    boolean isMonorepoNoComposition(JsonNode content);

    List<String> listMappedChildParameterKeys(JsonNode parentContent, String compositionFieldPath);

    List<String> listModuleParameterKeysWithoutDefault(JsonNode moduleContent);

    String extractVersionNumber(JsonNode content);

    String extractSpecNumber(JsonNode content);

    String extractSpecVersion(JsonNode content);

}
