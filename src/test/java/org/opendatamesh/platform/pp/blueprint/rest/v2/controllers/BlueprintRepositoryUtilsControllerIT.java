package org.opendatamesh.platform.pp.blueprint.rest.v2.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.opendatamesh.platform.git.exceptions.GitOperationException;
import org.opendatamesh.platform.git.git.GitOperation;
import org.opendatamesh.platform.git.model.Repository;
import org.opendatamesh.platform.git.provider.GitProvider;
import org.opendatamesh.platform.pp.blueprint.rest.v2.BlueprintApplicationIT;
import org.opendatamesh.platform.pp.blueprint.rest.v2.RoutesV2;
import org.opendatamesh.platform.pp.blueprint.rest.v2.mocks.GitProviderFactoryMock;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintRepoOwnerTypeRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintRepoProviderTypeRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.repository.InitRepositoryCommandRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.repository.RepositoryContentFileRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.repository.RepositoryContentReadRes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * Integration tests for {@link BlueprintRepositoryUtilsController}.
 */
public class BlueprintRepositoryUtilsControllerIT extends BlueprintApplicationIT {

    private static final String TEST_PAT_TOKEN = "test-pat-token";
    private static final String TEST_PAT_USERNAME = "test-user";

    /**
     * Realistic ODM-style manifest (YAML).
     */
    private static final String EXPECTED_MANIFEST_YAML = """
            apiVersion: blueprint.odm/v1
            kind: BlueprintManifest
            metadata:
              name: sample-blueprint
            spec:
              description: Integration test manifest
            """;

    /**
     * Valid JSON with nested object (pretty-printed as sent in the request body).
     */
    private static final String EXPECTED_CONFIG_JSON = """
            {
              "schemaVersion": 1,
              "features": {
                "registry": true
              }
            }
            """;

    /**
     * Markdown readme.
     */
    private static final String EXPECTED_README_MD = """
            # Sample Blueprint
            
            This repository was initialized by an integration test.
            
            - Item one
            - Item two
            """;

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    @Autowired
    private GitProviderFactoryMock gitProviderFactoryMock;

    @AfterEach
    void tearDown() {
        gitProviderFactoryMock.reset();
    }

