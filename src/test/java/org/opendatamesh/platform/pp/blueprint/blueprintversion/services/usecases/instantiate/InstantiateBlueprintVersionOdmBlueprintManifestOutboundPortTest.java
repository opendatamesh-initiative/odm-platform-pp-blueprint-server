package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import org.opendatamesh.platform.pp.blueprint.manifest.ManifestYamlTestSupport;
import org.opendatamesh.platform.pp.blueprint.manifest.model.Manifest;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Structural instantiate validation via {@code collectValidationIssues} (invalid manifests cannot be published).
 */
class InstantiateBlueprintVersionOdmBlueprintManifestOutboundPortTest {

    private final InstantiateBlueprintVersionOdmBlueprintManifestOutboundPortImpl port =
            new InstantiateBlueprintVersionOdmBlueprintManifestOutboundPortImpl();

    @Test
    void whenResolvingModuleParametersThenParamReferencesAndLiteralValuesBuildChildContexts() throws IOException {
        JsonNode content = ManifestYamlTestSupport.readYamlTreeFromClasspath(
                "/manifest/example-2.2-monorepo-composition.yaml");
        Map<String, JsonNode> parentParameters = Map.of(
                "projectSlug", JsonNodeFactory.instance.textNode("acme-lake"),
                "enablePiiMasking", JsonNodeFactory.instance.booleanNode(true));

        Map<String, Map<String, JsonNode>> resolved =
                port.resolveModuleParameters(content, parentParameters);

        assertThat(resolved.get("storage"))
                .containsOnlyKeys("bucketPrefix", "encryptAtRest", "region");
        assertThat(resolved.get("storage").get("bucketPrefix").asText()).isEqualTo("acme-lake");
        assertThat(resolved.get("storage").get("encryptAtRest").asBoolean()).isTrue();
        assertThat(resolved.get("storage").get("region").asText()).isEqualTo("eu-west-1");
        assertThat(resolved.get("serving"))
                .containsOnlyKeys("serviceName");
        assertThat(resolved.get("serving").get("serviceName").asText()).isEqualTo("acme-lake");
    }

    @Test
    void whenMappedParentParameterHasNoResolvedValueThenResolutionIssueIncludesHint() throws IOException {
        JsonNode content = ManifestYamlTestSupport.readYamlTreeFromClasspath(
                "/manifest/example-2.2-monorepo-composition.yaml");

        List<InstantiationValidationIssue> issues =
                port.collectModuleParameterResolutionIssues(content, Map.of());

        assertThat(issues).hasSize(3).allSatisfy(issue -> {
            assertThat(issue.fieldPath()).contains("parameterMapping").endsWith(".$param");
            assertThat(issue.problem()).containsIgnoringCase("cannot be resolved");
            assertThat(issue.hint()).isNotBlank();
        });
    }

    /*
     * Feature: Structural validation at publish and instantiate
     * Scenario: Empty root.targets is rejected at both gates
     *   Given instantiation.root.targets is []
     *   When instantiate validates equivalent content
     *   Then instantiate also returns 400 with the same rule and a hint
     *   And no Git mutation runs
     */
    @Test
    void whenEmptyRootTargetsThenIssueWithHint() throws IOException {
        List<InstantiationValidationIssue> issues = collect("/manifest/invalid/empty-root-targets.yaml");
        assertThat(issues).anySatisfy(issue -> {
            assertThat(issue.fieldPath()).contains("targets");
            assertThat(issue.problem()).containsIgnoringCase("required");
            assertThat(issue.hint()).isNotBlank();
        });
    }

    /*
     * Feature: Structural validation at publish and instantiate
     * Scenario: Unused repository key is rejected at both gates
     *   Given a key "orphan" with no root or composition target referencing it
     *   When instantiate validates
     *   Then 400 lists the unused key and a hint to add a route or remove the key
     */
    @Test
    void whenUnusedRepositoryKeyThenIssueWithHint() throws IOException {
        List<InstantiationValidationIssue> issues = collect("/manifest/invalid/unused-key.yaml");
        assertThat(issues).anySatisfy(issue -> {
            assertThat(issue.problem()).contains("orphan");
            assertThat(issue.hint()).containsIgnoringCase("unused");
        });
    }

    /*
     * Feature: Structural validation at publish and instantiate
     * Scenario: Nested path-prefix on the same key is rejected at both gates
     *   Given a route with path "./" and another with path "data-plane/storage" on the same key
     *   When instantiate validates
     *   Then 400 explains nested path coverage is forbidden and hints to use sibling destinations
     */
    @Test
    void whenNestedPathPrefixThenIssueWithHint() throws IOException {
        List<InstantiationValidationIssue> issues = collect("/manifest/invalid/nested-destinations.yaml");
        assertThat(issues).anySatisfy(issue -> {
            assertThat(issue.problem()).containsIgnoringCase("Nested");
            assertThat(issue.hint()).isNotBlank();
        });
    }

