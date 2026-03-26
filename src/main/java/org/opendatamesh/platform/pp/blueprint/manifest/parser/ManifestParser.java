package org.opendatamesh.platform.pp.blueprint.manifest.parser;

import com.fasterxml.jackson.databind.JsonNode;
import org.opendatamesh.platform.pp.blueprint.manifest.extension.ManifestComponentBaseExtendedConverter;
import org.opendatamesh.platform.pp.blueprint.manifest.model.Manifest;
import org.opendatamesh.platform.pp.blueprint.manifest.model.core.ManifestComponentBase;

import java.io.IOException;

/**
 * Serializes and deserializes {@link Manifest} documents (JSON or YAML loaded to {@link JsonNode} by the caller).
 */
public interface ManifestParser {

    /**
     * Deserializes a JSON tree into a {@link Manifest}.
     *
     * @param jsonNode the document root (typically from {@code ObjectMapper.readTree} for JSON or YAML)
     * @return the parsed manifest
     * @throws IOException if deserialization fails
     */
    Manifest deserialize(JsonNode jsonNode) throws IOException;

    /**
     * Serializes a {@link Manifest} to a JSON tree.
     *
     * @param manifest the manifest to serialize
     * @return the JSON representation
     * @throws IOException if serialization fails
     */
    JsonNode serialize(Manifest manifest) throws IOException;

    /**
     * Registers a converter for typed handling of extension properties on {@link ManifestComponentBase} nodes
     * (same idea as {@code Parser#register(ComponentBaseExtendedConverter)} for descriptors).
     *
     * @param converter the converter to register
     * @param <T>       the concrete extension type
     * @return this parser (fluent)
     */
    <T extends ManifestComponentBase> ManifestParser register(ManifestComponentBaseExtendedConverter<T> converter);
}
