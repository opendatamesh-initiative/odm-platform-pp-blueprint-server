package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.publish;

import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;

import com.fasterxml.jackson.databind.JsonNode;

interface PublishBlueprintVersionManifestOutboundPort {

    JsonNode autofillManifest(JsonNode content, BlueprintVersion blueprintVersion);

    void validateManifest(JsonNode content);

    void setVersionFieldsFromManifestContent(BlueprintVersion blueprintVersion);
}

