package org.opendatamesh.platform.pp.blueprint.manifest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Loads README-style manifest YAML from the test classpath into {@link JsonNode} for {@link org.opendatamesh.platform.pp.blueprint.manifest.parser.ManifestParser}.
 */
public final class ManifestYamlTestSupport {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    private ManifestYamlTestSupport() {
    }

    public static ObjectMapper yamlObjectMapper() {
        return YAML_MAPPER;
    }

    /**
     * @param classpathPath e.g. {@code /manifest/example-2.1-monorepo-no-composition.yaml}
     */
    public static JsonNode readYamlTreeFromClasspath(String classpathPath) throws IOException {
        try (InputStream in = ManifestYamlTestSupport.class.getResourceAsStream(classpathPath)) {
            Objects.requireNonNull(in, "Classpath resource not found: " + classpathPath);
            return YAML_MAPPER.readTree(in);
        }
    }

    /**
     * Asserts {@code parser.serialize(manifest)} is structurally the same as the tree from the first read (YAML → JsonNode).
     */
    public static void assertSerializedJsonTreeEqualsInitialRead(JsonNode initialRead, JsonNode serialized) {
        assertThat(serialized).usingRecursiveComparison().isEqualTo(initialRead);
    }
}
