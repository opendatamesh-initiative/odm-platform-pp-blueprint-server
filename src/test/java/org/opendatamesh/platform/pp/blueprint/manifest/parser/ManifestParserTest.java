package org.opendatamesh.platform.pp.blueprint.manifest.parser;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.manifestautofiller.OdmBlueprintManifestAutoFiller;
import org.opendatamesh.platform.pp.blueprint.manifest.ManifestYamlTestSupport;
import org.opendatamesh.platform.pp.blueprint.manifest.model.Manifest;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestComposition;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestParameter;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestParserTest {

    @Test
    void givenReadmeExample21MonorepoYamlWhenDeserializeAndSerializeThenManifestMatchesReadmeAndRoundTrips() throws IOException {
        ManifestParser parser = ManifestParserFactory.getParser();
        JsonNode tree = ManifestYamlTestSupport.readYamlTreeFromClasspath("/manifest/example-2.1-monorepo-no-composition.yaml");

        Manifest manifest = parser.deserialize(tree);

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
        assertEquals(1, manifest.getInstantiation().getRepositories().size());
        assertEquals(OdmBlueprintManifestAutoFiller.DEFAULT_REPOSITORY_KEY, manifest.getInstantiation().getRepositories().get(0).getKey());
        assertEquals(OdmBlueprintManifestAutoFiller.DEFAULT_REPOSITORY_KEY, manifest.getInstantiation().getRoot().getRepository());
        assertEquals(1, manifest.getInstantiation().getRoot().getTargets().size());
        assertEquals(OdmBlueprintManifestAutoFiller.DEFAULT_REPOSITORY_KEY, manifest.getInstantiation().getRoot().getTargets().get(0).getRepository());
        assertEquals("./", manifest.getInstantiation().getRoot().getTargets().get(0).getPath());

        JsonNode serialized = parser.serialize(manifest);
        ManifestYamlTestSupport.assertSerializedJsonTreeEqualsInitialRead(tree, serialized);

        Manifest again = parser.deserialize(serialized);
        assertThat(again).usingRecursiveComparison().isEqualTo(manifest);
    }

    @Test
    void givenReadmeExample22CompositionYamlWhenDeserializeAndSerializeThenCompositionTargetsMatchAndRoundTrips() throws IOException {
        ManifestParser parser = ManifestParserFactory.getParser();
        JsonNode tree = ManifestYamlTestSupport.readYamlTreeFromClasspath("/manifest/example-2.2-monorepo-composition.yaml");

        Manifest m = parser.deserialize(tree);

        assertEquals("full-stack-dp", m.getName());
        assertEquals(2, m.getComposition().size());
        ManifestComposition storage = m.getComposition().get(0);
        assertEquals("storage", storage.getModule());
        assertEquals("odm-blueprint-s3-lake", storage.getBlueprintName());
        assertEquals(3, storage.getParameterMapping().size());
        assertTrue(storage.getParameterMapping().containsKey("bucketPrefix"));
        assertTrue(storage.getParameterMapping().containsKey("encryptAtRest"));
        assertTrue(storage.getParameterMapping().containsKey("region"));
        JsonNode bucketPrefix = storage.getParameterMapping().get("bucketPrefix");
        assertTrue(bucketPrefix.isObject());
        assertEquals("projectSlug", bucketPrefix.get("$param").asText());
        JsonNode region = storage.getParameterMapping().get("region");
        assertTrue(region.isObject());
        assertEquals("eu-west-1", region.get("value").asText());
        assertEquals(1, storage.getTargets().size());
        assertEquals("data-plane/storage", storage.getTargets().get(0).getPath());
        assertEquals(OdmBlueprintManifestAutoFiller.DEFAULT_REPOSITORY_KEY, storage.getTargets().get(0).getRepository());

        assertEquals(1, m.getInstantiation().getRepositories().size());
        assertEquals(OdmBlueprintManifestAutoFiller.DEFAULT_REPOSITORY_KEY, m.getInstantiation().getRepositories().get(0).getKey());
        assertEquals(OdmBlueprintManifestAutoFiller.DEFAULT_REPOSITORY_KEY, m.getInstantiation().getRoot().getRepository());
        assertEquals(1, m.getInstantiation().getRoot().getTargets().size());
        assertEquals("core/", m.getInstantiation().getRoot().getTargets().get(0).getPath());

        JsonNode serialized = parser.serialize(m);
        ManifestYamlTestSupport.assertSerializedJsonTreeEqualsInitialRead(tree, serialized);

        Manifest again = parser.deserialize(serialized);
        assertThat(again).usingRecursiveComparison().isEqualTo(m);
    }

    @Test
    void givenReadmeExample23PolyrepoYamlWhenDeserializeAndSerializeThenTargetsMatchAndRoundTrips() throws IOException {
        ManifestParser parser = ManifestParserFactory.getParser();
        JsonNode tree = ManifestYamlTestSupport.readYamlTreeFromClasspath("/manifest/example-2.3-polyrepo-no-composition.yaml");

        Manifest m = parser.deserialize(tree);

        assertEquals(2, m.getInstantiation().getRepositories().size());
        assertEquals("infra-repo", m.getInstantiation().getRepositories().get(0).getKey());
        assertEquals("app-repo", m.getInstantiation().getRoot().getRepository());
        assertEquals(3, m.getInstantiation().getRoot().getTargets().size());
        assertEquals("terraform/", m.getInstantiation().getRoot().getTargets().get(0).getSourcePath());
        assertEquals("infra-repo", m.getInstantiation().getRoot().getTargets().get(0).getRepository());
        assertEquals("terraform/", m.getInstantiation().getRoot().getTargets().get(0).getPath());
        assertEquals("policies/", m.getInstantiation().getRoot().getTargets().get(1).getSourcePath());
        assertEquals("infra-repo", m.getInstantiation().getRoot().getTargets().get(1).getRepository());
        assertEquals("governance/policies", m.getInstantiation().getRoot().getTargets().get(1).getPath());
        assertEquals("application/", m.getInstantiation().getRoot().getTargets().get(2).getSourcePath());
        assertEquals("app-repo", m.getInstantiation().getRoot().getTargets().get(2).getRepository());
        assertEquals("./", m.getInstantiation().getRoot().getTargets().get(2).getPath());

        JsonNode serialized = parser.serialize(m);
        ManifestYamlTestSupport.assertSerializedJsonTreeEqualsInitialRead(tree, serialized);

        Manifest again = parser.deserialize(serialized);
        assertThat(again).usingRecursiveComparison().isEqualTo(m);
    }

    @Test
    void givenReadmeExample24PolyrepoCompositionYamlWhenDeserializeAndSerializeThenModulesAndTargetsMatchAndRoundTrips() throws IOException {
        ManifestParser parser = ManifestParserFactory.getParser();
        JsonNode tree = ManifestYamlTestSupport.readYamlTreeFromClasspath("/manifest/example-2.4-polyrepo-composition.yaml");

        Manifest m = parser.deserialize(tree);

        assertEquals(2, m.getComposition().size());
        assertEquals("ingest", m.getComposition().get(0).getModule());
        assertEquals(1, m.getComposition().get(0).getTargets().size());
        assertEquals("pipeline-repo", m.getComposition().get(0).getTargets().get(0).getRepository());
        assertEquals("pipelines/batch", m.getComposition().get(0).getTargets().get(0).getPath());
        JsonNode ingestDomain = m.getComposition().get(0).getParameterMapping().get("domain");
        assertTrue(ingestDomain.isObject());
        assertEquals("dataDomain", ingestDomain.get("$param").asText());
        assertEquals("consume", m.getComposition().get(1).getModule());
        assertEquals("api-repo", m.getComposition().get(1).getTargets().get(0).getRepository());

        assertEquals(2, m.getInstantiation().getRepositories().size());
        assertEquals("pipeline-repo", m.getInstantiation().getRoot().getRepository());
        assertEquals(1, m.getInstantiation().getRoot().getTargets().size());
        assertEquals("./core", m.getInstantiation().getRoot().getTargets().get(0).getSourcePath());
        assertEquals("./core", m.getInstantiation().getRoot().getTargets().get(0).getPath());

        JsonNode serialized = parser.serialize(m);
        ManifestYamlTestSupport.assertSerializedJsonTreeEqualsInitialRead(tree, serialized);

        Manifest again = parser.deserialize(serialized);
        assertThat(again).usingRecursiveComparison().isEqualTo(m);
    }
}
