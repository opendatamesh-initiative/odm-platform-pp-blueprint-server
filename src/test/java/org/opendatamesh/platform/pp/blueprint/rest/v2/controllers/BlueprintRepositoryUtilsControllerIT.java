package org.opendatamesh.platform.pp.blueprint.rest.v2.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link BlueprintRepositoryUtilsController}.
 */
public class BlueprintRepositoryUtilsControllerIT extends BlueprintApplicationIT {

    private static final String TEST_PAT_TOKEN = "test-pat-token";
    private static final String TEST_PAT_USERNAME = "test-user";

    /** Realistic ODM-style manifest (YAML). */
    private static final String EXPECTED_MANIFEST_YAML = """
            apiVersion: blueprint.odm/v1
            kind: BlueprintManifest
            metadata:
              name: sample-blueprint
            spec:
              description: Integration test manifest
            """;

    /** Valid JSON with nested object (pretty-printed as sent in the request body). */
    private static final String EXPECTED_CONFIG_JSON = """
            {
              "schemaVersion": 1,
              "features": {
                "registry": true
              }
            }
            """;

    /** Markdown readme. */
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
        String blueprintUuid = createBlueprintWithRepository("whenInitRepositoryContentWithValidPayloadThenReturnsOk");

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
        String blueprintUuid = createBlueprintWithRepository("whenInitRepositoryContentWithEmptyResourcesThenReturnsBadRequest");

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
        String blueprintUuid = createBlueprintWithRepository("whenRemoteRepositoryNotFoundThenReturnsBadRequest");

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
        String blueprintUuid = createBlueprintWithRepository("whenGitReadRepositoryFailsThenReturnsBadRequest");

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

    private String createBlueprintWithRepository(String namePrefix) {
        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName(namePrefix + "-bp");
        blueprint.setDisplayName(namePrefix + "-display");
        blueprint.setDescription(namePrefix + "-description");

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

        ResponseEntity<BlueprintRes> response = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                BlueprintRes.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody().getUuid();
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
