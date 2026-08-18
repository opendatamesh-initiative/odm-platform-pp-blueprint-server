package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate;

import java.nio.file.Path;
import java.util.Map;

import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;

import com.fasterxml.jackson.databind.JsonNode;

interface InstantiateBlueprintVersionTemplatingOutboundPort {

    void monorepoNoCompositionRenderAndCopy(
            BlueprintVersion blueprintVersion,
            Map<String, JsonNode> parameters,
            Path sourceRoot,
            Path targetRoot
    );

    void enrichDescriptorWithBlueprintMetadata(
            Path rootTarget,
            BlueprintVersion version,
            Map<String, JsonNode> parameters);
}
