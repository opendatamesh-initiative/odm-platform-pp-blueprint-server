package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.manifestautofiller;

import com.fasterxml.jackson.databind.JsonNode;
import org.opendatamesh.platform.pp.blueprint.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.blueprint.manifest.model.Manifest;
import org.opendatamesh.platform.pp.blueprint.manifest.parser.ManifestParser;
import org.opendatamesh.platform.pp.blueprint.manifest.parser.ManifestParserFactory;

import java.io.IOException;

public class OdmBlueprintManifestAutoFiller implements ManifestAutoFiller {

    /** Default logical repository key seeded when {@code instantiation.repositories} is empty. */
    public static final String DEFAULT_REPOSITORY_KEY = "main";

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
