package org.opendatamesh.platform.pp.blueprint.manifest.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opendatamesh.platform.pp.blueprint.manifest.extension.ManifestComponentBaseExtendedConverter;
import org.opendatamesh.platform.pp.blueprint.manifest.model.Manifest;
import org.opendatamesh.platform.pp.blueprint.manifest.model.core.ManifestComponentBase;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import static org.opendatamesh.platform.pp.blueprint.manifest.parser.ManifestExtensionHandler.ExtensionHandlerStatus.DESERIALIZING;
import static org.opendatamesh.platform.pp.blueprint.manifest.parser.ManifestExtensionHandler.ExtensionHandlerStatus.SERIALIZING;

/**
 * Jackson-based {@link ManifestParser} implementation (same approach as {@code org.opendatamesh.dpds.parser.ParserImpl}).
 */
class ManifestParserImpl implements ManifestParser {

    private final ObjectMapper objectMapper;
    private final List<ManifestComponentBaseExtendedConverter<ManifestComponentBase>> componentBaseExtendedConverters =
            new ArrayList<>();

    ManifestParserImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Manifest deserialize(JsonNode raw) throws IOException {
        JsonNode jsonNode = raw.deepCopy();
        Manifest manifest = objectMapper.treeToValue(jsonNode, Manifest.class);
        if (!componentBaseExtendedConverters.isEmpty()) {
            ManifestExtensionHandler extensionHandler =
                    new ManifestExtensionHandler(DESERIALIZING, componentBaseExtendedConverters, objectMapper);
            ManifestExtensionVisitorImpl visitor = new ManifestExtensionVisitorImpl(extensionHandler);
            visitor.visit(manifest);
        }
        return manifest;
    }

    @Override
    public JsonNode serialize(Manifest manifest) throws IOException {
        Manifest toSerialize = manifest;
        if (!componentBaseExtendedConverters.isEmpty()) {
            toSerialize = deepCopy(manifest);
            ManifestExtensionHandler extensionHandler =
                    new ManifestExtensionHandler(SERIALIZING, componentBaseExtendedConverters, objectMapper);
            ManifestExtensionVisitorImpl visitor = new ManifestExtensionVisitorImpl(extensionHandler);
            visitor.visit(toSerialize);
        }
        return objectMapper.valueToTree(toSerialize);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends ManifestComponentBase> ManifestParser register(ManifestComponentBaseExtendedConverter<T> converter) {
        componentBaseExtendedConverters.add((ManifestComponentBaseExtendedConverter<ManifestComponentBase>) converter);
        return this;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Serializable> T deepCopy(T object) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(object);
            out.flush();
            try (ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
                 ObjectInputStream in = new ObjectInputStream(bis)) {
                return (T) in.readObject();
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
