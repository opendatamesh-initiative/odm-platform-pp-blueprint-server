package org.opendatamesh.platform.pp.blueprint.manifest.extension;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opendatamesh.platform.pp.blueprint.manifest.model.core.ManifestComponentBase;

/**
 * Converts unknown manifest properties (captured in {@link ManifestComponentBase#getAdditionalProperties()}) into
 * typed {@link ManifestComponentBase} subclasses for deserialization, and the reverse for serialization — same role as
 * {@code org.opendatamesh.dpds.extensions.ComponentBaseExtendedConverter} for descriptors.
 */
public interface ManifestComponentBaseExtendedConverter<T extends ManifestComponentBase> {

    boolean supports(String key, Class<? extends ManifestComponentBase> parentClass);

    T deserialize(ObjectMapper defaultMapper, JsonNode jsonNode) throws JacksonException;

    JsonNode serialize(ObjectMapper defaultMapper, T value) throws JacksonException;
}
