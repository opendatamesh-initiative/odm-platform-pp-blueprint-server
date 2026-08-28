package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.updatedataproduct;

import com.fasterxml.jackson.databind.JsonNode;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.BlueprintDataProductDescriptorService;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.BlueprintRenderService;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

class UpdateDataProductTemplatingOutboundPortImpl implements UpdateDataProductTemplatingOutboundPort {

    private final BlueprintRenderService blueprintRenderService;
    private final BlueprintDataProductDescriptorService blueprintDataProductDescriptorService;

    UpdateDataProductTemplatingOutboundPortImpl(
            BlueprintRenderService blueprintRenderService,
            BlueprintDataProductDescriptorService blueprintDataProductDescriptorService
    ) {
        this.blueprintRenderService = blueprintRenderService;
        this.blueprintDataProductDescriptorService = blueprintDataProductDescriptorService;
    }

    @Override
    public void applyRoute(
            Path sourceRoot,
            String sourcePath,
            Path targetRoot,
            String destinationPath,
            Map<String, JsonNode> parameters
    ) {
        blueprintRenderService.renderAndCopySubtree(
                sourceRoot, sourcePath, targetRoot, destinationPath, parameters);
    }

    @Override
    public void renderDescriptorToRoot(
            Path parentSourceRoot,
            String descriptorTemplatePath,
            Path rootTarget,
            Map<String, JsonNode> parameters
    ) {
        blueprintRenderService.renderDescriptorTemplate(
                parentSourceRoot, descriptorTemplatePath, rootTarget, parameters);
    }

    @Override
    public void recordParentLineage(
            Path rootTarget,
            BlueprintVersion parentVersion,
            Map<String, JsonNode> parentResolvedParameters
    ) {
        blueprintDataProductDescriptorService.enrichDescriptorWithBlueprintMetadata(
                rootTarget,
                parentVersion,
                parentResolvedParameters);
        blueprintRenderService.relocateParentLineageSidecar(rootTarget, parentVersion);
    }

    @Override
    public void relocateModuleReferencedFiles(
            Path targetRoot,
            String moduleAlias,
            BlueprintVersion moduleVersion,
            Path moduleSourceRoot,
            List<String> destinationPaths
    ) {
        blueprintRenderService.relocateModuleReferencedFiles(
                targetRoot, moduleAlias, moduleVersion, moduleSourceRoot, destinationPaths);
    }
}