    /*
     * Feature: Structural validation at publish and instantiate
     * Scenario: Exact overlapping destinations on the same key are rejected at both gates
     *   Given two routes with the same repository key and the same normalized path
     *   When instantiate validates
     *   Then 400 lists the duplicate (repository, path) and a hint to make destinations unique
     */
    @Test
    void whenExactOverlappingDestinationsThenIssueWithHint() throws IOException {
        List<InstantiationValidationIssue> issues = collect("/manifest/invalid/exact-overlap.yaml");
        assertThat(issues).anySatisfy(issue -> {
            assertThat(issue.problem()).containsIgnoringCase("Duplicate");
            assertThat(issue.hint()).isNotBlank();
        });
    }

    /*
     * Feature: Module parameterMapping contract
     * Scenario: Bare scalar mapping entry is rejected at both gates
     *   Given parameterMapping region: eu-west-1
     *   When instantiate validates
     *   Then 400 states the entry must be an object and hints to use { value: eu-west-1 } or { $param: ... }
     */
    @Test
    void whenParameterMappingBareScalarThenIssueWithHint() throws IOException {
        List<InstantiationValidationIssue> issues = collect("/manifest/invalid/bare-parameter-mapping.yaml");
        assertThat(issues).anySatisfy(issue -> {
            assertThat(issue.fieldPath()).contains("parameterMapping");
            assertThat(issue.hint()).contains("$param");
        });
    }

    /*
     * Feature: Structural validation at publish and instantiate
     * Scenario: Multiple structural problems are all reported
     *   Given a manifest with unused key AND nested destinations AND an invalid parameterMapping entry
     *   When instantiate validates
     *   Then the 400 message contains every problem
     *   And each problem includes a how-to-fix hint
     *   And validation does not stop at the first error
     */
    @Test
    void whenMultipleStructuralErrorsThenAllListedWithHints() throws IOException {
        List<InstantiationValidationIssue> issues = collect("/manifest/invalid/multiple-structural-errors.yaml");
        assertThat(issues.size()).isGreaterThanOrEqualTo(3);
        assertThat(issues).allSatisfy(issue -> assertThat(issue.hint()).isNotBlank());
        List<String> problems = issues.stream().map(InstantiationValidationIssue::problem).toList();
        assertThat(problems).anyMatch(p -> p.contains("orphan"));
        assertThat(problems).anyMatch(p -> p.toLowerCase().contains("nested") || p.toLowerCase().contains("path-prefix"));
        assertThat(problems).anyMatch(p -> p.toLowerCase().contains("parametermapping")
                || p.contains("$param")
                || p.toLowerCase().contains("object"));
    }

    private List<InstantiationValidationIssue> collect(String classpathPath) throws IOException {
        JsonNode content = ManifestYamlTestSupport.readYamlTreeFromClasspath(classpathPath);
        return port.collectValidationIssues(
                Manifest.SPEC_NAME,
                "1.0.0",
                content,
                Map.of(),
                List.of());
    }

    /*
     * Feature: Structural validation at publish and instantiate
     * Scenario: Missing instantiation.root.repository is rejected at both gates
     */
    @Test
    void whenMissingRootRepositoryThenIssueWithHint() throws IOException {
        List<InstantiationValidationIssue> issues = collect("/manifest/invalid/missing-root-repository.yaml");
        assertThat(issues).anySatisfy(issue -> {
            assertThat(issue.fieldPath()).contains("targetRepositories");
            assertThat(issue.problem()).containsIgnoringCase("isRoot");
            assertThat(issue.hint()).isNotBlank();
        });
    }

    /*
     * Feature: Structural validation at publish and instantiate
     * Scenario: instantiation.root.repository that is not a declared key is rejected at both gates
     */
    @Test
    void whenUnknownRootRepositoryThenIssueWithHint() throws IOException {
        List<InstantiationValidationIssue> issues = collect("/manifest/invalid/unknown-root-repository.yaml");
        assertThat(issues).anySatisfy(issue -> {
            assertThat(issue.problem()).contains("unknown-repo");
            assertThat(issue.hint()).isNotBlank();
        });
    }

    @Test
    void whenValidPolyrepoManifestThenNoDescriptorRouteValidationIssue() throws IOException {
        JsonNode content = ManifestYamlTestSupport.readYamlTreeFromClasspath(
                "/manifest/example-2.3-polyrepo-no-composition.yaml");
        List<InstantiationValidationIssue> issues = port.collectValidationIssues(
                Manifest.SPEC_NAME,
                "1.0.0",
                content,
                Map.of(),
                List.of());
        assertThat(issues.stream().map(InstantiationValidationIssue::problem))
                .noneMatch(problem -> problem.toLowerCase().contains("descriptor"));
    }

    @Test
    void whenValidManifestThenDesignateRootKeyFromRootRepository() throws IOException {
        JsonNode content = ManifestYamlTestSupport.readYamlTreeFromClasspath(
                "/manifest/example-2.3-polyrepo-no-composition.yaml");
        assertThat(port.retrieveRootTargetRepositoryKey(content)).isEqualTo("app-repo");
    }
}
