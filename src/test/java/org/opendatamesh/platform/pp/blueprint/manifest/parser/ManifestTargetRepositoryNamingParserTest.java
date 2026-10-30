package org.opendatamesh.platform.pp.blueprint.manifest.parser;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.opendatamesh.platform.pp.blueprint.manifest.ManifestYamlTestSupport;
import org.opendatamesh.platform.pp.blueprint.manifest.model.Manifest;
import org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation.ManifestTargetRepositoryNaming;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ManifestTargetRepositoryNamingParserTest {

    /*
     * Feature: Repository naming conventions in the manifest
     * Scenario: Naming survives parse and serialize, including unknown keys
     *   Given a manifest whose naming object has an extra unknown key
     *   When it is deserialized and serialized
     *   Then pattern, enforcement and the unknown key are preserved
     */
    @Test
    void whenParseAndSerializeNamingThenPreserveFieldsAndUnknownKeys() throws IOException {
        JsonNode initialTree = ManifestYamlTestSupport.readYamlTreeFromClasspath("/manifest/repository-naming-valid.yaml");
        ManifestParser parser = ManifestParserFactory.getParser();

        Manifest manifest = parser.deserialize(initialTree);
        JsonNode serialized = parser.serialize(manifest);

        ManifestYamlTestSupport.assertSerializedJsonTreeEqualsInitialRead(initialTree, serialized);
        ManifestTargetRepositoryNaming suggested = manifest.getTargetRepositories().get(0).getNaming();
        assertThat(suggested.getPattern()).isEqualTo("${dpName}-infra");
        assertThat(suggested.getEnforcement()).isEqualTo(ManifestTargetRepositoryNaming.ENFORCEMENT_SUGGESTED);
        ManifestTargetRepositoryNaming binding = manifest.getTargetRepositories().get(1).getNaming();
        assertThat(binding.getPattern()).isEqualTo("${dpDomain}-${dpName}");
        assertThat(binding.getEnforcement()).isNull();
        assertThat(binding.getAdditionalProperties()).containsKey("futureOption");
    }
}
