package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.publish;

import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.manifestautofiller.ManifestAutoFiller;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.manifestvalidator.ManifestValidator;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.manifestvalidator.OdmBlueprintValidatorFactory;
import com.fasterxml.jackson.databind.JsonNode;
import org.opendatamesh.platform.pp.blueprint.manifest.parser.ManifestParser;
import org.opendatamesh.platform.pp.blueprint.manifest.parser.ManifestParserFactory;
import org.opendatamesh.platform.pp.blueprint.manifest.model.Manifest;
import org.opendatamesh.platform.pp.blueprint.exceptions.BadRequestException;
import java.io.IOException;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.manifestautofiller.OdmBlueprintManifestAutoFillerFactory;
class PublishBlueprintVersionManifestOutboundPortImpl implements PublishBlueprintVersionManifestOutboundPort {

    private final OdmBlueprintValidatorFactory manifestValidatorFactory;
    private final ManifestParser manifestParser;
    private final OdmBlueprintManifestAutoFillerFactory manifestAutoFillerFactory;  
    PublishBlueprintVersionManifestOutboundPortImpl(OdmBlueprintValidatorFactory manifestValidatorFactory, OdmBlueprintManifestAutoFillerFactory manifestAutoFillerFactory) {
        this.manifestValidatorFactory = manifestValidatorFactory;
        this.manifestParser = ManifestParserFactory.getParser();
        this.manifestAutoFillerFactory = manifestAutoFillerFactory;
    }

    @Override
    public void validateManifest(String manifestSpec, String manifestSpecVersion, JsonNode content) {
        ManifestValidator manifestValidator = manifestValidatorFactory.getManifestValidator(manifestSpec, manifestSpecVersion);
        manifestValidator.validateManifest(content);
    }

    @Override
    public JsonNode autofillManifest(String manifestSpec, String manifestSpecVersion, JsonNode content) {
        ManifestAutoFiller manifestAutoFiller = manifestAutoFillerFactory.getManifestAutoFiller(manifestSpec, manifestSpecVersion);
        return manifestAutoFiller.autofillManifest(content);
    }

    @Override
    public String extractVersionNumber(JsonNode manifestContent) {
        try {
            Manifest manifest = manifestParser.deserialize(manifestContent);
            if (manifest == null || manifest.getVersion() == null || manifest.getVersion().isBlank()) {
                throw new BadRequestException("Manifest version is required");
            }
            return manifest.getVersion();
        } catch (IOException e) {
            throw new BadRequestException("Invalid manifest content: " + e.getMessage(), e);
        }
    }

    @Override
    public String extractSpecNumber(JsonNode manifestContent) {
        try {
            Manifest manifest = manifestParser.deserialize(manifestContent);
            if (manifest == null || manifest.getSpec() == null || manifest.getSpec().isBlank()) {
                throw new BadRequestException("Manifest spec is required");
            }
            return manifest.getSpec();
        } catch (IOException e) {
            throw new BadRequestException("Invalid manifest content: " + e.getMessage(), e);
        }
    }

    @Override
    public String extractSpecVersion(JsonNode manifestContent) {
        try {
            Manifest manifest = manifestParser.deserialize(manifestContent);
            if (manifest == null || manifest.getSpecVersion() == null || manifest.getSpecVersion().isBlank()) {
                throw new BadRequestException("Manifest spec version is required");
            }
            return manifest.getSpecVersion();
        } catch (IOException e) {
            throw new BadRequestException("Invalid manifest content: " + e.getMessage(), e);
        }
    }
}
