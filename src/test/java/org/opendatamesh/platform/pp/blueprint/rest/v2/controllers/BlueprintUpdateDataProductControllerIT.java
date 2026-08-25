package org.opendatamesh.platform.pp.blueprint.rest.v2.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.opendatamesh.platform.git.exceptions.GitClientException;
import org.opendatamesh.platform.git.exceptions.GitOperationException;
import org.opendatamesh.platform.git.exceptions.GitProviderAuthenticationException;
import org.opendatamesh.platform.git.git.GitOperation;
import org.opendatamesh.platform.git.model.*;
import org.opendatamesh.platform.git.provider.GitProvider;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.manifestautofiller.OdmBlueprintManifestAutoFiller;
import org.opendatamesh.platform.pp.blueprint.rest.v2.BlueprintApplicationIT;
import org.opendatamesh.platform.pp.blueprint.rest.v2.RoutesV2;
import org.opendatamesh.platform.pp.blueprint.rest.v2.mocks.GitProviderFactoryMock;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.ErrorRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintRepoOwnerTypeRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintRepoProviderTypeRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.BlueprintVersionRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.updatedataproduct.UpdateDataProductCommandRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.updatedataproduct.UpdateDataProductResultRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.updatedataproduct.UpdateDataProductTargetRepositoryRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.gitproviders.RepositoryRes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for update-data-product endpoint (BDMD-5127).
 */
