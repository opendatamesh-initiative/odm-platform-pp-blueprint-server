package org.opendatamesh.platform.pp.blueprint.manifest.parser;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Factory for {@link ManifestParser}, aligned with {@code org.opendatamesh.dpds.parser.ParserFactory}.
 */
public abstract class ManifestParserFactory {

    private ManifestParserFactory() {
    }

    public static ManifestParser getParser() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        return new ManifestParserImpl(objectMapper);
    }

    public static ManifestParser getParser(ObjectMapper objectMapper) {
        return new ManifestParserImpl(objectMapper);
    }
}
