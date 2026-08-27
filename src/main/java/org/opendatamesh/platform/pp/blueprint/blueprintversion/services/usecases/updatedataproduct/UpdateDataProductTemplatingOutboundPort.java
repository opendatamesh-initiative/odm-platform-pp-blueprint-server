package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.updatedataproduct;

import com.fasterxml.jackson.databind.JsonNode;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;

import java.nio.file.Path;
import java.util.Map;

interface UpdateDataProductTemplatingOutboundPort {

    void applyRoute(
            Path sourceRoot,
            String sourcePath,
            Path targetRoot,
            String destinationPath,
            Map<String, JsonNode> parameters);

    void renderDescriptorToRoot(
            Path parentSourceRoot,
            String descriptorTemplatePath,
            Path rootTarget,
            Map<String, JsonNode> parameters);

    void recordParentLineage(
            Path rootTarget,
            BlueprintVersion parentVersion,
            Map<String, JsonNode> parentResolvedParameters);
}
