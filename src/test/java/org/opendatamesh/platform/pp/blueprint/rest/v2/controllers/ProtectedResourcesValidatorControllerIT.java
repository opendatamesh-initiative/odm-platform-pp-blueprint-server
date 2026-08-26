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
import org.opendatamesh.platform.git.model.Tag;
import org.opendatamesh.platform.git.provider.GitProvider;
import org.opendatamesh.platform.pp.blueprint.rest.v2.BlueprintApplicationIT;
import org.opendatamesh.platform.pp.blueprint.rest.v2.RoutesV2;
import org.opendatamesh.platform.pp.blueprint.rest.v2.mocks.GitProviderFactoryMock;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.ErrorRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintRepoOwnerTypeRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintRepoProviderTypeRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.BlueprintVersionRes;
import org.opendatamesh.platform.pp.blueprint.validator.resources.PolicyEvaluationRequestRes;
import org.opendatamesh.platform.pp.blueprint.validator.resources.PolicyEvaluationResultRes;
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
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ProtectedResourcesValidatorControllerIT extends BlueprintApplicationIT {

    private static final String EVALUATE_PATH = "/api/v1/up/validator/evaluate-policy";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final ObjectMapper YAML_OBJECT_MAPPER = new ObjectMapper(new YAMLFactory());
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

    @Test
    void unsupportedStrategyReturnsNotApplicable() throws Exception {
        JsonNode manifest = readYamlManifestResource("manifest/example-2.2-monorepo-composition.yaml");
        BlueprintContext context = createBlueprintAndVersion("composed", "2.1.0", manifest);
        PolicyEvaluationRequestRes request = evaluationRequest(publicationEvent(
                "v2.1.0",
                lineageContent(context.blueprintName, context.versionNumber),
                productRepoNode()
        ));
        ResponseEntity<PolicyEvaluationResultRes> response = evaluate(request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getEvaluationResult()).isTrue();
        assertThat(response.getBody().getOutputObject().getMessage()).contains("monorepo blueprints without composition");
        deleteCreatedBlueprint(context);
    }

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

    private GitOperation stubGit(Path sourceDir, Path productDir) {
        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        GitOperation mockGitOperation = Mockito.mock(GitOperation.class);
        when(mockGitProvider.gitOperation()).thenReturn(mockGitOperation);
        when(mockGitProvider.getRepository(anyString(), anyString())).thenReturn(java.util.Optional.of(new Repository()));
        doAnswer(invocation -> {
            Repository repository = invocation.getArgument(0);
            Consumer<File> consumer = invocation.getArgument(2);
            String cloneUrl = repository.getCloneUrlHttp();
            if (cloneUrl != null && cloneUrl.contains("customer360")) {
                consumer.accept(productDir.toFile());
            } else {
                consumer.accept(sourceDir.toFile());
            }
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

    private void copyProtectedPublishedFiles(Path sourceDir, Path productDir) throws IOException {
        Path core = productDir.resolve("infrastructure/core");
        Files.createDirectories(core);
        Files.copy(sourceDir.resolve("infrastructure/core/network.tf"), core.resolve("network.tf"), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(sourceDir.resolve("infrastructure/core/iam.tf"), core.resolve("iam.tf"), StandardCopyOption.REPLACE_EXISTING);
        Path docs = productDir.resolve("docs");
        Files.createDirectories(docs);
        Files.copy(sourceDir.resolve("docs/architecture.md"), docs.resolve("architecture.md"), StandardCopyOption.REPLACE_EXISTING);
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