    /**
     * Feature: Initialize blueprint repository content
     * Given a blueprint with a linked repository exists
     * And the Git provider mock clones into a writable directory
     * When the client POSTs valid init content to "/api/v2/pp/blueprint/blueprints/{uuid}/repository-content"
     * Then the response status is 200
     * And files on disk match the request payloads byte-for-byte
     * And YAML and JSON payloads parse and contain the expected structure
     */
    @Test
    void whenInitRepositoryContentWithValidPayloadThenReturnsOk(@TempDir Path tempDir) throws Exception {
        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName("whenInitRepositoryContentWithValidPayloadThenReturnsOk-bp");
        blueprint.setDisplayName("whenInitRepositoryContentWithValidPayloadThenReturnsOk-display");
        blueprint.setDescription("whenInitRepositoryContentWithValidPayloadThenReturnsOk-description");
        BlueprintRes.BlueprintRepoRes blueprintRepo = new BlueprintRes.BlueprintRepoRes();
        blueprintRepo.setExternalIdentifier("ext-id");
        blueprintRepo.setName("repo-name");
        blueprintRepo.setDescription("repo-desc");
        blueprintRepo.setManifestRootPath("/manifest");
        blueprintRepo.setDescriptorTemplatePath("/template");
        blueprintRepo.setReadmePath("/readme");
        blueprintRepo.setRemoteUrlHttp("https://github.com/org/repo.git");
        blueprintRepo.setRemoteUrlSsh("git@github.com:org/repo.git");
        blueprintRepo.setDefaultBranch("main");
        blueprintRepo.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
        blueprintRepo.setProviderBaseUrl("https://github.com");
        blueprintRepo.setOwnerId("org");
        blueprintRepo.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
        blueprint.setBlueprintRepo(blueprintRepo);
        ResponseEntity<BlueprintRes> createResponse = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                BlueprintRes.class
        );
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody()).isNotNull();
        String blueprintUuid = createResponse.getBody().getUuid();

        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        GitOperation mockGitOperation = Mockito.mock(GitOperation.class);
        when(mockGitProvider.gitOperation()).thenReturn(mockGitOperation);
        when(mockGitProvider.getRepository(eq("ext-id"), eq("org"))).thenReturn(Optional.of(new Repository()));

        doAnswer(invocation -> {
            Consumer<File> consumer = invocation.getArgument(2);
            consumer.accept(tempDir.toFile());
            return null;
        }).when(mockGitOperation).readRepository(any(), any(), any());

        doNothing().when(mockGitOperation).addFiles(any(), any());
        doNothing().when(mockGitOperation).commit(any(), any());
        doNothing().when(mockGitOperation).push(any(), anyBoolean());

        InitRepositoryCommandRes body = new InitRepositoryCommandRes();
        body.setAuthorName("Test Author");
        body.setAuthorEmail("author@example.com");

        InitRepositoryCommandRes.RepositoryResource yamlResource = new InitRepositoryCommandRes.RepositoryResource();
        yamlResource.setFilePath("manifest/blueprint.yaml");
        yamlResource.setFileContent(EXPECTED_MANIFEST_YAML);
        body.getResources().add(yamlResource);

        InitRepositoryCommandRes.RepositoryResource jsonResource = new InitRepositoryCommandRes.RepositoryResource();
        jsonResource.setFilePath("config/features.json");
        jsonResource.setFileContent(EXPECTED_CONFIG_JSON);
        body.getResources().add(jsonResource);

        InitRepositoryCommandRes.RepositoryResource mdResource = new InitRepositoryCommandRes.RepositoryResource();
        mdResource.setFilePath("README.md");
        mdResource.setFileContent(EXPECTED_README_MD);
        body.getResources().add(mdResource);

        ResponseEntity<Void> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid + "/repository-content"),
                HttpMethod.POST,
                new HttpEntity<>(body, createJsonGitProviderHeaders()),
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Path manifestPath = tempDir.resolve("manifest/blueprint.yaml");
        Path jsonPath = tempDir.resolve("config/features.json");
        Path readmePath = tempDir.resolve("README.md");

        assertThat(Files.readString(manifestPath, StandardCharsets.UTF_8)).isEqualTo(EXPECTED_MANIFEST_YAML);
        assertThat(Files.readString(jsonPath, StandardCharsets.UTF_8)).isEqualTo(EXPECTED_CONFIG_JSON);
        assertThat(Files.readString(readmePath, StandardCharsets.UTF_8)).isEqualTo(EXPECTED_README_MD);

        JsonNode yamlTree = YAML.readTree(Files.newBufferedReader(manifestPath, StandardCharsets.UTF_8));
        assertThat(yamlTree.path("apiVersion").asText()).isEqualTo("blueprint.odm/v1");
        assertThat(yamlTree.path("kind").asText()).isEqualTo("BlueprintManifest");
        assertThat(yamlTree.path("metadata").path("name").asText()).isEqualTo("sample-blueprint");

        JsonNode jsonTree = JSON.readTree(Files.newBufferedReader(jsonPath, StandardCharsets.UTF_8));
        assertThat(jsonTree.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(jsonTree.path("features").path("registry").asBoolean()).isTrue();

        rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
    }

    /**
     * Feature: Initialize blueprint repository content — validation
     * Given the init command has no resources
     * When the client POSTs to "/api/v2/pp/blueprint/blueprints/{uuid}/repository-content"
     * Then the response status is 400
     */
    @Test
    void whenInitRepositoryContentWithEmptyResourcesThenReturnsBadRequest() {
        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName("whenInitRepositoryContentWithEmptyResourcesThenReturnsBadRequest-bp");
        blueprint.setDisplayName("whenInitRepositoryContentWithEmptyResourcesThenReturnsBadRequest-display");
        blueprint.setDescription("whenInitRepositoryContentWithEmptyResourcesThenReturnsBadRequest-description");
        BlueprintRes.BlueprintRepoRes blueprintRepo = new BlueprintRes.BlueprintRepoRes();
        blueprintRepo.setExternalIdentifier("ext-id");
        blueprintRepo.setName("repo-name");
        blueprintRepo.setDescription("repo-desc");
        blueprintRepo.setManifestRootPath("/manifest");
        blueprintRepo.setDescriptorTemplatePath("/template");
        blueprintRepo.setReadmePath("/readme");
        blueprintRepo.setRemoteUrlHttp("https://github.com/org/repo.git");
        blueprintRepo.setRemoteUrlSsh("git@github.com:org/repo.git");
        blueprintRepo.setDefaultBranch("main");
        blueprintRepo.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
        blueprintRepo.setProviderBaseUrl("https://github.com");
        blueprintRepo.setOwnerId("org");
        blueprintRepo.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
        blueprint.setBlueprintRepo(blueprintRepo);
        ResponseEntity<BlueprintRes> createResponse = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                BlueprintRes.class
        );
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody()).isNotNull();
        String blueprintUuid = createResponse.getBody().getUuid();

        InitRepositoryCommandRes body = new InitRepositoryCommandRes();
        body.setAuthorName("A");
        body.setAuthorEmail("a@b.c");
        body.setResources(Collections.emptyList());

        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid + "/repository-content"),
                HttpMethod.POST,
                new HttpEntity<>(body, createJsonGitProviderHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
    }

    /**
     * Feature: Initialize blueprint repository content — blueprint not found
     * Given no blueprint exists for the given UUID
     * When the client POSTs a valid init command
     * Then the response status is 404
     */
    @Test
    void whenInitRepositoryContentForUnknownBlueprintThenReturnsNotFound() {
        InitRepositoryCommandRes body = new InitRepositoryCommandRes();
        body.setAuthorName("Test Author");
        body.setAuthorEmail("author@example.com");
        InitRepositoryCommandRes.RepositoryResource resource = new InitRepositoryCommandRes.RepositoryResource();
        resource.setFilePath("a.yaml");
        resource.setFileContent("k: v\n");
        body.getResources().add(resource);

        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINTS, "/00000000-0000-0000-0000-000000000001/repository-content"),
                HttpMethod.POST,
                new HttpEntity<>(body, createJsonGitProviderHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * Feature: Initialize blueprint repository content — remote missing
     * Given a blueprint with a linked repository exists
     * And the Git provider has no matching remote repository
     * When the client POSTs a valid init command
     * Then the response status is 400
     */
    @Test
    void whenRemoteRepositoryNotFoundThenReturnsBadRequest() {
        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName("whenRemoteRepositoryNotFoundThenReturnsBadRequest-bp");
        blueprint.setDisplayName("whenRemoteRepositoryNotFoundThenReturnsBadRequest-display");
        blueprint.setDescription("whenRemoteRepositoryNotFoundThenReturnsBadRequest-description");
        BlueprintRes.BlueprintRepoRes blueprintRepo = new BlueprintRes.BlueprintRepoRes();
        blueprintRepo.setExternalIdentifier("ext-id");
        blueprintRepo.setName("repo-name");
        blueprintRepo.setDescription("repo-desc");
        blueprintRepo.setManifestRootPath("/manifest");
        blueprintRepo.setDescriptorTemplatePath("/template");
        blueprintRepo.setReadmePath("/readme");
        blueprintRepo.setRemoteUrlHttp("https://github.com/org/repo.git");
        blueprintRepo.setRemoteUrlSsh("git@github.com:org/repo.git");
        blueprintRepo.setDefaultBranch("main");
        blueprintRepo.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
        blueprintRepo.setProviderBaseUrl("https://github.com");
        blueprintRepo.setOwnerId("org");
        blueprintRepo.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
        blueprint.setBlueprintRepo(blueprintRepo);
        ResponseEntity<BlueprintRes> createResponse = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                BlueprintRes.class
        );
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody()).isNotNull();
        String blueprintUuid = createResponse.getBody().getUuid();

        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        when(mockGitProvider.getRepository(eq("ext-id"), eq("org"))).thenReturn(Optional.empty());

        InitRepositoryCommandRes body = new InitRepositoryCommandRes();
        body.setAuthorName("Test Author");
        body.setAuthorEmail("author@example.com");
        InitRepositoryCommandRes.RepositoryResource resource = new InitRepositoryCommandRes.RepositoryResource();
        resource.setFilePath("x.yaml");
        resource.setFileContent("{}");
        body.getResources().add(resource);

        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid + "/repository-content"),
                HttpMethod.POST,
                new HttpEntity<>(body, createJsonGitProviderHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
    }

    /**
     * Feature: Initialize blueprint repository content — Git read failure
     * Given a blueprint with a linked repository exists
     * And the Git layer fails while reading the repository (e.g. branch missing)
     * When the client POSTs a valid init command
     * Then the response status is 400
     */
    @Test
    void whenGitReadRepositoryFailsThenReturnsBadRequest() {
        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName("whenGitReadRepositoryFailsThenReturnsBadRequest-bp");
        blueprint.setDisplayName("whenGitReadRepositoryFailsThenReturnsBadRequest-display");
        blueprint.setDescription("whenGitReadRepositoryFailsThenReturnsBadRequest-description");
        BlueprintRes.BlueprintRepoRes blueprintRepo = new BlueprintRes.BlueprintRepoRes();
        blueprintRepo.setExternalIdentifier("ext-id");
        blueprintRepo.setName("repo-name");
        blueprintRepo.setDescription("repo-desc");
        blueprintRepo.setManifestRootPath("/manifest");
        blueprintRepo.setDescriptorTemplatePath("/template");
        blueprintRepo.setReadmePath("/readme");
        blueprintRepo.setRemoteUrlHttp("https://github.com/org/repo.git");
        blueprintRepo.setRemoteUrlSsh("git@github.com:org/repo.git");
        blueprintRepo.setDefaultBranch("main");
        blueprintRepo.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
        blueprintRepo.setProviderBaseUrl("https://github.com");
        blueprintRepo.setOwnerId("org");
        blueprintRepo.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
        blueprint.setBlueprintRepo(blueprintRepo);
        ResponseEntity<BlueprintRes> createResponse = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                BlueprintRes.class
        );
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody()).isNotNull();
        String blueprintUuid = createResponse.getBody().getUuid();

        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        GitOperation mockGitOperation = Mockito.mock(GitOperation.class);
        when(mockGitProvider.gitOperation()).thenReturn(mockGitOperation);
        when(mockGitProvider.getRepository(eq("ext-id"), eq("org"))).thenReturn(Optional.of(new Repository()));

        doThrow(new GitOperationException("branch not found")).when(mockGitOperation).readRepository(any(), any(), any());

        InitRepositoryCommandRes body = new InitRepositoryCommandRes();
        body.setAuthorName("Test Author");
        body.setAuthorEmail("author@example.com");
        InitRepositoryCommandRes.RepositoryResource resource = new InitRepositoryCommandRes.RepositoryResource();
        resource.setFilePath("x.yaml");
        resource.setFileContent("{}");
        body.getResources().add(resource);

        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid + "/repository-content"),
                HttpMethod.POST,
                new HttpEntity<>(body, createJsonGitProviderHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
    }


    /**
     * REPO-READ-001 — Successful read with explicit paths and branch pointer
     */
    @Test
    void whenReadRepositoryContentWithBranchAndPathsThenReturnsOk(@TempDir Path tempDir) throws Exception {
        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName("whenReadRepositoryContentWithBranchAndPathsThenReturnsOk-bp");
        blueprint.setDisplayName("whenReadRepositoryContentWithBranchAndPathsThenReturnsOk-display");
        blueprint.setDescription("whenReadRepositoryContentWithBranchAndPathsThenReturnsOk-description");
        BlueprintRes.BlueprintRepoRes blueprintRepo = new BlueprintRes.BlueprintRepoRes();
        blueprintRepo.setExternalIdentifier("ext-id");
        blueprintRepo.setName("repo-name");
        blueprintRepo.setDescription("repo-desc");
        blueprintRepo.setManifestRootPath("/manifest");
        blueprintRepo.setDescriptorTemplatePath("/template");
        blueprintRepo.setReadmePath("/readme");
        blueprintRepo.setRemoteUrlHttp("https://github.com/org/repo.git");
        blueprintRepo.setRemoteUrlSsh("git@github.com:org/repo.git");
        blueprintRepo.setDefaultBranch("main");
        blueprintRepo.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
        blueprintRepo.setProviderBaseUrl("https://github.com");
        blueprintRepo.setOwnerId("org");
        blueprintRepo.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
        blueprint.setBlueprintRepo(blueprintRepo);
        ResponseEntity<BlueprintRes> createResponse = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                BlueprintRes.class
        );
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody()).isNotNull();
        String blueprintUuid = createResponse.getBody().getUuid();
        stubGitCloneToTempDir(tempDir);

        Path dir = tempDir.resolve("dir");
        Files.createDirectories(dir);
        String yamlContent = "key: value\n";
        Files.writeString(dir.resolve("file.yaml"), yamlContent, StandardCharsets.UTF_8);

        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        GitOperation mockGitOperation = mockGitProvider.gitOperation();

        String url = buildGetRepositoryContentUrl(blueprintUuid, "main", null, null, List.of("dir/file.yaml"));
        ResponseEntity<RepositoryContentReadRes> response = rest.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(createJsonGitProviderHeaders()),
                new ParameterizedTypeReference<RepositoryContentReadRes>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getResources()).hasSize(1);
        assertThat(response.getBody().getResources().get(0).getFilePath()).isEqualTo("dir/file.yaml");
        assertThat(response.getBody().getResources().get(0).getFileContent()).isEqualTo(yamlContent);

        verify(mockGitOperation, never()).addFiles(any(), any());
        verify(mockGitOperation, never()).commit(any(), any());
        verify(mockGitOperation, never()).push(any(), anyBoolean());

        rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
    }

    /**
     * REPO-READ-002 — Successful read with tag pointer
     */
    @Test
    void whenReadRepositoryContentWithTagThenReturnsOk(@TempDir Path tempDir) throws Exception {
        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName("whenReadRepositoryContentWithTagThenReturnsOk-bp");
        blueprint.setDisplayName("whenReadRepositoryContentWithTagThenReturnsOk-display");
        blueprint.setDescription("whenReadRepositoryContentWithTagThenReturnsOk-description");
        BlueprintRes.BlueprintRepoRes blueprintRepo = new BlueprintRes.BlueprintRepoRes();
        blueprintRepo.setExternalIdentifier("ext-id");
        blueprintRepo.setName("repo-name");
        blueprintRepo.setDescription("repo-desc");
        blueprintRepo.setManifestRootPath("/manifest");
        blueprintRepo.setDescriptorTemplatePath("/template");
        blueprintRepo.setReadmePath("/readme");
        blueprintRepo.setRemoteUrlHttp("https://github.com/org/repo.git");
        blueprintRepo.setRemoteUrlSsh("git@github.com:org/repo.git");
        blueprintRepo.setDefaultBranch("main");
        blueprintRepo.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
        blueprintRepo.setProviderBaseUrl("https://github.com");
        blueprintRepo.setOwnerId("org");
        blueprintRepo.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
        blueprint.setBlueprintRepo(blueprintRepo);
        ResponseEntity<BlueprintRes> createResponse = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                BlueprintRes.class
        );
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody()).isNotNull();
        String blueprintUuid = createResponse.getBody().getUuid();
        stubGitCloneToTempDir(tempDir);

        Files.writeString(tempDir.resolve("a.txt"), "tag-content", StandardCharsets.UTF_8);

        String url = buildGetRepositoryContentUrl(blueprintUuid, null, "v1.0.0", null, List.of("a.txt"));
        ResponseEntity<RepositoryContentReadRes> response = rest.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(createJsonGitProviderHeaders()),
                new ParameterizedTypeReference<RepositoryContentReadRes>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getResources()).hasSize(1);
        assertThat(response.getBody().getResources().get(0).getFileContent()).isEqualTo("tag-content");

        rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
    }

    /**
     * REPO-READ-003 — Successful read with commit hash pointer
     */
    @Test
    void whenReadRepositoryContentWithCommitThenReturnsOk(@TempDir Path tempDir) throws Exception {
        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName("whenReadRepositoryContentWithCommitThenReturnsOk-bp");
        blueprint.setDisplayName("whenReadRepositoryContentWithCommitThenReturnsOk-display");
        blueprint.setDescription("whenReadRepositoryContentWithCommitThenReturnsOk-description");
        BlueprintRes.BlueprintRepoRes blueprintRepo = new BlueprintRes.BlueprintRepoRes();
        blueprintRepo.setExternalIdentifier("ext-id");
        blueprintRepo.setName("repo-name");
        blueprintRepo.setDescription("repo-desc");
        blueprintRepo.setManifestRootPath("/manifest");
        blueprintRepo.setDescriptorTemplatePath("/template");
        blueprintRepo.setReadmePath("/readme");
        blueprintRepo.setRemoteUrlHttp("https://github.com/org/repo.git");
        blueprintRepo.setRemoteUrlSsh("git@github.com:org/repo.git");
        blueprintRepo.setDefaultBranch("main");
        blueprintRepo.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
        blueprintRepo.setProviderBaseUrl("https://github.com");
        blueprintRepo.setOwnerId("org");
        blueprintRepo.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
        blueprint.setBlueprintRepo(blueprintRepo);
        ResponseEntity<BlueprintRes> createResponse = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                BlueprintRes.class
        );
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody()).isNotNull();
        String blueprintUuid = createResponse.getBody().getUuid();
        stubGitCloneToTempDir(tempDir);

        Files.writeString(tempDir.resolve("b.txt"), "commit-content", StandardCharsets.UTF_8);

        String url = buildGetRepositoryContentUrl(blueprintUuid, null, null, "abc1234deadbeef", List.of("b.txt"));
        ResponseEntity<RepositoryContentReadRes> response = rest.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(createJsonGitProviderHeaders()),
                new ParameterizedTypeReference<RepositoryContentReadRes>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getResources()).hasSize(1);
        assertThat(response.getBody().getResources().get(0).getFileContent()).isEqualTo("commit-content");

        rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
    }

    /**
     * REPO-READ-004 — Default paths when no path query parameters
     */
    @Test
    void whenReadRepositoryContentWithoutPathParamsThenUsesDefaultTriple(@TempDir Path tempDir) throws Exception {
        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName("whenReadRepositoryContentWithoutPathParamsThenUsesDefaultTriple-bp");
        blueprint.setDisplayName("whenReadRepositoryContentWithoutPathParamsThenUsesDefaultTriple-display");
        blueprint.setDescription("whenReadRepositoryContentWithoutPathParamsThenUsesDefaultTriple-description");
        BlueprintRes.BlueprintRepoRes blueprintRepo = new BlueprintRes.BlueprintRepoRes();
        blueprintRepo.setExternalIdentifier("ext-id");
        blueprintRepo.setName("repo-name");
        blueprintRepo.setDescription("repo-desc");
        blueprintRepo.setManifestRootPath("manifest/blueprint.yaml");
        blueprintRepo.setDescriptorTemplatePath("templates/descriptor.json");
        blueprintRepo.setReadmePath("README.md");
        blueprintRepo.setRemoteUrlHttp("https://github.com/org/repo.git");
        blueprintRepo.setRemoteUrlSsh("git@github.com:org/repo.git");
        blueprintRepo.setDefaultBranch("main");
        blueprintRepo.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
        blueprintRepo.setProviderBaseUrl("https://github.com");
        blueprintRepo.setOwnerId("org");
        blueprintRepo.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
        blueprint.setBlueprintRepo(blueprintRepo);
        ResponseEntity<BlueprintRes> createResponse = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                BlueprintRes.class
        );
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody()).isNotNull();
        String blueprintUuid = createResponse.getBody().getUuid();
        stubGitCloneToTempDir(tempDir);

        Files.writeString(tempDir.resolve("README.md"), "readme-body", StandardCharsets.UTF_8);
        Files.createDirectories(tempDir.resolve("manifest"));
        Files.writeString(tempDir.resolve("manifest/blueprint.yaml"), "manifest-body", StandardCharsets.UTF_8);
        Files.createDirectories(tempDir.resolve("templates"));
        Files.writeString(tempDir.resolve("templates/descriptor.json"), "{}", StandardCharsets.UTF_8);

        String url = buildGetRepositoryContentUrl(blueprintUuid, "main", null, null, null);
        ResponseEntity<RepositoryContentReadRes> response = rest.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(createJsonGitProviderHeaders()),
                new ParameterizedTypeReference<RepositoryContentReadRes>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getResources()).hasSize(3);
        Map<String, String> byPath = response.getBody().getResources().stream()
                .collect(Collectors.toMap(RepositoryContentFileRes::getFilePath,
                        RepositoryContentFileRes::getFileContent));
        assertThat(byPath.get("README.md")).isEqualTo("readme-body");
        assertThat(byPath.get("manifest/blueprint.yaml")).isEqualTo("manifest-body");
        assertThat(byPath.get("templates/descriptor.json")).isEqualTo("{}");

        rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
    }

    /**
     * REPO-READ-006 — Blueprint not found
     */
    @Test
    void whenReadRepositoryContentForUnknownBlueprintThenReturnsNotFound() {
        String url = buildGetRepositoryContentUrl(
                "00000000-0000-0000-0000-000000000001", "main", null, null, List.of("x.txt"));
        ResponseEntity<String> response = rest.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(createJsonGitProviderHeaders()),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * REPO-READ-007 — Conflicting repository pointer
     */
    @Test
    void whenReadRepositoryContentWithBranchAndTagThenReturnsBadRequest() {
        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName("whenReadRepositoryContentWithBranchAndTagThenReturnsBadRequest-bp");
        blueprint.setDisplayName("whenReadRepositoryContentWithBranchAndTagThenReturnsBadRequest-display");
        blueprint.setDescription("whenReadRepositoryContentWithBranchAndTagThenReturnsBadRequest-description");
        BlueprintRes.BlueprintRepoRes blueprintRepo = new BlueprintRes.BlueprintRepoRes();
        blueprintRepo.setExternalIdentifier("ext-id");
        blueprintRepo.setName("repo-name");
        blueprintRepo.setDescription("repo-desc");
        blueprintRepo.setManifestRootPath("/manifest");
        blueprintRepo.setDescriptorTemplatePath("/template");
        blueprintRepo.setReadmePath("/readme");
        blueprintRepo.setRemoteUrlHttp("https://github.com/org/repo.git");
        blueprintRepo.setRemoteUrlSsh("git@github.com:org/repo.git");
        blueprintRepo.setDefaultBranch("main");
        blueprintRepo.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
        blueprintRepo.setProviderBaseUrl("https://github.com");
        blueprintRepo.setOwnerId("org");
        blueprintRepo.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
        blueprint.setBlueprintRepo(blueprintRepo);
        ResponseEntity<BlueprintRes> createResponse = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                BlueprintRes.class
        );
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody()).isNotNull();
        String blueprintUuid = createResponse.getBody().getUuid();

        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(
                apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid + "/repository-content"));
        b.queryParam("branch", "main");
        b.queryParam("tag", "v1.0.0");

        ResponseEntity<String> response = rest.exchange(
                b.build().toUriString(),
                HttpMethod.GET,
                new HttpEntity<>(createJsonGitProviderHeaders()),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
    }

    /**
     * REPO-READ-008 — Path traversal in requested path
     */
    @Test
    void whenReadRepositoryContentWithPathTraversalThenReturnsBadRequest() {
        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName("whenReadRepositoryContentWithPathTraversalThenReturnsBadRequest-bp");
        blueprint.setDisplayName("whenReadRepositoryContentWithPathTraversalThenReturnsBadRequest-display");
        blueprint.setDescription("whenReadRepositoryContentWithPathTraversalThenReturnsBadRequest-description");
        BlueprintRes.BlueprintRepoRes blueprintRepo = new BlueprintRes.BlueprintRepoRes();
        blueprintRepo.setExternalIdentifier("ext-id");
        blueprintRepo.setName("repo-name");
        blueprintRepo.setDescription("repo-desc");
        blueprintRepo.setManifestRootPath("/manifest");
        blueprintRepo.setDescriptorTemplatePath("/template");
        blueprintRepo.setReadmePath("/readme");
        blueprintRepo.setRemoteUrlHttp("https://github.com/org/repo.git");
        blueprintRepo.setRemoteUrlSsh("git@github.com:org/repo.git");
        blueprintRepo.setDefaultBranch("main");
        blueprintRepo.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
        blueprintRepo.setProviderBaseUrl("https://github.com");
        blueprintRepo.setOwnerId("org");
        blueprintRepo.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
        blueprint.setBlueprintRepo(blueprintRepo);
        ResponseEntity<BlueprintRes> createResponse = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                BlueprintRes.class
        );
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody()).isNotNull();
        String blueprintUuid = createResponse.getBody().getUuid();

        String url = buildGetRepositoryContentUrl(blueprintUuid, "main", null, null, List.of("../../etc/passwd"));
        ResponseEntity<String> response = rest.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(createJsonGitProviderHeaders()),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
    }

    /**
     * REPO-READ-009 — Repository pointer not found or Git read failure
     */
    @Test
    void whenReadRepositoryContentGitReadFailsThenReturnsBadRequest() {
        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName("whenReadRepositoryContentGitReadFailsThenReturnsBadRequest-bp");
        blueprint.setDisplayName("whenReadRepositoryContentGitReadFailsThenReturnsBadRequest-display");
        blueprint.setDescription("whenReadRepositoryContentGitReadFailsThenReturnsBadRequest-description");
        BlueprintRes.BlueprintRepoRes blueprintRepo = new BlueprintRes.BlueprintRepoRes();
        blueprintRepo.setExternalIdentifier("ext-id");
        blueprintRepo.setName("repo-name");
        blueprintRepo.setDescription("repo-desc");
        blueprintRepo.setManifestRootPath("/manifest");
        blueprintRepo.setDescriptorTemplatePath("/template");
        blueprintRepo.setReadmePath("/readme");
        blueprintRepo.setRemoteUrlHttp("https://github.com/org/repo.git");
        blueprintRepo.setRemoteUrlSsh("git@github.com:org/repo.git");
        blueprintRepo.setDefaultBranch("main");
        blueprintRepo.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
        blueprintRepo.setProviderBaseUrl("https://github.com");
        blueprintRepo.setOwnerId("org");
        blueprintRepo.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
        blueprint.setBlueprintRepo(blueprintRepo);
        ResponseEntity<BlueprintRes> createResponse = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                BlueprintRes.class
        );
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody()).isNotNull();
        String blueprintUuid = createResponse.getBody().getUuid();

        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        GitOperation mockGitOperation = Mockito.mock(GitOperation.class);
        when(mockGitProvider.gitOperation()).thenReturn(mockGitOperation);
        when(mockGitProvider.getRepository(eq("ext-id"), eq("org"))).thenReturn(Optional.of(new Repository()));

        doThrow(new GitOperationException("branch not found")).when(mockGitOperation).readRepository(any(), any(),
                any());

        String url = buildGetRepositoryContentUrl(blueprintUuid, "nonexistent-branch", null, null, List.of("x.txt"));
        ResponseEntity<String> response = rest.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(createJsonGitProviderHeaders()),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
    }

    /**
     * REPO-READ-010 — Requested file does not exist at pointer
     */
    @Test
    void whenReadRepositoryContentFileMissingThenReturnsNotFound(@TempDir Path tempDir) throws Exception {
        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName("whenReadRepositoryContentFileMissingThenReturnsNotFound-bp");
        blueprint.setDisplayName("whenReadRepositoryContentFileMissingThenReturnsNotFound-display");
        blueprint.setDescription("whenReadRepositoryContentFileMissingThenReturnsNotFound-description");
        BlueprintRes.BlueprintRepoRes blueprintRepo = new BlueprintRes.BlueprintRepoRes();
        blueprintRepo.setExternalIdentifier("ext-id");
        blueprintRepo.setName("repo-name");
        blueprintRepo.setDescription("repo-desc");
        blueprintRepo.setManifestRootPath("/manifest");
        blueprintRepo.setDescriptorTemplatePath("/template");
        blueprintRepo.setReadmePath("/readme");
        blueprintRepo.setRemoteUrlHttp("https://github.com/org/repo.git");
        blueprintRepo.setRemoteUrlSsh("git@github.com:org/repo.git");
        blueprintRepo.setDefaultBranch("main");
        blueprintRepo.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
        blueprintRepo.setProviderBaseUrl("https://github.com");
        blueprintRepo.setOwnerId("org");
        blueprintRepo.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
        blueprint.setBlueprintRepo(blueprintRepo);
        ResponseEntity<BlueprintRes> createResponse = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                BlueprintRes.class
        );
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody()).isNotNull();
        String blueprintUuid = createResponse.getBody().getUuid();
        stubGitCloneToTempDir(tempDir);

        String url = buildGetRepositoryContentUrl(blueprintUuid, "main", null, null, List.of("missing.txt"));
        ResponseEntity<String> response = rest.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(createJsonGitProviderHeaders()),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
    }

    private void stubGitCloneToTempDir(Path tempDir) {
        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        GitOperation mockGitOperation = Mockito.mock(GitOperation.class);
        when(mockGitProvider.gitOperation()).thenReturn(mockGitOperation);
        when(mockGitProvider.getRepository(eq("ext-id"), eq("org"))).thenReturn(Optional.of(new Repository()));

        doAnswer(invocation -> {
            Consumer<File> consumer = invocation.getArgument(2);
            consumer.accept(tempDir.toFile());
            return null;
        }).when(mockGitOperation).readRepository(any(), any(), any());
    }

    private String buildGetRepositoryContentUrl(String blueprintUuid, String branch, String tag, String commit,
                                                List<String> paths) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(
                apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid + "/repository-content"));
        if (branch != null) {
            b.queryParam("branch", branch);
        }
        if (tag != null) {
            b.queryParam("tag", tag);
        }
        if (commit != null) {
            b.queryParam("commit", commit);
        }
        if (paths != null) {
            for (String p : paths) {
                b.queryParam("path", p);
            }
        }
        return b.build().toUriString();
    }

    private HttpHeaders createJsonGitProviderHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-odm-gpauth-type", "PAT");
        headers.set("x-odm-gpauth-param-token", TEST_PAT_TOKEN);
        headers.set("x-odm-gpauth-param-username", TEST_PAT_USERNAME);
        return headers;
    }
}
