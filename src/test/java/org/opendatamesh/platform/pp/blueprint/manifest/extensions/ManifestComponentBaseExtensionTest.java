package org.opendatamesh.platform.pp.blueprint.manifest.extensions;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.opendatamesh.platform.pp.blueprint.manifest.ManifestYamlTestSupport;
import org.opendatamesh.platform.pp.blueprint.manifest.model.Manifest;
import org.opendatamesh.platform.pp.blueprint.manifest.parser.ManifestParser;
import org.opendatamesh.platform.pp.blueprint.manifest.parser.ManifestParserFactory;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ManifestComponentBaseExtensionTest {

    @Test
    void givenYamlWithExtensionAndRegisteredConverterWhenDeserializeAndSerializeThenParsedExtensionRoundTripsAndMatchesTree() throws IOException {
        // given
        JsonNode initialTree = ManifestYamlTestSupport.readYamlTreeFromClasspath("/manifest/manifest-with-extension.yaml");
        ManifestParser parser = ManifestParserFactory.getParser()
                .register(new ManifestDumbExtensionConverter());

        // when
        Manifest manifest = parser.deserialize(initialTree);
        JsonNode serialized = parser.serialize(manifest);

        // then — serialized JSON matches the document tree produced from the first read (YAML → JsonNode)
        ManifestYamlTestSupport.assertSerializedJsonTreeEqualsInitialRead(initialTree, serialized);

        assertEquals(1, manifest.getAdditionalProperties().size());
        assertEquals(1, manifest.getParsedProperties().size());
        assertInstanceOf(ManifestDumbExtension.class, manifest.getParsedProperties().get("dumb_extension"));
        ManifestDumbExtension ext = (ManifestDumbExtension) manifest.getParsedProperties().get("dumb_extension");
        assertEquals("valueA", ext.getFieldA());
        assertEquals("valueB", ext.getFieldB());

        Manifest again = parser.deserialize(serialized);
        assertThat(again).usingRecursiveComparison().isEqualTo(manifest);
    }
}
