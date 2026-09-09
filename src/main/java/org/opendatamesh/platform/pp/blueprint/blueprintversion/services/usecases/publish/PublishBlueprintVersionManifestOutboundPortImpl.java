package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.publish;

import com.fasterxml.jackson.databind.JsonNode;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.InstantiationScenarioResolver;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.manifestautofiller.ManifestAutoFiller;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.manifestautofiller.OdmBlueprintManifestAutoFillerFactory;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.manifestvalidator.ManifestValidator;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.manifestvalidator.OdmBlueprintValidatorFactory;
import org.opendatamesh.platform.pp.blueprint.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.blueprint.manifest.model.Manifest;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestComposition;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestParameter;
import org.opendatamesh.platform.pp.blueprint.manifest.parser.ManifestParser;
import org.opendatamesh.platform.pp.blueprint.manifest.parser.ManifestParserFactory;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class PublishBlueprintVersionManifestOutboundPortImpl implements PublishBlueprintVersionManifestOutboundPort {

    private final OdmBlueprintValidatorFactory manifestValidatorFactory;
    private final ManifestParser manifestParser;
    private final OdmBlueprintManifestAutoFillerFactory manifestAutoFillerFactory;

    PublishBlueprintVersionManifestOutboundPortImpl(
            OdmBlueprintValidatorFactory manifestValidatorFactory,
            OdmBlueprintManifestAutoFillerFactory manifestAutoFillerFactory
    ) {
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
    public List<PublishCompositionIdentity> listCompositionIdentities(JsonNode content) {
        try {
            Manifest manifest = manifestParser.deserialize(content);
            if (manifest == null || manifest.getComposition() == null || manifest.getComposition().isEmpty()) {
                return List.of();
            }

            List<ManifestComposition> compositions = manifest.getComposition();
            List<PublishCompositionIdentity> identities = new ArrayList<>();
            for (int i = 0; i < compositions.size(); i++) {
                ManifestComposition composition = compositions.get(i);
                if (composition == null) {
                    continue;
                }
                if (!StringUtils.hasText(composition.getBlueprintName())
                        || !StringUtils.hasText(composition.getBlueprintVersion())) {
                    continue;
                }
                identities.add(new PublishCompositionIdentity(
                        composition.getModule(),
                        composition.getBlueprintName().trim(),
                        composition.getBlueprintVersion().trim(),
                        "composition[" + i + "]"));
            }
            return identities;
        } catch (IOException e) {
            throw new BadRequestException("Invalid manifest content: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isMonorepoNoComposition(JsonNode content) {
        try {
            return InstantiationScenarioResolver.isMonorepoNoComposition(manifestParser.deserialize(content));
        } catch (IOException e) {
            throw new BadRequestException("Invalid composition module manifest content: " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> listMappedChildParameterKeys(JsonNode parentContent, String compositionFieldPath) {
        try {
            Manifest manifest = manifestParser.deserialize(parentContent);
            ManifestComposition composition = compositionAtFieldPath(manifest, compositionFieldPath);
            if (composition == null) {
                return List.of();
            }
            Map<String, JsonNode> parameterMapping = composition.getParameterMapping();
            if (parameterMapping == null || parameterMapping.isEmpty()) {
                return List.of();
            }
            List<String> keys = new ArrayList<>();
            for (String key : parameterMapping.keySet()) {
                if (StringUtils.hasText(key)) {
                    keys.add(key.trim());
                }
            }
            return keys;
        } catch (IOException e) {
            throw new BadRequestException("Invalid manifest content: " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> listModuleParameterKeysWithoutDefault(JsonNode moduleContent) {
        try {
            Manifest manifest = manifestParser.deserialize(moduleContent);
            if (manifest == null || manifest.getParameters() == null || manifest.getParameters().isEmpty()) {
                return List.of();
            }
            List<String> keys = new ArrayList<>();
            for (ManifestParameter parameter : manifest.getParameters()) {
                if (parameter == null || !StringUtils.hasText(parameter.getKey())) {
                    continue;
                }
                if (!hasDefault(parameter)) {
                    keys.add(parameter.getKey().trim());
                }
            }
            return keys;
        } catch (IOException e) {
            throw new BadRequestException("Invalid composition module manifest content: " + e.getMessage(), e);
        }
    }

    @Override
    public JsonNode autofillManifest(String manifestSpec, String manifestSpecVersion, JsonNode content, String blueprintName) {
        ManifestAutoFiller manifestAutoFiller = manifestAutoFillerFactory.getManifestAutoFiller(manifestSpec, manifestSpecVersion);
        return manifestAutoFiller.autofillManifest(content, blueprintName);
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

    private ManifestComposition compositionAtFieldPath(Manifest manifest, String compositionFieldPath) {
        if (manifest == null || manifest.getComposition() == null || !StringUtils.hasText(compositionFieldPath)) {
            return null;
        }
        List<ManifestComposition> compositions = manifest.getComposition();
        for (int i = 0; i < compositions.size(); i++) {
            if (("composition[" + i + "]").equals(compositionFieldPath)) {
                return compositions.get(i);
            }
        }
        return null;
    }

    private boolean hasDefault(ManifestParameter parameter) {
        JsonNode defaultValue = parameter.getDefaultValue();
        return defaultValue != null && !defaultValue.isNull();
    }

}
