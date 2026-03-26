package org.opendatamesh.platform.pp.blueprint.manifest.extensions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opendatamesh.platform.pp.blueprint.manifest.model.Manifest;
import org.opendatamesh.platform.pp.blueprint.manifest.model.core.ManifestComponentBase;
import org.opendatamesh.platform.pp.blueprint.manifest.extension.ManifestComponentBaseExtendedConverter;

class ManifestDumbExtensionConverter implements ManifestComponentBaseExtendedConverter<ManifestDumbExtension> {

    @Override
    public boolean supports(String key, Class<? extends ManifestComponentBase> parentClass) {
        return parentClass == Manifest.class && "dumb_extension".equalsIgnoreCase(key);
    }

    @Override
    public ManifestDumbExtension deserialize(ObjectMapper defaultMapper, JsonNode jsonNode) {
        try {
            return defaultMapper.treeToValue(jsonNode, ManifestDumbExtension.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public JsonNode serialize(ObjectMapper defaultMapper, ManifestDumbExtension value) {
        return defaultMapper.valueToTree(value);
    }
}
