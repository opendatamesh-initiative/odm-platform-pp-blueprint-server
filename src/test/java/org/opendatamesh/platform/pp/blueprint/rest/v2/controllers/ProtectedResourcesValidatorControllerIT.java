package org.opendatamesh.platform.pp.blueprint.rest.v2.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.opendatamesh.platform.git.git.GitOperation;
import org.opendatamesh.platform.git.model.Commit;
import org.opendatamesh.platform.git.model.Repository;
import org.opendatamesh.platform.git.model.RepositoryPointer;
import org.opendatamesh.platform.git.model.RepositoryPointerBranch;
import org.opendatamesh.platform.git.model.RepositoryPointerTag;
import org.opendatamesh.platform.git.model.Tag;
import org.opendatamesh.platform.git.provider.GitProvider;
import org.opendatamesh.platform.pp.blueprint.rest.v2.BlueprintApplicationIT;
import org.opendatamesh.platform.pp.blueprint.rest.v2.RoutesV2;
import org.opendatamesh.platform.pp.blueprint.rest.v2.mocks.GitProviderFactoryMock;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.ErrorRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintRepoOwnerTypeRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintRepoProviderTypeRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintTypeRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.BlueprintVersionRes;
import org.opendatamesh.platform.pp.blueprint.old.v1.resources.PolicyEvaluationRequestRes;
import org.opendatamesh.platform.pp.blueprint.old.v1.resources.PolicyEvaluationResultRes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests for protected-resources integrity evaluation.
 * Scenarios trace to {@code spdd/prompt/BDMD-5124-202608210930-[Feat]-service-protected-resources-integrity-policy-adapter.md} (Gherkin).
 */
public class ProtectedResourcesValidatorControllerIT extends BlueprintApplicationIT {

    private static final String EVALUATE_PATH = "/api/v1/up/validator/evaluate-policy";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final ObjectMapper YAML_OBJECT_MAPPER = new ObjectMapper(new YAMLFactory());
    private static final String MODULE_STORAGE_CLONE_URL = "https://github.com/org/module-storage-repository.git";
    private static final String MODULE_SERVING_CLONE_URL = "https://github.com/org/module-serving-repository.git";
    private static final List<String> SOURCE_REPO_RESOURCE_FILES = List.of(
            "instantiate/source-repo/README.md",
            "instantiate/source-repo/manifest.yaml",
            "instantiate/source-repo/plain.txt",
            "instantiate/source-repo/templates/config.txt.vm",
            "instantiate/source-repo/templates/descriptor.json.vm",
            "instantiate/source-repo/templates/pipelines/deploy.yaml.vm",
            "instantiate/source-repo/templates/catalog/table.sql.vm",
            "instantiate/source-repo/infrastructure/core/network.tf",
            "instantiate/source-repo/infrastructure/core/iam.tf",
            "instantiate/source-repo/docs/architecture.md",
            "instantiate/source-repo/scripts/bootstrap.sh"
    );

    @Autowired
    private GitProviderFactoryMock gitProviderFactoryMock;

    @BeforeEach
    @AfterEach
    void resetGitMocks() {
        gitProviderFactoryMock.reset();
    }

