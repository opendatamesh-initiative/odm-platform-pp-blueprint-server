package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate;

import com.fasterxml.jackson.databind.JsonNode;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.BlueprintDataProductDescriptorService;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.BlueprintRenderService;

import java.nio.file.Path;
import java.util.Map;

class InstantiateBlueprintVersionTemplatingOutboundPortImpl implements InstantiateBlueprintVersionTemplatingOutboundPort {

    private final BlueprintRenderService blueprintRenderService;
    private final BlueprintDataProductDescriptorService blueprintDataProductDescriptorService;

    InstantiateBlueprintVersionTemplatingOutboundPortImpl(
            BlueprintRenderService blueprintRenderService,
            BlueprintDataProductDescriptorService blueprintDataProductDescriptorService
    ) {
        this.blueprintRenderService = blueprintRenderService;
        this.blueprintDataProductDescriptorService = blueprintDataProductDescriptorService;
    }

    @Override
    public void monorepoNoCompositionRenderAndCopy(
            BlueprintVersion blueprintVersion,
            Map<String, JsonNode> parameters,
            Path sourceRoot,
            Path targetRoot
    ) {
        blueprintRenderService.monorepoNoCompositionRenderAndCopy(blueprintVersion, parameters, sourceRoot, targetRoot);
    }

    @Override
    public void enrichDescriptorWithBlueprintMetadata(
            Path rootTarget,
            BlueprintVersion version,
            Map<String, JsonNode> parameters
    ) {
        blueprintDataProductDescriptorService.enrichDescriptorWithBlueprintMetadata(rootTarget, version, parameters);
    }
}