public class BlueprintUpdateDataProductControllerIT extends BlueprintApplicationIT {

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
            "instantiate/source-repo/scripts/bootstrap.sh");

    @Autowired
    private GitProviderFactoryMock gitProviderFactoryMock;

    @BeforeEach
    @AfterEach
    void resetGitMocks() {
        gitProviderFactoryMock.reset();
    }

    @Test
    void whenUpdateWithoutPullRequestThenReturn200(@TempDir Path sourceDir, @TempDir Path targetDir) throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        Files.writeString(targetDir.resolve("from-checkpoint.txt"), "baseline");
        BlueprintPair context = createBlueprintWithVersions("mesh-dp", "1.0.0", "2.0.0");
        GitOperation mockGitOperation = stubUpdateHappyPath(sourceDir, targetDir);

        ResponseEntity<UpdateDataProductResultRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_UPDATE_DATA_PRODUCT),
                HttpMethod.POST,
                new HttpEntity<>(buildUpdateRequest(context.blueprintName, false, null), jsonHeaders()),
                UpdateDataProductResultRes.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getResults()).hasSize(1);
        assertThat(response.getBody().getResults().getFirst().getUpdateBranchName())
                .isEqualTo("update/blueprint-v2.0.0");
        assertThat(response.getBody().getResults().getFirst().getCheckpointTag()).isEqualTo("blueprint-v2.0.0");
        assertThat(response.getBody().getResults().getFirst().getCommitHash()).isEqualTo("abc123def456");
        assertThat(response.getBody().getResults().getFirst().getPullRequestWebUrl()).isNull();
        assertThat(response.getBody().getWarnings()).isEmpty();

        ArgumentCaptor<RepositoryPointer> pointerCaptor = ArgumentCaptor.forClass(RepositoryPointer.class);
        verify(mockGitOperation, times(2)).readRepository(any(), pointerCaptor.capture(), any());
        assertThat(pointerCaptor.getAllValues().get(0)).isInstanceOf(RepositoryPointerTag.class);
        assertThat(pointerCaptor.getAllValues().get(0).getRefValue()).isEqualTo("blueprint-v1.0.0");
        assertThat(pointerCaptor.getAllValues().get(1)).isInstanceOf(RepositoryPointerTag.class);
        assertThat(pointerCaptor.getAllValues().get(1).getRefValue()).isEqualTo("v2.0.0");
        verify(mockGitOperation).createAndCheckoutBranch(eq(targetDir.toFile()), eq("update/blueprint-v2.0.0"));
        verify(mockGitOperation).pushBranch(eq(targetDir.toFile()), eq("update/blueprint-v2.0.0"));
        verify(mockGitOperation).pushTag(eq(targetDir.toFile()), eq("blueprint-v2.0.0"));
        verify(gitProviderFactoryMock.getMockGitProvider(), never()).createPullRequest(any(), any());

        deleteCreatedBlueprint(context.blueprintUuid);
    }

    @Test
    void whenUpdateWithPullRequestThenReturnWebUrl(@TempDir Path sourceDir, @TempDir Path targetDir) throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        BlueprintPair context = createBlueprintWithVersions("mesh-dp", "1.0.0", "2.0.0");
        stubUpdateHappyPath(sourceDir, targetDir);
        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        PullRequest pullRequest = new PullRequest();
        pullRequest.setWebUrl("https://github.com/org/dp-repo/pull/42");
        when(mockGitProvider.createPullRequest(any(), any())).thenReturn(pullRequest);

        UpdateDataProductCommandRes request = buildUpdateRequest(context.blueprintName, true, "main");
        ResponseEntity<UpdateDataProductResultRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_UPDATE_DATA_PRODUCT),
                HttpMethod.POST,
                new HttpEntity<>(request, jsonHeaders()),
                UpdateDataProductResultRes.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getResults().getFirst().getPullRequestWebUrl())
                .isEqualTo("https://github.com/org/dp-repo/pull/42");
        assertThat(response.getBody().getWarnings()).isEmpty();

        ArgumentCaptor<CreatePullRequest> prCaptor = ArgumentCaptor.forClass(CreatePullRequest.class);
        verify(mockGitProvider).createPullRequest(any(), prCaptor.capture());
        assertThat(prCaptor.getValue().getSourceBranch()).isEqualTo("update/blueprint-v2.0.0");
        assertThat(prCaptor.getValue().getTargetBranch()).isEqualTo("main");

        deleteCreatedBlueprint(context.blueprintUuid);
    }

    @Test
    void whenPullRequestTargetBranchOmittedThenUsesRepositoryDefault(@TempDir Path sourceDir, @TempDir Path targetDir)
            throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        BlueprintPair context = createBlueprintWithVersions("mesh-dp", "1.0.0", "2.0.0");
        stubUpdateHappyPath(sourceDir, targetDir);
        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        PullRequest pullRequest = new PullRequest();
        pullRequest.setWebUrl("https://github.com/org/dp-repo/pull/7");
        when(mockGitProvider.createPullRequest(any(), any())).thenReturn(pullRequest);

        ResponseEntity<UpdateDataProductResultRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_UPDATE_DATA_PRODUCT),
                HttpMethod.POST,
                new HttpEntity<>(buildUpdateRequest(context.blueprintName, true, null), jsonHeaders()),
                UpdateDataProductResultRes.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ArgumentCaptor<CreatePullRequest> prCaptor = ArgumentCaptor.forClass(CreatePullRequest.class);
        verify(mockGitProvider).createPullRequest(any(), prCaptor.capture());
        assertThat(prCaptor.getValue().getTargetBranch()).isEqualTo("main");
        deleteCreatedBlueprint(context.blueprintUuid);
    }

    @Test
    void whenAuthorOmittedThenUsesServerDefaults(@TempDir Path sourceDir, @TempDir Path targetDir) throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        BlueprintPair context = createBlueprintWithVersions("mesh-dp", "1.0.0", "2.0.0");
        AtomicReference<Commit> commitRef = new AtomicReference<>();
        stubUpdateHappyPath(sourceDir, targetDir, commitRef);

        ResponseEntity<UpdateDataProductResultRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_UPDATE_DATA_PRODUCT),
                HttpMethod.POST,
                new HttpEntity<>(buildUpdateRequest(context.blueprintName, false, null), jsonHeaders()),
                UpdateDataProductResultRes.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(commitRef.get().getAuthor()).isEqualTo("odm-blueprint-server");
        assertThat(commitRef.get().getAuthorEmail()).isEqualTo("odm-blueprint-server@local");
        deleteCreatedBlueprint(context.blueprintUuid);
    }

    @Test
    void whenPullRequestFailsAfterSuccessfulUpdateThenReturn200WithWarnings(@TempDir Path sourceDir,
            @TempDir Path targetDir) throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        BlueprintPair context = createBlueprintWithVersions("mesh-dp", "1.0.0", "2.0.0");
        stubUpdateHappyPath(sourceDir, targetDir);
        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        when(mockGitProvider.createPullRequest(any(), any()))
                .thenThrow(new GitClientException(400, "cannot open PR"));

        ResponseEntity<UpdateDataProductResultRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_UPDATE_DATA_PRODUCT),
                HttpMethod.POST,
                new HttpEntity<>(buildUpdateRequest(context.blueprintName, true, "main"), jsonHeaders()),
                UpdateDataProductResultRes.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getResults()).hasSize(1);
        assertThat(response.getBody().getResults().getFirst().getUpdateBranchName())
                .isEqualTo("update/blueprint-v2.0.0");
        assertThat(response.getBody().getResults().getFirst().getCheckpointTag()).isEqualTo("blueprint-v2.0.0");
        assertThat(response.getBody().getResults().getFirst().getPullRequestWebUrl()).isNull();
        assertThat(response.getBody().getWarnings()).isNotEmpty();
        assertThat(response.getBody().getWarnings().getFirst()).contains("Pull request creation failed");
        assertThat(response.getBody().getWarnings().getFirst()).contains("already pushed");
        deleteCreatedBlueprint(context.blueprintUuid);
    }

    @Test
    void whenBlueprintNameBlankThenReturn400() throws Exception {
        BlueprintPair context = createBlueprintWithVersions("mesh-dp", "1.0.0", "2.0.0");
        UpdateDataProductCommandRes request = buildUpdateRequest(context.blueprintName, false, null);
        request.setBlueprintName(" ");

        ResponseEntity<ErrorRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_UPDATE_DATA_PRODUCT),
                HttpMethod.POST,
                new HttpEntity<>(request, jsonHeaders()),
                ErrorRes.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getError()).isEqualTo("BadRequestException");
        assertThat(response.getBody().getMessage()).isEqualTo("Blueprint name is required");
        deleteCreatedBlueprint(context.blueprintUuid);
    }

    @Test
    void whenCurrentAndNextEqualThenReturn400() throws Exception {
        BlueprintPair context = createBlueprintWithVersions("mesh-dp", "1.0.0", "2.0.0");
        UpdateDataProductCommandRes request = buildUpdateRequest(context.blueprintName, false, null);
        request.setNextVersionNumber("1.0.0");

        ResponseEntity<ErrorRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_UPDATE_DATA_PRODUCT),
                HttpMethod.POST,
                new HttpEntity<>(request, jsonHeaders()),
                ErrorRes.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getError()).isEqualTo("BadRequestException");
        assertThat(response.getBody().getMessage())
                .isEqualTo("Current and next blueprint version numbers must be different");
        deleteCreatedBlueprint(context.blueprintUuid);
    }

    @Test
    void whenTargetRepositoriesEmptyThenReturn400() throws Exception {
        BlueprintPair context = createBlueprintWithVersions("mesh-dp", "1.0.0", "2.0.0");
        UpdateDataProductCommandRes request = buildUpdateRequest(context.blueprintName, false, null);
        request.setTargetRepositories(List.of());

        ResponseEntity<ErrorRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_UPDATE_DATA_PRODUCT),
                HttpMethod.POST,
                new HttpEntity<>(request, jsonHeaders()),
                ErrorRes.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("At least one target repository is required");
        deleteCreatedBlueprint(context.blueprintUuid);
    }

    @Test
    void whenMoreThanOneTargetThenReturn400() throws Exception {
        BlueprintPair context = createBlueprintWithVersions("mesh-dp", "1.0.0", "2.0.0");
        UpdateDataProductCommandRes request = buildUpdateRequest(context.blueprintName, false, null);
        UpdateDataProductTargetRepositoryRes second = new UpdateDataProductTargetRepositoryRes();
        second.setTargetId(OdmBlueprintManifestAutoFiller.DEFAULT_REPOSITORY_KEY);
        second.setRepository(buildTargetRepository());
        request.setTargetRepositories(List.of(request.getTargetRepositories().getFirst(), second));

        ResponseEntity<ErrorRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_UPDATE_DATA_PRODUCT),
                HttpMethod.POST,
                new HttpEntity<>(request, jsonHeaders()),
                ErrorRes.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage())
                .isEqualTo(
                        "Exactly one target repository is required, only monorepo is supported in this phase");
        deleteCreatedBlueprint(context.blueprintUuid);
    }

    @Test
    void whenUnknownBlueprintThenReturn404() {
        UpdateDataProductCommandRes request = buildUpdateRequest("does-not-exist", false, null);

        ResponseEntity<ErrorRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_UPDATE_DATA_PRODUCT),
                HttpMethod.POST,
                new HttpEntity<>(request, jsonHeaders()),
                ErrorRes.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getError()).isEqualTo("NotFoundException");
        assertThat(response.getBody().getMessage()).contains("does-not-exist");
    }

    @Test
    void whenCurrentCheckpointMissingThenReturn400(@TempDir Path sourceDir, @TempDir Path targetDir) throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        BlueprintPair context = createBlueprintWithVersions("mesh-dp", "1.0.0", "2.0.0");
        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        GitOperation mockGitOperation = Mockito.mock(GitOperation.class);
        when(mockGitProvider.gitOperation()).thenReturn(mockGitOperation);
        doThrow(new GitOperationException("readRepository", "checkpoint tag blueprint-v1.0.0 was not found"))
                .when(mockGitOperation).readRepository(any(), any(), any());

        ResponseEntity<ErrorRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_UPDATE_DATA_PRODUCT),
                HttpMethod.POST,
                new HttpEntity<>(buildUpdateRequest(context.blueprintName, false, null), jsonHeaders()),
                ErrorRes.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getError()).isEqualTo("GitOperationFailed");
        assertThat(response.getBody().getMessage()).contains("blueprint-v1.0.0");
        verify(mockGitOperation, never()).createAndCheckoutBranch(any(), anyString());
        deleteCreatedBlueprint(context.blueprintUuid);
    }

    @Test
    void whenUpdateBranchAlreadyExistsThenReturn400(@TempDir Path sourceDir, @TempDir Path targetDir) throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        Files.writeString(targetDir.resolve("from-checkpoint.txt"), "baseline");
        BlueprintPair context = createBlueprintWithVersions("mesh-dp", "1.0.0", "2.0.0");
        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        GitOperation mockGitOperation = Mockito.mock(GitOperation.class);
        when(mockGitProvider.gitOperation()).thenReturn(mockGitOperation);
        doAnswer(invocation -> {
            Consumer<File> consumer = invocation.getArgument(2);
            consumer.accept(targetDir.toFile());
            return null;
        }).when(mockGitOperation).readRepository(any(), any(), any());
        doThrow(new GitOperationException("createAndCheckoutBranch", "branch update/blueprint-v2.0.0 already exists"))
                .when(mockGitOperation).createAndCheckoutBranch(any(), anyString());

        ResponseEntity<ErrorRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_UPDATE_DATA_PRODUCT),
                HttpMethod.POST,
                new HttpEntity<>(buildUpdateRequest(context.blueprintName, false, null), jsonHeaders()),
                ErrorRes.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getError()).isEqualTo("GitOperationFailed");
        assertThat(response.getBody().getMessage()).contains("update/blueprint-v2.0.0");
        verify(mockGitOperation, never()).pushTag(any(), anyString());
        deleteCreatedBlueprint(context.blueprintUuid);
    }

    @Test
    void whenGitCredentialsInvalidThenReturn400() throws Exception {
        BlueprintPair context = createBlueprintWithVersions("mesh-dp", "1.0.0", "2.0.0");
        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        when(mockGitProvider.gitOperation())
                .thenThrow(new GitProviderAuthenticationException("Authentication failed for Git provider"));

        ResponseEntity<ErrorRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_UPDATE_DATA_PRODUCT),
                HttpMethod.POST,
                new HttpEntity<>(buildUpdateRequest(context.blueprintName, false, null), jsonHeaders()),
                ErrorRes.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getError()).isEqualTo("Git Provider Authentication Failed");
        assertThat(response.getBody().getMessage()).doesNotContain("test-token");
        deleteCreatedBlueprint(context.blueprintUuid);
    }

    private GitOperation stubUpdateHappyPath(Path sourceDir, Path targetDir) {
        return stubUpdateHappyPath(sourceDir, targetDir, null);
    }

    private GitOperation stubUpdateHappyPath(Path sourceDir, Path targetDir, AtomicReference<Commit> commitRef) {
        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        GitOperation mockGitOperation = Mockito.mock(GitOperation.class);
        when(mockGitProvider.gitOperation()).thenReturn(mockGitOperation);
        when(mockGitProvider.getRepository(anyString(), anyString())).thenReturn(Optional.of(new Repository()));

        AtomicInteger callCounter = new AtomicInteger(0);
        doAnswer(invocation -> {
            Consumer<File> consumer = invocation.getArgument(2);
            // first: target at current checkpoint; second: source at next version tag
            consumer.accept(callCounter.getAndIncrement() == 0 ? targetDir.toFile() : sourceDir.toFile());
            return null;
        }).when(mockGitOperation).readRepository(any(), any(), any());

        when(mockGitOperation.createAndCheckoutBranch(any(), anyString())).thenReturn("abc123def456");
        doNothing().when(mockGitOperation).addAll(any());
        doAnswer(invocation -> {
            if (commitRef != null) {
                commitRef.set(invocation.getArgument(1));
            }
            return null;
        }).when(mockGitOperation).commit(any(), any());
        when(mockGitOperation.getHeadSha(any(), anyString())).thenReturn("abc123def456");
        doNothing().when(mockGitOperation).addTag(any(), any(Tag.class));
        doNothing().when(mockGitOperation).pushBranch(any(), anyString());
        doNothing().when(mockGitOperation).pushTag(any(), anyString());
        return mockGitOperation;
    }

    private UpdateDataProductCommandRes buildUpdateRequest(
            String blueprintName,
            boolean createPullRequest,
            String pullRequestTargetBranch) {
        UpdateDataProductCommandRes request = new UpdateDataProductCommandRes();
        request.setBlueprintName(blueprintName);
        request.setCurrentVersionNumber("1.0.0");
        request.setNextVersionNumber("2.0.0");
        request.setCreatePullRequest(createPullRequest);

        UpdateDataProductTargetRepositoryRes target = new UpdateDataProductTargetRepositoryRes();
        target.setTargetId(OdmBlueprintManifestAutoFiller.DEFAULT_REPOSITORY_KEY);
        target.setRepository(buildTargetRepository());
        target.setPullRequestTargetBranch(pullRequestTargetBranch);
        request.setTargetRepositories(List.of(target));

        Map<String, JsonNode> parameters = new LinkedHashMap<>();
        parameters.put("environment", OBJECT_MAPPER.valueToTree("prod"));
        parameters.put("retentionDays", OBJECT_MAPPER.valueToTree(365));
        request.setParameters(parameters);
        return request;
    }

    private RepositoryRes buildTargetRepository() {
        RepositoryRes repositoryRes = new RepositoryRes();
        repositoryRes.setId("dp-repo");
        repositoryRes.setName("dp-repo");
        repositoryRes.setDescription("Data product repository");
        repositoryRes.setCloneUrlHttp("https://github.com/org/dp-repo.git");
        repositoryRes.setCloneUrlSsh("git@github.com:org/dp-repo.git");
        repositoryRes.setDefaultBranch("main");
        repositoryRes.setOwnerId("org");
        return repositoryRes;
    }

    private BlueprintPair createBlueprintWithVersions(String blueprintName, String currentVersion, String nextVersion)
            throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String uniqueBlueprintName = blueprintName + "-" + suffix;
        JsonNode manifest = manifestMonorepoNoComposition();

        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName(uniqueBlueprintName);
        blueprint.setDisplayName(uniqueBlueprintName + "-display");
        blueprint.setDescription(uniqueBlueprintName + "-description");
        blueprint.setBlueprintRepo(buildBlueprintRepo());

        ResponseEntity<BlueprintRes> createdBlueprint = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                BlueprintRes.class);
        assertThat(createdBlueprint.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createdBlueprint.getBody()).isNotNull();

        createVersion(createdBlueprint.getBody(), uniqueBlueprintName, currentVersion, manifest);
        createVersion(createdBlueprint.getBody(), uniqueBlueprintName, nextVersion, manifest);
        return new BlueprintPair(createdBlueprint.getBody().getUuid(), uniqueBlueprintName);
    }

    private void createVersion(BlueprintRes blueprint, String blueprintName, String version, JsonNode manifestContent) {
        ObjectNode content = (ObjectNode) manifestContent.deepCopy();
        content.put("name", blueprintName);
        content.put("version", version);

        BlueprintVersionRes versionRes = new BlueprintVersionRes();
        versionRes.setName(blueprintName + "-" + version);
        versionRes.setDescription("version " + version);
        versionRes.setReadme("README.md");
        versionRes.setTag("v" + version);
        versionRes.setVersionNumber(version);
        versionRes.setSpec("odm-blueprint-manifest");
        versionRes.setSpecVersion("1.0.0");
        versionRes.setBlueprint(blueprint);
        versionRes.setContent(content);

        ResponseEntity<BlueprintVersionRes> createdVersion = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS),
                new HttpEntity<>(versionRes),
                BlueprintVersionRes.class);
        assertThat(createdVersion.getStatusCode()).isEqualTo(HttpStatus.CREATED);
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
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("manifest/example-2.1-monorepo-no-composition.yaml")) {
            return YAML_OBJECT_MAPPER.readTree(inputStream);
        }
    }

    private void writeSourceBlueprintFiles(Path sourceDir) throws IOException {
        for (String resourcePath : SOURCE_REPO_RESOURCE_FILES) {
            String relativePath = resourcePath.replaceFirst("^instantiate/source-repo/", "");
            Path destination = sourceDir.resolve(relativePath);
            if (destination.getParent() != null) {
                Files.createDirectories(destination.getParent());
            }
            try (InputStream inputStream = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream(resourcePath)) {
                Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-odm-gpauth-type", "PAT");
        headers.set("x-odm-gpauth-param-token", "test-token");
        headers.set("x-odm-gpauth-param-username", "test-user");
        return headers;
    }

    private void deleteCreatedBlueprint(String blueprintUuid) {
        if (blueprintUuid != null) {
            rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
        }
    }

    private record BlueprintPair(String blueprintUuid, String blueprintName) {
    }
}
