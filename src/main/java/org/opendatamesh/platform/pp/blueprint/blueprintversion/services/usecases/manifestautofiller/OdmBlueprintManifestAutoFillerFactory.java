package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.manifestautofiller;

import org.opendatamesh.platform.pp.blueprint.exceptions.BadRequestException;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Component;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.ManifestSpec;

@Component
public class OdmBlueprintManifestAutoFillerFactory {
    
    public ManifestAutoFiller getManifestAutoFiller(String manifestSpec, String manifestSpecVersion) {
        if (!StringUtils.hasText(manifestSpec)) {
            throw new BadRequestException("Manifest spec is required");
        }
        if (!StringUtils.hasText(manifestSpecVersion)) {
            throw new BadRequestException("Manifest spec version is required");
        }
        if (manifestSpec.equalsIgnoreCase(ManifestSpec.ODM_BLUEPRINT_MANIFEST.getSpec()) && manifestSpecVersion.matches("1\\..*")) {
            return new OdmBlueprintManifestAutoFiller();
        }
        throw new BadRequestException(
            String.format("Unsupported manifest specification: %s version %s. Currently only ODM Blueprint Manifest 1.x is supported.", manifestSpec, manifestSpecVersion));
    }
}
