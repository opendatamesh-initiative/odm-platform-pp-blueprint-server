package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.manifestautofiller;

import com.fasterxml.jackson.databind.JsonNode;
import org.opendatamesh.platform.pp.blueprint.manifest.parser.ManifestParser;
import org.opendatamesh.platform.pp.blueprint.manifest.parser.ManifestParserFactory;
import org.opendatamesh.platform.pp.blueprint.manifest.model.Manifest;
import java.io.IOException;
import org.opendatamesh.platform.pp.blueprint.exceptions.BadRequestException;

class OdmBlueprintManifestAutoFiller implements ManifestAutoFiller {

    private final ManifestParser parser = ManifestParserFactory.getParser();

    @Override
    public JsonNode autofillManifest(JsonNode manifestContent, String blueprintName) {

        Manifest manifest;
        try {
            manifest = parser.deserialize(manifestContent);
        } catch (IOException e) {
            throw new BadRequestException("Invalid manifest content: " + e.getMessage(), e);
        }
        if (manifest == null) {
            throw new BadRequestException("Invalid manifest content: manifest is null");
        }

        OdmBlueprintManifestAutoFillerVisitor visitor = new OdmBlueprintManifestAutoFillerVisitor(blueprintName);
        manifest.accept(visitor);

        try {
            return parser.serialize(manifest);
        } catch (IOException e) {
            throw new BadRequestException("Failed to serialize manifest: " + e.getMessage(), e);
        }
    }
}