    /**
     * Feature: Protected-resources integrity evaluation
     *
     * Scenario: Missing objectToEvaluate returns 400
     *   Given a Policy evaluate request with no objectToEvaluate
     *   When the validator evaluates the request
     *   Then the response status is 400
     */
    @Test
    void missingObjectToEvaluateReturns400() {
        PolicyEvaluationRequestRes request = new PolicyEvaluationRequestRes();
        request.setPolicyEvaluationId(1L);
        ResponseEntity<ErrorRes> response = rest.exchange(
                apiUrlFromString(EVALUATE_PATH),
                HttpMethod.POST,
                new HttpEntity<>(request, jsonHeaders()),
                ErrorRes.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * Feature: Protected-resources integrity evaluation
     *
     * Scenario: Unreadable Policy evaluation object returns 400
     *   Given a Policy evaluate request whose objectToEvaluate is not a JSON object
     *   When the validator evaluates the request
     *   Then the response status is 400
     *   And the error message is "Empty/Malformed Policy Evaluation Object"
     */
    @Test
    void unreadableObjectToEvaluateReturns400() {
        PolicyEvaluationRequestRes request = new PolicyEvaluationRequestRes();
        request.setPolicyEvaluationId(1L);
        request.setObjectToEvaluate(OBJECT_MAPPER.getNodeFactory().textNode("not-an-object"));
        ResponseEntity<ErrorRes> response = rest.exchange(
                apiUrlFromString(EVALUATE_PATH),
                HttpMethod.POST,
                new HttpEntity<>(request, jsonHeaders()),
                ErrorRes.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("Empty/Malformed Policy Evaluation Object");
    }

    /**
     * Feature: Protected-resources integrity evaluation
     *
     * Scenario: No blueprint lineage is not applicable
     *   Given a Policy evaluate request for a data product version
     *   And the version has no blueprint lineage
     *   When the validator evaluates the request
     *   Then the response status is 200
     *   And evaluationResult is true
     *   And the message states the version was not created from a blueprint
     */
    @Test
    void noLineageReturnsNotApplicablePass() {
        PolicyEvaluationRequestRes request = evaluationRequest(publicationEvent(
                "v1.0.0",
                OBJECT_MAPPER.createObjectNode().put("info", "no-blueprint"),
                productRepoNode()
        ));
        ResponseEntity<PolicyEvaluationResultRes> response = evaluate(request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getEvaluationResult()).isTrue();
        assertThat(response.getBody().getOutputObject().getMessage()).contains("not created from a blueprint");
    }

    /**
     * Feature: Protected-resources integrity evaluation
     *
     * Scenario: Empty protectedResources is not applicable
     *   Given a recorded monorepo blueprint version without composition
     *   And the blueprint manifest has an empty protectedResources list
     *   And the data product version has blueprint lineage for that version
     *   When the validator evaluates the request
     *   Then the response status is 200
     *   And evaluationResult is true
     *   And the message states the blueprint does not declare protected resources
     */
    @Test
    void lineageWithEmptyProtectedResourcesReturnsNotApplicable() throws Exception {
        JsonNode manifest = manifestMonorepoNoComposition();
        ((ObjectNode) manifest).set("protectedResources", OBJECT_MAPPER.createArrayNode());
        BlueprintContext context = createBlueprintAndVersion("empty-protected", "1.0.0", manifest);
        PolicyEvaluationRequestRes request = evaluationRequest(publicationEvent(
                "v1.0.0",
                lineageContent(context.blueprintName, context.versionNumber),
                productRepoNode()
        ));
        ResponseEntity<PolicyEvaluationResultRes> response = evaluate(request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getEvaluationResult()).isTrue();
        assertThat(response.getBody().getOutputObject().getMessage()).contains("does not declare protected resources");
        deleteCreatedBlueprint(context);
    }

    /**
     * Feature: Protected-resources integrity evaluation
     *
     * Scenario: Applicable check missing product repository or tag fails closed
     *   Given a recorded monorepo blueprint version with protected resources
     *   And the data product version has blueprint lineage for that version
     *   And the evaluation object has no publication tag and no nested product repository
     *   When the validator evaluates the request
     *   Then the response status is 200
     *   And evaluationResult is false
     *   And the message states the data product version is missing its Git repository or tag
     */
    @Test
    void applicableMissingTagAndRepoFails() throws Exception {
        BlueprintContext context = createBlueprintAndVersion(
                "missing-clone", "1.0.0", manifestMonorepoNoComposition());
        ObjectNode event = OBJECT_MAPPER.createObjectNode();
        ObjectNode eventContent = event.putObject("eventContent");
        ObjectNode version = eventContent.putObject("dataProductVersion");
        version.set("content", lineageContent(context.blueprintName, context.versionNumber));
        PolicyEvaluationRequestRes request = evaluationRequest(event);
        ResponseEntity<PolicyEvaluationResultRes> response = evaluate(request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getEvaluationResult()).isFalse();
        assertThat(response.getBody().getOutputObject().getMessage())
                .contains("missing its Git repository or tag");
        deleteCreatedBlueprint(context);
    }

    /**
     * Feature: Repaired one-destination integrity
     *
     * Scenario: 1→1 matching root shorthand passes
     *   Given a recorded 1→1 Blueprint with root-shorthand protected paths
     *   And the published root tree matches local re-instantiation
     *   When integrity is evaluated
     *   Then evaluationResult is true
     */
    @Test
    void applicableMatchingTreesPass(@TempDir Path sourceDir, @TempDir Path productDir) throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        copyProtectedPublishedFiles(sourceDir, productDir);
        BlueprintContext context = createBlueprintAndVersion(
                "matching-trees", "1.2.0", manifestMonorepoNoComposition());
        GitOperation gitOperation = stubGit(sourceDir, productDir);

        PolicyEvaluationRequestRes request = evaluationRequest(publicationEvent(
                "publication-v1",
                lineageContent(context.blueprintName, context.versionNumber),
                productRepoNode()
        ));
        ResponseEntity<PolicyEvaluationResultRes> response = evaluate(request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getEvaluationResult()).isTrue();
        assertThat(response.getBody().getOutputObject().getMessage())
                .contains("Protected resources match the blueprint");
        verify(gitOperation, never()).pushBranch(any(), anyString());
        verify(gitOperation, never()).pushTag(any(), anyString());
        deleteCreatedBlueprint(context);
    }

    /**
     * Feature: Protected-resources integrity evaluation
     *
     * Scenario: Protected file contents that differ from the blueprint fail
     *   Given a recorded monorepo blueprint version that protects "infrastructure/core/**"
     *   And the published data product version has a modified "infrastructure/core/network.tf"
     *   When the validator evaluates the request
     *   Then the response status is 200
     *   And evaluationResult is false
     *   And the message names the declared path and the file "infrastructure/core/network.tf"
     *   And the message states that file contents differ from the blueprint
     *   And Git pushBranch was never invoked
     *   And Git pushTag was never invoked
     */
    @Test
    void applicableModifiedProtectedFileFailsWithPath(@TempDir Path sourceDir, @TempDir Path productDir) throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        copyProtectedPublishedFiles(sourceDir, productDir);
        Files.writeString(productDir.resolve("infrastructure/core/network.tf"), "tampered published terraform\n");
        BlueprintContext context = createBlueprintAndVersion(
                "modified-tree", "1.2.0", manifestMonorepoNoComposition());
        GitOperation gitOperation = stubGit(sourceDir, productDir);

        PolicyEvaluationRequestRes request = evaluationRequest(publicationEvent(
                "publication-v1",
                lineageContent(context.blueprintName, context.versionNumber),
                productRepoNode()
        ));
        ResponseEntity<PolicyEvaluationResultRes> response = evaluate(request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getEvaluationResult()).isFalse();
        assertThat(response.getBody().getOutputObject().getMessage()).contains("infrastructure/core");
        assertThat(response.getBody().getOutputObject().getMessage()).contains("network.tf");
        assertThat(response.getBody().getOutputObject().getMessage()).contains("contents of file 'infrastructure/core/network.tf'");
        assertThat(response.getBody().getOutputObject().getMessage()).contains("differ from the blueprint");
        verify(gitOperation, never()).pushBranch(any(), anyString());
        verify(gitOperation, never()).pushTag(any(), anyString());
        deleteCreatedBlueprint(context);
    }

    /**
     * Feature: Protected-resources integrity evaluation
     *
     * Scenario: Unknown recorded blueprint version fails
     *   Given a data product version with blueprint lineage for a name and version this service does not store
     *   And the evaluation object has a publication tag and nested product repository
     *   When the validator evaluates the request
     *   Then the response status is 200
     *   And evaluationResult is false
     *   And the message states the blueprint version was not found
     */
    @Test
    void unknownBlueprintVersionFails() {
        PolicyEvaluationRequestRes request = evaluationRequest(publicationEvent(
                "v1.0.0",
                lineageContent("does-not-exist", "9.9.9"),
                productRepoNode()
        ));
        ResponseEntity<PolicyEvaluationResultRes> response = evaluate(request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getEvaluationResult()).isFalse();
        assertThat(response.getBody().getOutputObject().getMessage()).contains("was not found");
    }

    /**
     * Feature: Protected-resources integrity evaluation
     *
     * Scenario: Git clone failure fails closed
     *   Given a recorded monorepo blueprint version with protected resources
     *   And the data product version has blueprint lineage for that version
     *   And cloning the published data product version fails
     *   When the validator evaluates the request
     *   Then the response status is 200
     *   And evaluationResult is false
     *   And the message is a non-blank infrastructure failure
     *   And the message does not contain Git tokens
     */
    @Test
    void gitCloneFailureFailsClosed() throws Exception {
        BlueprintContext context = createBlueprintAndVersion(
                "clone-fail", "1.0.0", manifestMonorepoNoComposition());
        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        GitOperation mockGitOperation = Mockito.mock(GitOperation.class);
        when(mockGitProvider.gitOperation()).thenReturn(mockGitOperation);
        when(mockGitProvider.getRepository(anyString(), anyString()))
                .thenReturn(java.util.Optional.of(new Repository()));
        doThrow(new RuntimeException("clone failed")).when(mockGitOperation).readRepository(any(), any(), any());

        PolicyEvaluationRequestRes request = evaluationRequest(publicationEvent(
                "v1.0.0",
                lineageContent(context.blueprintName, context.versionNumber),
                productRepoNode()
        ));
        ResponseEntity<PolicyEvaluationResultRes> response = evaluate(request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getEvaluationResult()).isFalse();
        assertThat(response.getBody().getOutputObject().getMessage()).isNotBlank();
        assertThat(response.getBody().getOutputObject().getMessage()).doesNotContain("test-token");
        deleteCreatedBlueprint(context);
    }

    /**
     * Feature: Protected-resources integrity evaluation
     *
     * Scenario: Protected resource missing from the data product version fails
     *   Given a recorded monorepo blueprint version that protects "infrastructure/core/**"
     *   And the published data product version is missing "infrastructure/core/network.tf"
     *   When the validator evaluates the request
     *   Then the response status is 200
     *   And evaluationResult is false
     *   And the message states the protected resource is missing from the data product version
     */
    @Test
    void protectedFileMissingFromDataProductVersionFails(
            @TempDir Path sourceDir, @TempDir Path productDir) throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        copyProtectedPublishedFiles(sourceDir, productDir);
        Files.delete(productDir.resolve("infrastructure/core/network.tf"));
        BlueprintContext context = createBlueprintAndVersion(
                "missing-published", "1.2.0", manifestMonorepoNoComposition());
        stubGit(sourceDir, productDir);

        PolicyEvaluationRequestRes request = evaluationRequest(publicationEvent(
                "publication-v1",
                lineageContent(context.blueprintName, context.versionNumber),
                productRepoNode()
        ));
        ResponseEntity<PolicyEvaluationResultRes> response = evaluate(request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getEvaluationResult()).isFalse();
        assertThat(response.getBody().getOutputObject().getMessage()).contains("infrastructure/core");
        assertThat(response.getBody().getOutputObject().getMessage()).contains("network.tf");
        assertThat(response.getBody().getOutputObject().getMessage()).contains("missing");
        assertThat(response.getBody().getOutputObject().getMessage()).contains("data product version");
        deleteCreatedBlueprint(context);
    }

