package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.updatedataproduct;

import com.fasterxml.jackson.databind.JsonNode;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;

import java.nio.file.Path;
import java.util.Map;

interface UpdateDataProductTemplatingOutboundPort {

    void monorepoNoCompositionRenderAndCopy(
            BlueprintVersion blueprintVersion,
            Map<String, JsonNode> parameters,
            Path sourceRoot,
            Path targetRoot);

    void enrichDescriptorWithBlueprintMetadata(
            Path rootTarget,
            BlueprintVersion version,
            Map<String, JsonNode> parameters);
}
