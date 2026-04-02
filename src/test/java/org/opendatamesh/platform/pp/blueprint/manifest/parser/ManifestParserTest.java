package org.opendatamesh.platform.pp.blueprint.manifest.parser;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.opendatamesh.platform.pp.blueprint.manifest.ManifestYamlTestSupport;
import org.opendatamesh.platform.pp.blueprint.manifest.model.Manifest;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestComposition;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestInstantiation;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestParameter;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestParserTest {

    @Test
    void givenReadmeExample21MonorepoYamlWhenDeserializeAndSerializeThenManifestMatchesReadmeAndRoundTrips() throws IOException {
        // given
        ManifestParser parser = ManifestParserFactory.getParser();
        JsonNode tree = ManifestYamlTestSupport.readYamlTreeFromClasspath("/manifest/example-2.1-monorepo-no-composition.yaml");

        // when
        Manifest manifest = parser.deserialize(tree);

        // then
        assertEquals(Manifest.SPEC_NAME, manifest.getSpec());
        assertEquals("1.0.0", manifest.getSpecVersion());
        assertEquals("analytics-lakehouse", manifest.getName());
        assertEquals("Analytics Lakehouse Blueprint", manifest.getDisplayName());
        assertEquals("1.0.0", manifest.getVersion());
        assertEquals(2, manifest.getParameters().size());

        ManifestParameter env = manifest.getParameters().get(0);
        assertEquals("environment", env.getKey());
        assertEquals(ManifestParameter.ManifestParameterType.STRING, env.getType());
        assertTrue(Boolean.TRUE.equals(env.getRequired()));
        assertEquals(3, env.getValidation().getAllowedValues().size());

        ManifestParameter retention = manifest.getParameters().get(1);
        assertEquals("retentionDays", retention.getKey());
        assertEquals(ManifestParameter.ManifestParameterType.INTEGER, retention.getType());
        assertNotNull(retention.getDefaultValue());
        assertTrue(retention.getDefaultValue().isIntegralNumber());
        assertEquals(90, retention.getDefaultValue().intValue());

        assertEquals(2, manifest.getProtectedResources().size());
        assertEquals("infrastructure/core/**", manifest.getProtectedResources().get(0).getPath());
        assertEquals("README.md", manifest.getProtectedResources().get(1).getPath());

        assertNotNull(manifest.getInstantiation());
        assertEquals(ManifestInstantiation.InstantiationStrategy.MONOREPO, manifest.getInstantiation().getStrategy());

        JsonNode serialized = parser.serialize(manifest);
        ManifestYamlTestSupport.assertSerializedJsonTreeEqualsInitialRead(tree, serialized);

        Manifest again = parser.deserialize(serialized);
        assertThat(again).usingRecursiveComparison().isEqualTo(manifest);
    }

    @Test
    void givenReadmeExample22CompositionYamlWhenDeserializeAndSerializeThenCompositionLayoutMatchesAndRoundTrips() throws IOException {
        // given
        ManifestParser parser = ManifestParserFactory.getParser();
        JsonNode tree = ManifestYamlTestSupport.readYamlTreeFromClasspath("/manifest/example-2.2-monorepo-composition.yaml");

        // when
        Manifest m = parser.deserialize(tree);

        // then
        assertEquals("full-stack-dp", m.getName());
        assertEquals(2, m.getComposition().size());
        ManifestComposition storage = m.getComposition().get(0);
        assertEquals("storage", storage.getModule());
        assertEquals("odm-blueprint-s3-lake", storage.getBlueprintName());
        assertEquals(2, storage.getParameterMapping().size());
        assertTrue(storage.getParameterMapping().containsKey("bucketPrefix"));

        assertEquals(2, m.getInstantiation().getCompositionLayout().size());
        assertEquals("data-plane/storage", m.getInstantiation().getCompositionLayout().get(0).getTargetPath());
        assertEquals(ManifestInstantiation.InstantiationStrategy.MONOREPO, m.getInstantiation().getStrategy());

        JsonNode serialized = parser.serialize(m);
        ManifestYamlTestSupport.assertSerializedJsonTreeEqualsInitialRead(tree, serialized);

        Manifest again = parser.deserialize(serialized);
        assertThat(again).usingRecursiveComparison().isEqualTo(m);
    }

    @Test
    void givenReadmeExample23PolyrepoYamlWhenDeserializeAndSerializeThenTargetsMatchAndRoundTrips() throws IOException {
        // given
        ManifestParser parser = ManifestParserFactory.getParser();
        JsonNode tree = ManifestYamlTestSupport.readYamlTreeFromClasspath("/manifest/example-2.3-polyrepo-no-composition.yaml");

        // when
        Manifest m = parser.deserialize(tree);

        // then
        assertEquals(ManifestInstantiation.InstantiationStrategy.POLYREPO, m.getInstantiation().getStrategy());
        assertEquals(3, m.getInstantiation().getTargets().size());
        assertEquals("-infra", m.getInstantiation().getTargets().get(0).getRepositoryNamePostfix());
        assertEquals("terraform/", m.getInstantiation().getTargets().get(0).getSourcePath());
        assertEquals("-apps", m.getInstantiation().getTargets().get(1).getRepositoryNamePostfix());
        assertEquals("policies/", m.getInstantiation().getTargets().get(2).getTargetPath());

        JsonNode serialized = parser.serialize(m);
        ManifestYamlTestSupport.assertSerializedJsonTreeEqualsInitialRead(tree, serialized);

        Manifest again = parser.deserialize(serialized);
        assertThat(again).usingRecursiveComparison().isEqualTo(m);
    }

    @Test
    void givenReadmeExample24PolyrepoCompositionYamlWhenDeserializeAndSerializeThenModulesAndTargetsMatchAndRoundTrips() throws IOException {
        // given
        ManifestParser parser = ManifestParserFactory.getParser();
        JsonNode tree = ManifestYamlTestSupport.readYamlTreeFromClasspath("/manifest/example-2.4-polyrepo-composition.yaml");

        // when
        Manifest m = parser.deserialize(tree);

        // then
        assertEquals(2, m.getComposition().size());
        assertEquals("ingest", m.getComposition().get(0).getModule());
        assertEquals(ManifestInstantiation.InstantiationStrategy.POLYREPO, m.getInstantiation().getStrategy());
        assertEquals(2, m.getInstantiation().getTargets().size());
        assertEquals("ingest", m.getInstantiation().getTargets().get(0).getModule());
        assertEquals("-pipeline", m.getInstantiation().getTargets().get(0).getRepositoryNamePostfix());
        assertEquals("consume", m.getInstantiation().getTargets().get(1).getModule());

        JsonNode serialized = parser.serialize(m);
        ManifestYamlTestSupport.assertSerializedJsonTreeEqualsInitialRead(tree, serialized);

        Manifest again = parser.deserialize(serialized);
        assertThat(again).usingRecursiveComparison().isEqualTo(m);
    }
}
