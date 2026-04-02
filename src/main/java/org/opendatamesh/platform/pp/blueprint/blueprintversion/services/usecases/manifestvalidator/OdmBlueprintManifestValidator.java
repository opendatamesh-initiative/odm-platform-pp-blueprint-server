package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.manifestvalidator;

import org.opendatamesh.platform.pp.blueprint.manifest.parser.ManifestParser;
import org.opendatamesh.platform.pp.blueprint.manifest.parser.ManifestParserFactory;
import org.opendatamesh.platform.pp.blueprint.manifest.model.Manifest;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import org.opendatamesh.platform.pp.blueprint.exceptions.BadRequestException;

class OdmBlueprintManifestValidator implements ManifestValidator {
    
    private final ManifestParser parser = ManifestParserFactory.getParser();

    @Override
    public void validateManifest(JsonNode manifestContent) {
        OdmBlueprintManifestValidatorContext context = new OdmBlueprintManifestValidatorContext();

        Manifest manifest;
        try {
            manifest = parser.deserialize(manifestContent);
        } catch (IOException e) {
            throw new BadRequestException("Invalid manifest content: " + e.getMessage(), e);
        }
        if (manifest == null) {
            throw new BadRequestException("Invalid manifest content: manifest is null");
        }

        OdmBlueprintValidationVisitor visitor = new OdmBlueprintValidationVisitor(context);
        manifest.accept(visitor);

        context.throwIfHasErrors();
    }
}