    /**
     * Feature: Protected-resources integrity evaluation
     *
     * Scenario: Protected resource not produced by the blueprint fails
     *   Given a recorded monorepo blueprint version that protects "infrastructure/core/**"
     *   And the published data product version contains an extra file under "infrastructure/core/" that the blueprint does not produce
     *   When the validator evaluates the request
     *   Then the response status is 200
     *   And evaluationResult is false
     *   And the message states the file is not produced by the blueprint
     */
    @Test
    void extraProtectedFileNotProducedByBlueprintFails(
            @TempDir Path sourceDir, @TempDir Path productDir) throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        copyProtectedPublishedFiles(sourceDir, productDir);
        Files.writeString(productDir.resolve("infrastructure/core/extra.tf"), "not-from-blueprint\n");
        BlueprintContext context = createBlueprintAndVersion(
                "extra-published", "1.2.0", manifestMonorepoNoComposition());
        stubGit(sourceDir, productDir);

        PolicyEvaluationRequestRes request = evaluationRequest(publicationEvent(
                "publication-v1",
                lineageContent(context.blueprintName, context.versionNumber),
                productRepoNode()
        ));
        ResponseEntity<PolicyEvaluationResultRes> response = evaluate(request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getEvaluationResult()).isFalse();
        assertThat(response.getBody().getOutputObject().getMessage()).contains("infrastructure/core/extra.tf");
        assertThat(response.getBody().getOutputObject().getMessage()).contains("not produced by the blueprint");
        deleteCreatedBlueprint(context);
    }

    /**
     * Feature: Protected-resources integrity evaluation
     *
     * Scenario: Protected path missing from both the data product version and the blueprint fails
     *   Given a recorded monorepo blueprint version that protects a path present in neither tree
     *   When the validator evaluates the request
     *   Then the response status is 200
     *   And evaluationResult is false
     *   And the message states the path is missing from the data product version
     *   And the message states the path is not produced by the blueprint
     */
    @Test
    void protectedPathMissingFromBothTreesFails(
            @TempDir Path sourceDir, @TempDir Path productDir) throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        copyProtectedPublishedFiles(sourceDir, productDir);
        JsonNode manifest = manifestMonorepoNoComposition();
        ((ObjectNode) manifest).set(
                "protectedResources",
                OBJECT_MAPPER.createArrayNode().add(OBJECT_MAPPER.createObjectNode().put("path", "missing/protected.txt")));
        BlueprintContext context = createBlueprintAndVersion("missing-both", "1.2.0", manifest);
        stubGit(sourceDir, productDir);

        PolicyEvaluationRequestRes request = evaluationRequest(publicationEvent(
                "publication-v1",
                lineageContent(context.blueprintName, context.versionNumber),
                productRepoNode()
        ));
        ResponseEntity<PolicyEvaluationResultRes> response = evaluate(request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getEvaluationResult()).isFalse();
        assertThat(response.getBody().getOutputObject().getMessage()).contains("missing/protected.txt");
        assertThat(response.getBody().getOutputObject().getMessage()).contains("missing from the data product version");
        assertThat(response.getBody().getOutputObject().getMessage()).contains("not produced by the blueprint");
        deleteCreatedBlueprint(context);
    }

    /**
     * Feature: Protected-resources integrity evaluation
     *
     * Scenario: N→1 with protected composition destinations is evaluated
     *   Given a recorded parent blueprint that composes a published 1→1 module into one destination key
     *   And the parent `protectedResources` list a post-instantiation path under the module destination
     *   And the published product tree matches a local re-instantiation including that destination and `.odm/<alias>/` when protected
     *   When the validator evaluates the request
     *   Then evaluationResult is true
     *   And the message does not state that checks apply only to monorepo without composition
     */
    @Test
    void whenMonorepoWithCompositionProtectedPathsMatchThenPass(
            @TempDir Path parentSource, @TempDir Path moduleSource, @TempDir Path productDir) throws Exception {
        writeSourceBlueprintFiles(parentSource);
        writeSafeDescriptor(parentSource);
        writeSourceBlueprintFiles(moduleSource);
        writeSafeDescriptor(moduleSource);
        Files.writeString(moduleSource.resolve("module-only.txt"), "from-module\n");
        copyN1ProtectedPublishedFiles(moduleSource, productDir);

        BlueprintContext storage = createPublishedModule("odm-blueprint-s3-lake", "3.0.1", MODULE_STORAGE_CLONE_URL);
        BlueprintContext serving = createPublishedModule("odm-blueprint-api-skeleton", "1.4.0", MODULE_SERVING_CLONE_URL);
        ObjectNode parentManifest = (ObjectNode) readYamlManifestResource("manifest/example-2.2-monorepo-composition.yaml");
        rewriteCompositionRefs(parentManifest, storage, serving);
        parentManifest.set("protectedResources", n1ProtectedResources());
        BlueprintContext parent = createBlueprintAndVersion("full-stack-dp", "2.1.0", parentManifest);
        GitOperation gitOperation = stubGit(parentSource, moduleSource, productDir);

        PolicyEvaluationRequestRes request = evaluationRequest(publicationEvent(
                "publication-v1",
                composedLineageContent(parent.blueprintName, parent.versionNumber),
                productRepoNode()
        ));
        ResponseEntity<PolicyEvaluationResultRes> response = evaluate(request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getEvaluationResult()).isTrue();
        assertThat(response.getBody().getOutputObject().getMessage())
                .contains("Protected resources match the blueprint");
        assertThat(response.getBody().getOutputObject().getMessage())
                .doesNotContain("without composition");
        verify(gitOperation, never()).readRepository(any(), any(RepositoryPointerBranch.class), any());
        verify(gitOperation, never()).pushBranch(any(), anyString());
        verify(gitOperation, never()).pushTag(any(), anyString());
        deleteCreatedBlueprint(parent);
        deleteCreatedBlueprint(storage);
        deleteCreatedBlueprint(serving);
    }

    /**
     * Feature: Protected-resources integrity evaluation
     *
     * Scenario: N→1 mismatch on a module destination path fails
     *   Given the same composed parent
     *   And the published product tree is missing a file under the protected module destination
     *   When the validator evaluates the request
     *   Then evaluationResult is false
     *   And the message names that path as missing from the data product version
     */
    @Test
    void whenMonorepoWithCompositionProtectedPathMissingThenFail(
            @TempDir Path parentSource, @TempDir Path moduleSource, @TempDir Path productDir) throws Exception {
        writeSourceBlueprintFiles(parentSource);
        writeSafeDescriptor(parentSource);
        writeSourceBlueprintFiles(moduleSource);
        writeSafeDescriptor(moduleSource);
        Files.writeString(moduleSource.resolve("module-only.txt"), "from-module\n");
        copyN1ProtectedPublishedFiles(moduleSource, productDir);
        Files.deleteIfExists(productDir.resolve("data-plane/storage/module-only.txt"));

        BlueprintContext storage = createPublishedModule("odm-blueprint-s3-lake", "3.0.1", MODULE_STORAGE_CLONE_URL);
        BlueprintContext serving = createPublishedModule("odm-blueprint-api-skeleton", "1.4.0", MODULE_SERVING_CLONE_URL);
        ObjectNode parentManifest = (ObjectNode) readYamlManifestResource("manifest/example-2.2-monorepo-composition.yaml");
        rewriteCompositionRefs(parentManifest, storage, serving);
        parentManifest.set("protectedResources", n1ProtectedResources());
        BlueprintContext parent = createBlueprintAndVersion("full-stack-dp", "2.1.0", parentManifest);
        stubGit(parentSource, moduleSource, productDir);

        PolicyEvaluationRequestRes request = evaluationRequest(publicationEvent(
                "publication-v1",
                composedLineageContent(parent.blueprintName, parent.versionNumber),
                productRepoNode()
        ));
        ResponseEntity<PolicyEvaluationResultRes> response = evaluate(request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getEvaluationResult()).isFalse();
        assertThat(response.getBody().getOutputObject().getMessage()).contains("data-plane/storage/module-only.txt");
        assertThat(response.getBody().getOutputObject().getMessage()).contains("missing");
        assertThat(response.getBody().getOutputObject().getMessage()).contains("data product version");
        deleteCreatedBlueprint(parent);
        deleteCreatedBlueprint(storage);
        deleteCreatedBlueprint(serving);
    }

    /**
     * Feature: Protected-resources integrity evaluation
     *
     * Scenario: Empty protectedResources is not applicable even with composition
     *   Given a recorded N→1 parent whose `protectedResources` list is empty
     *   When the validator evaluates the request
     *   Then evaluationResult is true
     *   And the message states the blueprint does not declare protected resources
     */
    @Test
    void whenComposedParentWithEmptyProtectedResourcesThenNotApplicable() throws Exception {
        BlueprintContext storage = createPublishedModule("odm-blueprint-s3-lake", "3.0.1", MODULE_STORAGE_CLONE_URL);
        BlueprintContext serving = createPublishedModule("odm-blueprint-api-skeleton", "1.4.0", MODULE_SERVING_CLONE_URL);
        ObjectNode parentManifest = (ObjectNode) readYamlManifestResource("manifest/example-2.2-monorepo-composition.yaml");
        rewriteCompositionRefs(parentManifest, storage, serving);
        parentManifest.set("protectedResources", OBJECT_MAPPER.createArrayNode());
        BlueprintContext parent = createBlueprintAndVersion("full-stack-dp", "2.1.0", parentManifest);
        PolicyEvaluationRequestRes request = evaluationRequest(publicationEvent(
                "v2.1.0",
                composedLineageContent(parent.blueprintName, parent.versionNumber),
                productRepoNode()
        ));
        ResponseEntity<PolicyEvaluationResultRes> response = evaluate(request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getEvaluationResult()).isTrue();
        assertThat(response.getBody().getOutputObject().getMessage()).contains("does not declare protected resources");
        deleteCreatedBlueprint(parent);
        deleteCreatedBlueprint(storage);
        deleteCreatedBlueprint(serving);
    }

    /**
     * Feature: Protected-resources integrity evaluation
     *
     * Scenario: Unknown repository key at evaluate fails closed
     *   Given a recorded 1→1 blueprint whose stored protected resource names an undeclared `repository` key
     *   When the validator evaluates the request
     *   Then evaluationResult is false
     *   And the message names the unknown key
     */
    @Test
    void whenStoredUnknownProtectedRepositoryKeyThenFailClosed() throws Exception {
        JsonNode manifest = manifestMonorepoNoComposition();
        ((ObjectNode) manifest.get("protectedResources").get(0)).put("repository", "not-a-declared-key");
        BlueprintContext context = createBlueprintAndVersion("unknown-prot-repo", "1.0.0", manifest);
        PolicyEvaluationRequestRes request = evaluationRequest(publicationEvent(
                "v1.0.0",
                lineageContent(context.blueprintName, context.versionNumber),
                productRepoNode()
        ));
        ResponseEntity<PolicyEvaluationResultRes> response = evaluate(request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getEvaluationResult()).isFalse();
        assertThat(response.getBody().getOutputObject().getMessage()).contains("not-a-declared-key");
        deleteCreatedBlueprint(context);
    }

    /**
     * Feature: Protected-resources integrity evaluation
     *
     * Scenario: Additional remotes on a monorepo product do not fail the check
     *   Given a recorded 1→1 blueprint with protected resources
     *   And the evaluation object’s nested product has a non-empty `additionalDataProductRepos` array
     *   When the validator evaluates the request
     *   Then the check still clones only the root product repository
     *   And extras alone do not make evaluationResult false
     */
    @Test
    void whenMonorepoProductHasAdditionalReposThenStillEvaluatesRoot(
            @TempDir Path sourceDir, @TempDir Path productDir) throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        copyProtectedPublishedFiles(sourceDir, productDir);
        BlueprintContext context = createBlueprintAndVersion(
                "matching-trees-extras", "1.2.0", manifestMonorepoNoComposition());
        GitOperation gitOperation = stubGit(sourceDir, productDir);

        ObjectNode event = publicationEvent(
                "publication-v1",
                lineageContent(context.blueprintName, context.versionNumber),
                productRepoNode()
        );
        ObjectNode extra = OBJECT_MAPPER.createObjectNode();
        extra.put("repositoryKey", "infra-repo");
        extra.put("remoteUrlHttp", "https://github.com/org/extra-remote.git");
        ((ObjectNode) event.path("eventContent").path("dataProductVersion").path("dataProduct"))
                .set("additionalDataProductRepos", OBJECT_MAPPER.createArrayNode().add(extra));

        PolicyEvaluationRequestRes request = evaluationRequest(event);
        ResponseEntity<PolicyEvaluationResultRes> response = evaluate(request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getEvaluationResult()).isTrue();
        verify(gitOperation, never()).readRepository(
                argThat(repo -> repo != null && "https://github.com/org/extra-remote.git".equals(repo.getCloneUrlHttp())),
                any(),
                any());
        deleteCreatedBlueprint(context);
    }

    /**
     * Feature: Multi-destination integrity
     *
     * Scenario: 1→N protects only root and ignores unrelated metadata gaps
     *   Given a 1→N Blueprint protects only the root target
     *   And an unprotected additional target lacks locator or ref metadata
     *   When integrity is evaluated
     *   Then only the root published repository is cloned
     *   And the unrelated gap does not fail the policy
     */
    @Test
    void whenPolyrepoProtectsOnlyRootThenCloneOnlyRoot(
            @TempDir Path sourceDir, @TempDir Path rootProduct) throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        writeSafeDescriptor(sourceDir);
        copyRootProtectedPublishedFiles(sourceDir, rootProduct);
        ObjectNode manifest = polyrepoNoCompositionManifestForIntegrity();
        manifest.set("protectedResources", OBJECT_MAPPER.createArrayNode()
                .add(OBJECT_MAPPER.createObjectNode().put("path", "docs/architecture.md")));
        BlueprintContext context = createBlueprintAndVersion("poly-root-only", "1.0.0", manifest);
        GitOperation gitOperation = stubPublishedGit(sourceDir, sourceDir, Map.of("customer360", rootProduct));

        ObjectNode event = publicationEvent(
                "publication-v1",
                polyrepoLineageContent(context.blueprintName, context.versionNumber),
                productRepoNode());
        ObjectNode incomplete = OBJECT_MAPPER.createObjectNode();
        incomplete.put("repositoryKey", "infra-repo");
        ((ObjectNode) event.path("eventContent").path("dataProductVersion").path("dataProduct"))
                .set("additionalDataProductRepos", OBJECT_MAPPER.createArrayNode().add(incomplete));

        ResponseEntity<PolicyEvaluationResultRes> response = evaluate(evaluationRequest(event));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getEvaluationResult()).isTrue();
        verify(gitOperation, never()).readRepository(
                argThat(repo -> repo != null && repo.getCloneUrlHttp() != null && repo.getCloneUrlHttp().contains("infra-repo")),
                any(),
                any());
        deleteCreatedBlueprint(context);
    }

    /**
     * Feature: Multi-destination integrity
     *
     * Scenario: 1→N protects only an additional target
     *   Given a 1→N Blueprint protects only "infra-repo"
     *   And root publication metadata is absent
     *   And "infra-repo" has one locator and its own ref
     *   When integrity is evaluated
     *   Then only "infra-repo" is cloned and compared
     *   And root metadata absence does not fail the policy
     */
    @Test
    void whenPolyrepoProtectsOnlyAdditionalTargetThenRootMetadataNotRequired(
            @TempDir Path sourceDir, @TempDir Path infraProduct) throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        writeSafeDescriptor(sourceDir);
        copyInfraProtectedPublishedFiles(sourceDir, infraProduct);
        ObjectNode manifest = polyrepoNoCompositionManifestForIntegrity();
        manifest.set("protectedResources", OBJECT_MAPPER.createArrayNode()
                .add(OBJECT_MAPPER.createObjectNode()
                        .put("path", "infrastructure/core/**")
                        .put("repository", "infra-repo")));
        BlueprintContext context = createBlueprintAndVersion("poly-infra-only", "1.0.0", manifest);
        GitOperation gitOperation = stubPublishedGit(sourceDir, sourceDir, Map.of("infra-repo", infraProduct));

        ObjectNode event = OBJECT_MAPPER.createObjectNode();
        ObjectNode version = event.putObject("eventContent").putObject("dataProductVersion");
        version.set("content", polyrepoLineageContent(context.blueprintName, context.versionNumber));
        ObjectNode dataProduct = version.putObject("dataProduct");
        dataProduct.set("additionalDataProductRepos", OBJECT_MAPPER.createArrayNode().add(additionalRepoNode("infra-repo")));
        version.set("additionalTags", OBJECT_MAPPER.createArrayNode().add(additionalTagNode("infra-repo", "infra-v9")));

        ResponseEntity<PolicyEvaluationResultRes> response = evaluate(evaluationRequest(event));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getEvaluationResult()).isTrue();
        verify(gitOperation, never()).readRepository(
                argThat(repo -> repo != null && repo.getCloneUrlHttp() != null && repo.getCloneUrlHttp().contains("customer360")),
                any(),
                any());
        deleteCreatedBlueprint(context);
    }

    /**
     * Feature: Multi-destination integrity
     *
     * Scenario: Different root and additional refs are honored
     *   Given root and "infra-repo" are both protected
     *   And each has a different recorded ref
     *   When integrity is evaluated
     *   Then each repository is cloned at its own ref
     */
    @Test
    void whenMultipleTargetsProtectedThenCloneEachRecordedRef(
            @TempDir Path sourceDir, @TempDir Path rootProduct, @TempDir Path infraProduct) throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        writeSafeDescriptor(sourceDir);
        copyRootProtectedPublishedFiles(sourceDir, rootProduct);
        copyInfraProtectedPublishedFiles(sourceDir, infraProduct);
        ObjectNode manifest = polyrepoNoCompositionManifestForIntegrity();
        manifest.set("protectedResources", OBJECT_MAPPER.createArrayNode()
                .add(OBJECT_MAPPER.createObjectNode().put("path", "docs/architecture.md"))
                .add(OBJECT_MAPPER.createObjectNode()
                        .put("path", "infrastructure/core/**")
                        .put("repository", "infra-repo")));
        BlueprintContext context = createBlueprintAndVersion("poly-both", "1.0.0", manifest);
        GitOperation gitOperation = stubPublishedGit(
                sourceDir, sourceDir, Map.of("customer360", rootProduct, "infra-repo", infraProduct));

        ObjectNode event = publicationEvent(
                "root-v3",
                polyrepoLineageContent(context.blueprintName, context.versionNumber),
                productRepoNode());
        attachAdditionalPublication(event, "infra-repo", "infra-v9");

        ResponseEntity<PolicyEvaluationResultRes> response = evaluate(evaluationRequest(event));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getEvaluationResult()).isTrue();

        org.mockito.ArgumentCaptor<Repository> repoCaptor = org.mockito.ArgumentCaptor.forClass(Repository.class);
        org.mockito.ArgumentCaptor<RepositoryPointer> pointerCaptor =
                org.mockito.ArgumentCaptor.forClass(RepositoryPointer.class);
        verify(gitOperation, atLeastOnce()).readRepository(repoCaptor.capture(), pointerCaptor.capture(), any());
        List<Repository> repos = repoCaptor.getAllValues();
        List<RepositoryPointer> pointers = pointerCaptor.getAllValues();
        assertThat(productRef(repos, pointers, "customer360")).isEqualTo("root-v3");
        assertThat(productRef(repos, pointers, "infra-repo")).isEqualTo("infra-v9");
        deleteCreatedBlueprint(context);
    }

    /**
     * Feature: Multi-destination integrity
     *
     * Scenario: N→N compares parent and Module output by destination
     *   Given a parent Blueprint and Modules route protected output across root and additional targets
     *   And every published target matches its same-key expected tree
     *   When integrity is evaluated
     *   Then evaluationResult is true
     *   And no target is compared against another target tree
     */
    @Test
    void whenPolyrepoWithCompositionMatchesThenPass(
            @TempDir Path parentSource,
            @TempDir Path moduleSource,
            @TempDir Path pipelineProduct,
            @TempDir Path apiProduct) throws Exception {
        writeSourceBlueprintFiles(parentSource);
        writeSafeDescriptor(parentSource);
        writeSourceBlueprintFiles(moduleSource);
        writeSafeDescriptor(moduleSource);
        Files.writeString(moduleSource.resolve("module-only.txt"), "from-module\n");
        copyNnProtectedPublishedFiles(moduleSource, pipelineProduct, apiProduct);

        BlueprintContext ingest = createPublishedModule("odm-blueprint-ingest-batch", "2.0.0", MODULE_STORAGE_CLONE_URL);
        BlueprintContext consume = createPublishedModule("odm-blueprint-consumer-api", "1.1.0", MODULE_SERVING_CLONE_URL);
        ObjectNode parentManifest = (ObjectNode) readYamlManifestResource("manifest/example-2.4-polyrepo-composition.yaml");
        rewritePolyrepoCompositionRefs(parentManifest, ingest, consume);
        parentManifest.set("protectedResources", OBJECT_MAPPER.createArrayNode()
                .add(OBJECT_MAPPER.createObjectNode().put("path", "pipelines/batch/module-only.txt"))
                .add(OBJECT_MAPPER.createObjectNode()
                        .put("path", "services/consumer/module-only.txt")
                        .put("repository", "api-repo")));
        BlueprintContext parent = createBlueprintAndVersion("mesh-polyrepo-parent", "1.3.0", parentManifest);
        stubPublishedGit(parentSource, moduleSource, Map.of("customer360", pipelineProduct, "api-repo", apiProduct));

        ObjectNode event = publicationEvent(
                "publication-v1",
                polyrepoComposedLineageContent(parent.blueprintName, parent.versionNumber),
                productRepoNode());
        attachAdditionalPublication(event, "api-repo", "api-v4");

        ResponseEntity<PolicyEvaluationResultRes> response = evaluate(evaluationRequest(event));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getEvaluationResult()).isTrue();
        assertThat(response.getBody().getOutputObject().getMessage())
                .contains("Protected resources match the blueprint");
        deleteCreatedBlueprint(parent);
        deleteCreatedBlueprint(ingest);
        deleteCreatedBlueprint(consume);
    }

    /**
     * Feature: Update checkpoint isolation
     *
     * Scenario: Reused unchanged checkpoint does not bypass publication integrity
     *   Given a Blueprint update reuses an unchanged pure-render checkpoint
     *   And the product snapshot being published contains tampering under a protected path
     *   When protected-resources integrity is evaluated
     *   Then evaluationResult is false
     *   And the comparison uses the published product ref rather than treating checkpoint reuse as approval
     */
    @Test
    void whenUnchangedCheckpointIsReusedThenTamperedPublicationStillFailsIntegrity(
            @TempDir Path sourceDir, @TempDir Path productDir) throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        copyProtectedPublishedFiles(sourceDir, productDir);
        Files.writeString(productDir.resolve("infrastructure/core/network.tf"), "tampered after checkpoint reuse\n");
        BlueprintContext context = createBlueprintAndVersion(
                "checkpoint-isolation", "1.2.0", manifestMonorepoNoComposition());
        GitOperation gitOperation = stubGit(sourceDir, productDir);

        PolicyEvaluationRequestRes request = evaluationRequest(publicationEvent(
                "publication-v1",
                lineageContent(context.blueprintName, context.versionNumber),
                productRepoNode()));
        ResponseEntity<PolicyEvaluationResultRes> response = evaluate(request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getEvaluationResult()).isFalse();
        assertThat(response.getBody().getOutputObject().getMessage()).contains("network.tf");

        org.mockito.ArgumentCaptor<RepositoryPointer> pointerCaptor =
                org.mockito.ArgumentCaptor.forClass(RepositoryPointer.class);
        verify(gitOperation, atLeastOnce()).readRepository(any(), pointerCaptor.capture(), any());
        assertThat(pointerCaptor.getAllValues())
                .anyMatch(pointer -> pointer instanceof RepositoryPointerTag
                        && "publication-v1".equals(pointer.getRefValue()));
        assertThat(pointerCaptor.getAllValues())
                .noneMatch(pointer -> pointer.getRefValue() != null && pointer.getRefValue().startsWith("blueprint-v"));
        deleteCreatedBlueprint(context);
    }

    private GitOperation stubGit(Path sourceDir, Path productDir) {
        return stubGit(sourceDir, sourceDir, productDir);
    }

    private GitOperation stubGit(Path parentSource, Path moduleSource, Path productDir) {
        return stubPublishedGit(parentSource, moduleSource, Map.of("customer360", productDir));
    }

    private GitOperation stubPublishedGit(Path parentSource, Path moduleSource, Map<String, Path> productTreesByUrlFragment) {
        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        GitOperation mockGitOperation = Mockito.mock(GitOperation.class);
        when(mockGitProvider.gitOperation()).thenReturn(mockGitOperation);
        when(mockGitProvider.getRepository(anyString(), anyString())).thenReturn(java.util.Optional.of(new Repository()));
        doAnswer(invocation -> {
            Repository repository = invocation.getArgument(0);
            Consumer<File> consumer = invocation.getArgument(2);
            String cloneUrl = repository.getCloneUrlHttp();
            if (cloneUrl != null) {
                for (Map.Entry<String, Path> productTree : productTreesByUrlFragment.entrySet()) {
                    if (cloneUrl.contains(productTree.getKey())) {
                        consumer.accept(productTree.getValue().toFile());
                        return null;
                    }
                }
                if (cloneUrl.contains("module-")) {
                    consumer.accept(moduleSource.toFile());
                    return null;
                }
            }
            consumer.accept(parentSource.toFile());
            return null;
        }).when(mockGitOperation).readRepository(any(), any(), any());
        doNothing().when(mockGitOperation).createAndCheckoutOrphanBranch(any(), anyString());
        doNothing().when(mockGitOperation).addAll(any());
        doNothing().when(mockGitOperation).commit(any(), any(Commit.class));
        when(mockGitOperation.getHeadSha(any(), anyString())).thenReturn("deadbeefcafebabe");
        doNothing().when(mockGitOperation).addTag(any(), any(Tag.class));
        when(mockGitOperation.mergeBranch(any(), anyString(), anyString())).thenReturn("deadbeefcafebabe");
        doNothing().when(mockGitOperation).pushBranch(any(), anyString());
        doNothing().when(mockGitOperation).pushTag(any(), anyString());
        return mockGitOperation;
    }

    private ResponseEntity<PolicyEvaluationResultRes> evaluate(PolicyEvaluationRequestRes request) {
        return rest.exchange(
                apiUrlFromString(EVALUATE_PATH),
                HttpMethod.POST,
                new HttpEntity<>(request, jsonHeaders()),
                PolicyEvaluationResultRes.class
        );
    }

    private PolicyEvaluationRequestRes evaluationRequest(JsonNode objectToEvaluate) {
        PolicyEvaluationRequestRes request = new PolicyEvaluationRequestRes();
        request.setPolicyEvaluationId(42L);
        request.setObjectToEvaluate(objectToEvaluate);
        return request;
    }

    private ObjectNode publicationEvent(String tag, JsonNode content, JsonNode productRepo) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        ObjectNode eventContent = root.putObject("eventContent");
        ObjectNode version = eventContent.putObject("dataProductVersion");
        version.put("tag", tag);
        version.set("content", content);
        ObjectNode dataProduct = version.putObject("dataProduct");
        dataProduct.set("dataProductRepo", productRepo);
        return root;
    }

    private ObjectNode productRepoNode() {
        ObjectNode repo = OBJECT_MAPPER.createObjectNode();
        repo.put("remoteUrlHttp", "https://github.com/org/customer360.git");
        repo.put("providerType", "GITHUB");
        repo.put("providerBaseUrl", "https://github.com");
        repo.put("name", "customer360");
        repo.put("defaultBranch", "main");
        repo.put("ownerId", "org");
        repo.put("externalIdentifier", "target-repository-id");
        return repo;
    }

    private ObjectNode additionalRepoNode(String repositoryKey) {
        ObjectNode repo = OBJECT_MAPPER.createObjectNode();
        repo.put("repositoryKey", repositoryKey);
        repo.put("remoteUrlHttp", "https://github.com/org/" + repositoryKey + ".git");
        repo.put("providerType", "GITHUB");
        repo.put("providerBaseUrl", "https://github.com");
        repo.put("name", repositoryKey);
        repo.put("defaultBranch", "main");
        repo.put("ownerId", "org");
        repo.put("externalIdentifier", repositoryKey + "-id");
        return repo;
    }

    private ObjectNode additionalTagNode(String repositoryKey, String tag) {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.put("repositoryKey", repositoryKey);
        node.put("tag", tag);
        return node;
    }

    private void attachAdditionalPublication(ObjectNode event, String repositoryKey, String tag) {
        ObjectNode version = (ObjectNode) event.path("eventContent").path("dataProductVersion");
        ObjectNode dataProduct = (ObjectNode) version.path("dataProduct");
        dataProduct.set("additionalDataProductRepos", OBJECT_MAPPER.createArrayNode().add(additionalRepoNode(repositoryKey)));
        version.set("additionalTags", OBJECT_MAPPER.createArrayNode().add(additionalTagNode(repositoryKey, tag)));
    }

    private String productRef(List<Repository> repos, List<RepositoryPointer> pointers, String urlFragment) {
        for (int i = 0; i < repos.size(); i++) {
            Repository repository = repos.get(i);
            if (repository != null
                    && repository.getCloneUrlHttp() != null
                    && repository.getCloneUrlHttp().contains(urlFragment)) {
                return pointers.get(i).getRefValue();
            }
        }
        return null;
    }

    private ObjectNode lineageContent(String blueprintName, String versionNumber) {
        ObjectNode content = OBJECT_MAPPER.createObjectNode();
        ObjectNode blueprint = content.putObject("blueprint");
        blueprint.put("blueprintName", blueprintName);
        blueprint.put("blueprintVersionNumber", versionNumber);
        ObjectNode parameters = blueprint.putObject("parameters");
        parameters.put("environment", "prod");
        parameters.put("retentionDays", 365);
        return content;
    }

    private ObjectNode composedLineageContent(String blueprintName, String versionNumber) {
        ObjectNode content = OBJECT_MAPPER.createObjectNode();
        ObjectNode blueprint = content.putObject("blueprint");
        blueprint.put("blueprintName", blueprintName);
        blueprint.put("blueprintVersionNumber", versionNumber);
        ObjectNode parameters = blueprint.putObject("parameters");
        parameters.put("projectSlug", "acme-lake");
        parameters.put("enablePiiMasking", true);
        return content;
    }

    private ObjectNode polyrepoLineageContent(String blueprintName, String versionNumber) {
        ObjectNode content = OBJECT_MAPPER.createObjectNode();
        ObjectNode blueprint = content.putObject("blueprint");
        blueprint.put("blueprintName", blueprintName);
        blueprint.put("blueprintVersionNumber", versionNumber);
        ObjectNode parameters = blueprint.putObject("parameters");
        parameters.put("awsRegion", "eu-west-1");
        return content;
    }

    private ObjectNode polyrepoComposedLineageContent(String blueprintName, String versionNumber) {
        ObjectNode content = OBJECT_MAPPER.createObjectNode();
        ObjectNode blueprint = content.putObject("blueprint");
        blueprint.put("blueprintName", blueprintName);
        blueprint.put("blueprintVersionNumber", versionNumber);
        ObjectNode parameters = blueprint.putObject("parameters");
        parameters.put("dataDomain", "sales");
        return content;
    }

    private ObjectNode polyrepoNoCompositionManifestForIntegrity() throws Exception {
        ObjectNode manifest = (ObjectNode) readYamlManifestResource("manifest/example-2.3-polyrepo-no-composition.yaml");
        ObjectNode instantiation = OBJECT_MAPPER.createObjectNode();
        instantiation.put("type", "root");
        instantiation.set("targets", OBJECT_MAPPER.createArrayNode()
                .add(route("infrastructure/", "infra-repo", "infrastructure/"))
                .add(route("docs/", "app-repo", "docs/"))
                .add(route("templates/", "app-repo", "templates/")));
        manifest.set("instantiation", OBJECT_MAPPER.createArrayNode().add(instantiation));
        return manifest;
    }

    private ObjectNode route(String sourcePath, String repo, String destinationPath) {
        ObjectNode target = OBJECT_MAPPER.createObjectNode();
        target.put("sourcePath", sourcePath);
        target.put("repo", repo);
        target.put("destinationPath", destinationPath);
        return target;
    }

    private JsonNode n1ProtectedResources() {
        return OBJECT_MAPPER.createArrayNode()
                .add(OBJECT_MAPPER.createObjectNode().put("path", "data-plane/storage/module-only.txt"))
                .add(OBJECT_MAPPER.createObjectNode().put("path", ".odm/storage/**"));
    }

    private void copyN1ProtectedPublishedFiles(Path moduleSource, Path productDir) throws IOException {
        Path destination = productDir.resolve("data-plane/storage");
        Files.createDirectories(destination);
        Files.copy(
                moduleSource.resolve("module-only.txt"),
                destination.resolve("module-only.txt"),
                StandardCopyOption.REPLACE_EXISTING);
        Path sidecar = productDir.resolve(".odm/storage");
        Files.createDirectories(sidecar);
        Files.copy(moduleSource.resolve("README.md"), sidecar.resolve("README.md"), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(moduleSource.resolve("manifest.yaml"), sidecar.resolve("manifest.yaml"), StandardCopyOption.REPLACE_EXISTING);
    }

    private void rewriteCompositionRefs(ObjectNode parentManifest, BlueprintContext storage, BlueprintContext serving) {
        rewriteCompositionModuleRefs(parentManifest, Map.of("storage", storage, "serving", serving));
    }

    private void rewritePolyrepoCompositionRefs(ObjectNode parentManifest, BlueprintContext ingest, BlueprintContext consume) {
        rewriteCompositionModuleRefs(parentManifest, Map.of("ingest", ingest, "consume", consume));
    }

    private void rewriteCompositionModuleRefs(ObjectNode parentManifest, Map<String, BlueprintContext> modules) {
        for (JsonNode node : parentManifest.get("composition")) {
            ObjectNode composition = (ObjectNode) node;
            BlueprintContext module = modules.get(composition.get("module").asText());
            if (module != null) {
                composition.put("blueprintName", module.blueprintName);
                composition.put("blueprintVersion", module.versionNumber);
            }
        }
    }

    private BlueprintContext createPublishedModule(String blueprintName, String version, String cloneUrl)
            throws Exception {
        JsonNode moduleManifest = manifestMonorepoNoComposition();
        ((ObjectNode) moduleManifest).set("protectedResources", OBJECT_MAPPER.createArrayNode());
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String uniqueBlueprintName = blueprintName + "-" + suffix;
        ObjectNode content = (ObjectNode) moduleManifest.deepCopy();
        content.put("name", uniqueBlueprintName);
        content.put("version", version);
        String prefix = "integrity-mod-" + version.replace(".", "-") + "-" + suffix;
        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName(uniqueBlueprintName);
        blueprint.setDisplayName(prefix + "-display");
        blueprint.setDescription(prefix + "-description");
        blueprint.setBlueprintType(BlueprintTypeRes.MODULE);
        blueprint.setBlueprintRepo(buildModuleBlueprintRepo(cloneUrl));

        ResponseEntity<BlueprintRes> createdBlueprint = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                BlueprintRes.class
        );
        assertThat(createdBlueprint.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createdBlueprint.getBody()).isNotNull();

        BlueprintVersionRes versionRes = new BlueprintVersionRes();
        versionRes.setName(prefix + "-version");
        versionRes.setDescription(prefix + "-description");
        versionRes.setReadme("README.md");
        versionRes.setTag("v" + version);
        versionRes.setVersionNumber(version);
        versionRes.setSpec("odm-blueprint-manifest");
        versionRes.setSpecVersion("1.0.0");
        versionRes.setBlueprint(createdBlueprint.getBody());
        versionRes.setContent(content);

        ResponseEntity<BlueprintVersionRes> createdVersion = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS),
                new HttpEntity<>(versionRes),
                BlueprintVersionRes.class
        );
        assertThat(createdVersion.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return new BlueprintContext(createdBlueprint.getBody().getUuid(), uniqueBlueprintName, version);
    }

    private BlueprintRes.BlueprintRepoRes buildModuleBlueprintRepo(String cloneUrl) {
        BlueprintRes.BlueprintRepoRes blueprintRepo = new BlueprintRes.BlueprintRepoRes();
        blueprintRepo.setExternalIdentifier("module-blueprint-repository");
        blueprintRepo.setName("module-blueprint-repository");
        blueprintRepo.setDescription("module");
        blueprintRepo.setManifestRootPath("/manifest.yaml");
        blueprintRepo.setDescriptorTemplatePath(null);
        blueprintRepo.setReadmePath("/README.md");
        blueprintRepo.setRemoteUrlHttp(cloneUrl);
        blueprintRepo.setRemoteUrlSsh("git@github.com:org/module-blueprint-repository.git");
        blueprintRepo.setDefaultBranch("main");
        blueprintRepo.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
        blueprintRepo.setProviderBaseUrl("https://github.com");
        blueprintRepo.setOwnerId("org");
        blueprintRepo.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
        return blueprintRepo;
    }

    private void copyProtectedPublishedFiles(Path sourceDir, Path productDir) throws IOException {
        copyInfraProtectedPublishedFiles(sourceDir, productDir);
        copyRootProtectedPublishedFiles(sourceDir, productDir);
    }

    private void copyRootProtectedPublishedFiles(Path sourceDir, Path productDir) throws IOException {
        Path docs = productDir.resolve("docs");
        Files.createDirectories(docs);
        Files.copy(sourceDir.resolve("docs/architecture.md"), docs.resolve("architecture.md"), StandardCopyOption.REPLACE_EXISTING);
    }

    private void copyInfraProtectedPublishedFiles(Path sourceDir, Path productDir) throws IOException {
        Path core = productDir.resolve("infrastructure/core");
        Files.createDirectories(core);
        Files.copy(sourceDir.resolve("infrastructure/core/network.tf"), core.resolve("network.tf"), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(sourceDir.resolve("infrastructure/core/iam.tf"), core.resolve("iam.tf"), StandardCopyOption.REPLACE_EXISTING);
    }

    private void copyNnProtectedPublishedFiles(Path moduleSource, Path pipelineProduct, Path apiProduct) throws IOException {
        Path pipelineDestination = pipelineProduct.resolve("pipelines/batch");
        Files.createDirectories(pipelineDestination);
        Files.copy(
                moduleSource.resolve("module-only.txt"),
                pipelineDestination.resolve("module-only.txt"),
                StandardCopyOption.REPLACE_EXISTING);
        Path apiDestination = apiProduct.resolve("services/consumer");
        Files.createDirectories(apiDestination);
        Files.copy(
                moduleSource.resolve("module-only.txt"),
                apiDestination.resolve("module-only.txt"),
                StandardCopyOption.REPLACE_EXISTING);
    }

    private void writeSourceBlueprintFiles(Path sourceDir) throws IOException {
        for (String resourcePath : SOURCE_REPO_RESOURCE_FILES) {
            String relativePath = resourcePath.replaceFirst("^instantiate/source-repo/", "");
            Path destination = sourceDir.resolve(relativePath);
            if (destination.getParent() != null) {
                Files.createDirectories(destination.getParent());
            }
            try (InputStream inputStream = getResourceAsStream(resourcePath)) {
                Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /**
     * Parent N→1 parameters do not include {@code environment}/{@code retentionDays}.
     * The fixture descriptor template interpolates those keys as JSON; leave them
     * and lineage enrichment fails to parse {@code templates/descriptor.json}.
     */
    private void writeSafeDescriptor(Path sourceDir) throws IOException {
        Path descriptor = sourceDir.resolve("templates/descriptor.json.vm");
        Files.createDirectories(descriptor.getParent());
        Files.writeString(descriptor, """
                {
                  "dataProductDescriptor": "1.0.0",
                  "info": {
                    "name": "composed-product"
                  }
                }
                """);
    }

    private BlueprintContext createBlueprintAndVersion(String blueprintName, String version, JsonNode manifestContent)
            throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String uniqueBlueprintName = blueprintName + "-" + suffix;
        ObjectNode content = (ObjectNode) manifestContent.deepCopy();
        content.put("name", uniqueBlueprintName);
        content.put("version", version);
        String prefix = "integrity-" + version.replace(".", "-") + "-" + suffix;
        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName(uniqueBlueprintName);
        blueprint.setDisplayName(prefix + "-display");
        blueprint.setDescription(prefix + "-description");
        blueprint.setBlueprintType(BlueprintTypeRes.BLUEPRINT);
        blueprint.setBlueprintRepo(buildBlueprintRepo());

        ResponseEntity<BlueprintRes> createdBlueprint = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                BlueprintRes.class
        );
        assertThat(createdBlueprint.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createdBlueprint.getBody()).isNotNull();

        BlueprintVersionRes versionRes = new BlueprintVersionRes();
        versionRes.setName(prefix + "-version");
        versionRes.setDescription(prefix + "-description");
        versionRes.setReadme("README.md");
        versionRes.setTag("v" + version);
        versionRes.setVersionNumber(version);
        versionRes.setSpec("odm-blueprint-manifest");
        versionRes.setSpecVersion("1.0.0");
        versionRes.setBlueprint(createdBlueprint.getBody());
        versionRes.setContent(content);

        ResponseEntity<BlueprintVersionRes> createdVersion = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS),
                new HttpEntity<>(versionRes),
                BlueprintVersionRes.class
        );
        assertThat(createdVersion.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return new BlueprintContext(createdBlueprint.getBody().getUuid(), uniqueBlueprintName, version);
    }

    private BlueprintRes.BlueprintRepoRes buildBlueprintRepo() {
        BlueprintRes.BlueprintRepoRes blueprintRepo = new BlueprintRes.BlueprintRepoRes();
        blueprintRepo.setExternalIdentifier("source-blueprint-repository");
        blueprintRepo.setName("source-blueprint-repository");
        blueprintRepo.setDescription("source");
        blueprintRepo.setManifestRootPath("/manifest.yaml");
        blueprintRepo.setDescriptorTemplatePath("templates/descriptor.json.vm");
        blueprintRepo.setReadmePath("/README.md");
        blueprintRepo.setRemoteUrlHttp("https://github.com/org/source-blueprint-repository.git");
        blueprintRepo.setRemoteUrlSsh("git@github.com:org/source-blueprint-repository.git");
        blueprintRepo.setDefaultBranch("main");
        blueprintRepo.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
        blueprintRepo.setProviderBaseUrl("https://github.com");
        blueprintRepo.setOwnerId("org");
        blueprintRepo.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
        return blueprintRepo;
    }

    private JsonNode manifestMonorepoNoComposition() throws Exception {
        return readYamlManifestResource("manifest/example-2.1-monorepo-no-composition.yaml");
    }

    private JsonNode readYamlManifestResource(String resourcePath) throws IOException {
        try (InputStream inputStream = getResourceAsStream(resourcePath)) {
            return YAML_OBJECT_MAPPER.readTree(inputStream);
        }
    }

    private InputStream getResourceAsStream(String resourcePath) {
        InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath);
        if (inputStream == null) {
            throw new IllegalStateException("Test resource not found: " + resourcePath);
        }
        return inputStream;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private void deleteCreatedBlueprint(BlueprintContext context) {
        if (context != null && context.blueprintUuid != null) {
            rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + context.blueprintUuid));
        }
    }

    private static final class BlueprintContext {
        private final String blueprintUuid;
        private final String blueprintName;
        private final String versionNumber;

        private BlueprintContext(String blueprintUuid, String blueprintName, String versionNumber) {
            this.blueprintUuid = blueprintUuid;
            this.blueprintName = blueprintName;
            this.versionNumber = versionNumber;
        }
    }
}
