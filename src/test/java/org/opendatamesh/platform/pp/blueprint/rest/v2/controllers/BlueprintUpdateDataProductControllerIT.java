package org.opendatamesh.platform.pp.blueprint.rest.v2.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.updatedataproduct.UpdateDataProductTargetResultRes;
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

    /**
     * Scenario: Monorepo without composition updates one target from the current checkpoint
     * Given a published parent with one repository key and no composition
     * And the mapped remote already has checkpoint tag blueprint-v{current}
     * When the client posts update-data-product with that key and next version content
     * Then the server creates update/blueprint-v{next} from the current checkpoint, cleans, applies next root.targets, tags blueprint-v{next}, and returns one result
     */
    @Test
    void whenMonorepoNoCompositionUpdateThenHonorRootTargetsAndCheckpoint(@TempDir Path sourceDir, @TempDir Path targetDir) throws Exception {
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
        assertThat(pointerCaptor.getAllValues().get(0).getRefValue()).isEqualTo("v2.0.0");
        assertThat(pointerCaptor.getAllValues().get(1)).isInstanceOf(RepositoryPointerTag.class);
        assertThat(pointerCaptor.getAllValues().get(1).getRefValue()).isEqualTo("blueprint-v1.0.0");
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

    /**
     * Scenario: Global pull request failure is a warning
     * Given createPullRequest is true and Git push for a target succeeds
     * When opening the PR fails
     * Then HTTP 200 includes that warning and later targets still run
     */
    @Test
    void whenPullRequestOpenFailsThenReturn200WithWarningAndContinueTargets(
            @TempDir Path sourceDir,
            @TempDir Path infraTarget,
            @TempDir Path appTarget) throws Exception {
        writePolyrepoSourceFiles(sourceDir);
        JsonNode manifest = manifestPolyrepoNoComposition();
        BlueprintPair context = createBlueprintWithVersions("split-stack", "1.0.0", "2.0.0", manifest, manifest);

        GitOperation mockGitOperation = stubUpdateHappyPath(List.of(sourceDir), infraTarget, appTarget);
        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        when(mockGitProvider.createPullRequest(any(), any()))
                .thenThrow(new GitClientException(400, "cannot open PR"));

        ResponseEntity<UpdateDataProductResultRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_UPDATE_DATA_PRODUCT),
                HttpMethod.POST,
                new HttpEntity<>(buildPolyrepoUpdateRequest(context.blueprintName, true), jsonHeaders()),
                UpdateDataProductResultRes.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getResults()).hasSize(2);
        assertThat(response.getBody().getWarnings()).isNotEmpty();
        assertThat(response.getBody().getWarnings().getFirst()).contains("Pull request creation failed");
        verify(mockGitOperation, times(2)).pushBranch(any(), eq("update/blueprint-v2.0.0"));
        verify(mockGitOperation, times(2)).pushTag(any(), eq("blueprint-v2.0.0"));
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
    void whenDuplicateTargetIdThenReturn400() throws Exception {
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
                .contains("Duplicate targetId")
                .contains("Send each targetId once");
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
    void whenCurrentCheckpointMissingThenFailWithoutDefaultBranchCheckout(@TempDir Path sourceDir, @TempDir Path targetDir) throws Exception {
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

    /**
     * Scenario: Next version with extra or renamed repository key is rejected
     * Given current and next parent versions of the same blueprint
     * When next instantiation.repositories keys differ from current
     * Then the API returns 400 listing the structural delta with a hint to keep keys stable or instantiate new remotes
     * And no Git mutation occurs
     */
    @Test
    void whenNextRepositoryKeysDifferThenReturn400WithoutGit() throws Exception {
        JsonNode currentManifest = manifestMonorepoNoComposition();
        ObjectNode nextManifest = (ObjectNode) currentManifest.deepCopy();
        ArrayNode repositories = (ArrayNode) nextManifest.at("/instantiation/repositories");
        ObjectNode extra = repositories.addObject();
        extra.put("key", "extra");
        extra.put("description", "extra repository");

        BlueprintPair context = createBlueprintWithVersions("mesh-dp", "1.0.0", "2.0.0", currentManifest, nextManifest);

        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        GitOperation mockGitOperation = Mockito.mock(GitOperation.class);
        when(mockGitProvider.gitOperation()).thenReturn(mockGitOperation);

        ResponseEntity<ErrorRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_UPDATE_DATA_PRODUCT),
                HttpMethod.POST,
                new HttpEntity<>(buildUpdateRequest(context.blueprintName, false, null), jsonHeaders()),
                ErrorRes.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).contains("Repository keys differ");
        assertThat(response.getBody().getMessage()).contains("content-only");
        verify(mockGitOperation, never()).readRepository(any(), any(), any());
        deleteCreatedBlueprint(context.blueprintUuid);
    }

    /**
     * Scenario: Monorepo with composition updates one target from parent and modules
     * Given a next parent composing published 1→1 modules into one key at non-nested paths
     * And the remote has blueprint-v{current}
     * When update-data-product runs with next parameterMapping
     * Then parent and module routes are re-rendered into that one remote and lineage is parent-only
     */
    @Test
    void whenMonorepoWithCompositionUpdateThenRenderParentAndModulesIntoOneTarget(
            @TempDir Path sourceDir,
            @TempDir Path targetDir) throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        writeSafeDescriptor(sourceDir);

        ModuleBlueprint storage = createPublishedModule("odm-blueprint-s3-lake", "3.0.1");
        ModuleBlueprint serving = createPublishedModule("odm-blueprint-api-skeleton", "1.4.0");
        ObjectNode manifest = (ObjectNode) manifestMonorepoWithComposition();
        rewriteCompositionRefs(manifest, storage, serving);

        BlueprintPair context = createBlueprintWithVersions("full-stack-dp", "1.0.0", "2.0.0", manifest, manifest);
        GitOperation mockGitOperation = stubUpdateHappyPath(List.of(sourceDir, sourceDir, sourceDir), targetDir);

        ResponseEntity<UpdateDataProductResultRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_UPDATE_DATA_PRODUCT),
                HttpMethod.POST,
                new HttpEntity<>(buildCompositionUpdateRequest(context.blueprintName, false), jsonHeaders()),
                UpdateDataProductResultRes.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getResults()).hasSize(1);
        assertThat(Files.isDirectory(targetDir.resolve("core"))).isTrue();
        assertThat(Files.isDirectory(targetDir.resolve("data-plane/storage"))).isTrue();
        assertThat(Files.isDirectory(targetDir.resolve("app/serving"))).isTrue();
        assertThat(Files.isDirectory(targetDir.resolve(".odm/blueprint"))).isTrue();
        verify(mockGitOperation, times(4)).readRepository(any(), any(), any());

        deleteCreatedBlueprint(context.blueprintUuid);
        deleteCreatedBlueprint(storage.blueprintUuid());
        deleteCreatedBlueprint(serving.blueprintUuid());
    }

    /**
     * Scenario: Polyrepo without composition updates each remote independently
     * Given a next parent with two or more keys and no composition
     * And each mapped remote has blueprint-v{current}
     * When update-data-product supplies a complete targetId map
     * Then each remote gets its own update branch and next checkpoint tag of the same name
     * And lineage and descriptor exist only on instantiation.root.repository
     */
    @Test
    void whenPolyrepoNoCompositionUpdateThenFanOutResultsAndRootLineageOnly(
            @TempDir Path sourceDir,
            @TempDir Path infraTarget,
            @TempDir Path appTarget) throws Exception {
        writePolyrepoSourceFiles(sourceDir);
        JsonNode manifest = manifestPolyrepoNoComposition();
        BlueprintPair context = createBlueprintWithVersions("split-stack", "1.0.0", "2.0.0", manifest, manifest);

        GitOperation mockGitOperation = stubUpdateHappyPath(List.of(sourceDir), infraTarget, appTarget);

        ResponseEntity<UpdateDataProductResultRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_UPDATE_DATA_PRODUCT),
                HttpMethod.POST,
                new HttpEntity<>(buildPolyrepoUpdateRequest(context.blueprintName, false), jsonHeaders()),
                UpdateDataProductResultRes.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getResults()).hasSize(2);
        assertThat(response.getBody().getResults())
                .extracting(UpdateDataProductTargetResultRes::getCheckpointTag)
                .containsOnly("blueprint-v2.0.0");
        assertThat(Files.exists(appTarget.resolve(".odm/blueprint/blueprint-manifest.yaml"))).isTrue();
        assertThat(Files.exists(infraTarget.resolve(".odm/blueprint"))).isFalse();
        assertThat(Files.exists(infraTarget.resolve("terraform"))).isTrue();
        verify(mockGitOperation, times(2)).pushBranch(any(), eq("update/blueprint-v2.0.0"));
        verify(mockGitOperation, times(2)).pushTag(any(), eq("blueprint-v2.0.0"));
        // One shared parent source clone plus one clone for each target checkpoint.
        verify(mockGitOperation, times(3)).readRepository(any(), any(), any());

        deleteCreatedBlueprint(context.blueprintUuid);
    }

    /**
     * Scenario: Polyrepo with composition routes parent and modules across remotes
     * Given next composition targets pointing at different declared keys
     * When update-data-product runs
     * Then each key that receives routes is updated from its own current checkpoint
     * And module Git provider type and base URL match the parent
     */
    @Test
    void whenPolyrepoWithCompositionUpdateThenRouteModulesAndMatchGitProvider(
            @TempDir Path sourceDir,
            @TempDir Path pipelineTarget,
            @TempDir Path apiTarget) throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        writePolyrepoSourceFiles(sourceDir);
        writeSafeDescriptor(sourceDir);

        ModuleBlueprint ingest = createPublishedModule("odm-blueprint-ingest-batch", "2.0.0");
        ModuleBlueprint consume = createPublishedModule("odm-blueprint-consumer-api", "1.1.0");
        ObjectNode manifest = (ObjectNode) manifestPolyrepoWithComposition();
        rewritePolyrepoCompositionRefs(manifest, ingest, consume);

        BlueprintPair context = createBlueprintWithVersions("mesh-polyrepo", "1.0.0", "2.0.0", manifest, manifest);
        GitOperation mockGitOperation = stubUpdateHappyPath(
                List.of(sourceDir, sourceDir, sourceDir), pipelineTarget, apiTarget);

        ResponseEntity<UpdateDataProductResultRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_UPDATE_DATA_PRODUCT),
                HttpMethod.POST,
                new HttpEntity<>(buildPolyrepoCompositionUpdateRequest(context.blueprintName), jsonHeaders()),
                UpdateDataProductResultRes.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getResults()).hasSize(2);
        verify(mockGitOperation, times(2)).pushTag(any(), eq("blueprint-v2.0.0"));
        // Parent and two modules are cloned once, then both targets are cloned.
        verify(mockGitOperation, times(5)).readRepository(any(), any(), any());

        deleteCreatedBlueprint(context.blueprintUuid);
        deleteCreatedBlueprint(ingest.blueprintUuid());
        deleteCreatedBlueprint(consume.blueprintUuid());
    }

    /**
     * Scenario: Composition module that is not monorepo-no-composition is rejected
     * Given a next parent composing a published polyrepo module
     * When update-data-product is called
     * Then validation fails before Git with a monorepo-no-composition hint
     */
    @Test
    void whenCompositionModuleIsNotMonorepoNoCompositionThenReturn400() throws Exception {
        ModuleBlueprint storage = createPublishedModule(
                "odm-blueprint-s3-lake",
                "3.0.1",
                manifestPolyrepoNoComposition(),
                buildBlueprintRepo());
        ModuleBlueprint serving = createPublishedModule("odm-blueprint-api-skeleton", "1.4.0");
        ObjectNode manifest = (ObjectNode) manifestMonorepoWithComposition();
        rewriteCompositionRefs(manifest, storage, serving);

        BlueprintPair context = createBlueprintWithVersions("full-stack-dp", "1.0.0", "2.0.0", manifest, manifest);

        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        GitOperation mockGitOperation = Mockito.mock(GitOperation.class);
        when(mockGitProvider.gitOperation()).thenReturn(mockGitOperation);

        ResponseEntity<ErrorRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_UPDATE_DATA_PRODUCT),
                HttpMethod.POST,
                new HttpEntity<>(buildCompositionUpdateRequest(context.blueprintName, false), jsonHeaders()),
                ErrorRes.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).contains("not a monorepo with no composition");
        assertThat(response.getBody().getMessage()).contains("monorepo with no composition");
        verify(mockGitOperation, never()).readRepository(any(), any(), any());

        deleteCreatedBlueprint(context.blueprintUuid);
        deleteCreatedBlueprint(storage.blueprintUuid());
        deleteCreatedBlueprint(serving.blueprintUuid());
    }

    /**
     * Scenario: Composition module Git provider mismatch is rejected
     * Given a next parent and a module whose provider type or base URL differs
     * When update-data-product is called
     * Then validation fails before Git with a provider mismatch hint
     */
    @Test
    void whenCompositionModuleProviderMismatchesParentThenReturn400() throws Exception {
        BlueprintRes.BlueprintRepoRes gitlabRepo = buildBlueprintRepo();
        gitlabRepo.setProviderType(BlueprintRepoProviderTypeRes.GITLAB);
        gitlabRepo.setProviderBaseUrl("https://gitlab.com");
        gitlabRepo.setRemoteUrlHttp("https://gitlab.com/org/module-repo.git");
        gitlabRepo.setRemoteUrlSsh("git@gitlab.com:org/module-repo.git");

        ModuleBlueprint storage = createPublishedModule(
                "odm-blueprint-s3-lake", "3.0.1", manifestMonorepoNoComposition(), gitlabRepo);
        ModuleBlueprint serving = createPublishedModule("odm-blueprint-api-skeleton", "1.4.0");
        ObjectNode manifest = (ObjectNode) manifestMonorepoWithComposition();
        rewriteCompositionRefs(manifest, storage, serving);

        BlueprintPair context = createBlueprintWithVersions("full-stack-dp", "1.0.0", "2.0.0", manifest, manifest);

        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        GitOperation mockGitOperation = Mockito.mock(GitOperation.class);
        when(mockGitProvider.gitOperation()).thenReturn(mockGitOperation);

        ResponseEntity<ErrorRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_UPDATE_DATA_PRODUCT),
                HttpMethod.POST,
                new HttpEntity<>(buildCompositionUpdateRequest(context.blueprintName, false), jsonHeaders()),
                ErrorRes.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).contains("Git provider type or base URL does not match");
        assertThat(response.getBody().getMessage()).contains("same Git provider type and base URL");
        verify(mockGitOperation, never()).readRepository(any(), any(), any());

        deleteCreatedBlueprint(context.blueprintUuid);
        deleteCreatedBlueprint(storage.blueprintUuid());
        deleteCreatedBlueprint(serving.blueprintUuid());
    }

    /**
     * Scenario: Root key or topology change is rejected
     * Given current 1→1 and next N→1 or a different instantiation.root.repository
     * When update-data-product is called
     * Then validation fails with a structure-change hint before Git
     */
    @Test
    void whenRootKeyOrTopologyDiffersThenReturn400() throws Exception {
        JsonNode currentManifest = manifestMonorepoNoComposition();
        ObjectNode nextManifest = (ObjectNode) manifestMonorepoWithComposition();

        BlueprintPair context = createBlueprintWithVersions("mesh-dp", "1.0.0", "2.0.0", currentManifest, nextManifest);

        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        GitOperation mockGitOperation = Mockito.mock(GitOperation.class);
        when(mockGitProvider.gitOperation()).thenReturn(mockGitOperation);

        ResponseEntity<ErrorRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_UPDATE_DATA_PRODUCT),
                HttpMethod.POST,
                new HttpEntity<>(buildUpdateRequest(context.blueprintName, false, null), jsonHeaders()),
                ErrorRes.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).contains("content-only");
        assertThat(response.getBody().getMessage()).containsAnyOf("topology", "Repository keys");
        verify(mockGitOperation, never()).readRepository(any(), any(), any());
        deleteCreatedBlueprint(context.blueprintUuid);
    }

    /**
     * Scenario: Route or composition slot change is rejected
     * Given next root.targets or composition alias/blueprintName/targets differ from current
     * When update-data-product is called
     * Then 400 collect-all includes those layout mismatches
     */
    @Test
    void whenRoutesOrCompositionSlotsDifferThenReturn400() throws Exception {
        JsonNode currentManifest = manifestMonorepoNoComposition();
        ObjectNode nextManifest = (ObjectNode) currentManifest.deepCopy();
        ObjectNode rootTarget = (ObjectNode) nextManifest.at("/instantiation/root/targets/0");
        rootTarget.put("path", "renamed/");

        BlueprintPair context = createBlueprintWithVersions("mesh-dp", "1.0.0", "2.0.0", currentManifest, nextManifest);

        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        GitOperation mockGitOperation = Mockito.mock(GitOperation.class);
        when(mockGitProvider.gitOperation()).thenReturn(mockGitOperation);

        ResponseEntity<ErrorRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_UPDATE_DATA_PRODUCT),
                HttpMethod.POST,
                new HttpEntity<>(buildUpdateRequest(context.blueprintName, false, null), jsonHeaders()),
                ErrorRes.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).contains("Root target routes differ");
        assertThat(response.getBody().getMessage()).contains("content-only");
        verify(mockGitOperation, never()).readRepository(any(), any(), any());
        deleteCreatedBlueprint(context.blueprintUuid);
    }

    /**
     * Scenario: parameterMapping change is applied from next
     * Given the same composition slot and keys
     * And next parameterMapping rewires $param or value entries
     * When update-data-product runs
     * Then module files render with the next mapping and the request succeeds
     */
    @Test
    void whenNextParameterMappingDiffersThenUpdateSucceeds(
            @TempDir Path sourceDir,
            @TempDir Path targetDir) throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        writeSafeDescriptor(sourceDir);

        ModuleBlueprint storage = createPublishedModule("odm-blueprint-s3-lake", "3.0.1");
        ModuleBlueprint serving = createPublishedModule("odm-blueprint-api-skeleton", "1.4.0");
        ObjectNode currentManifest = (ObjectNode) manifestMonorepoWithComposition();
        rewriteCompositionRefs(currentManifest, storage, serving);
        ObjectNode nextManifest = currentManifest.deepCopy();
        ObjectNode storageMapping = (ObjectNode) nextManifest.at("/composition/0/parameterMapping");
        storageMapping.putObject("region").put("$param", "projectSlug");

        BlueprintPair context = createBlueprintWithVersions(
                "full-stack-dp", "1.0.0", "2.0.0", currentManifest, nextManifest);
        stubUpdateHappyPath(List.of(sourceDir, sourceDir, sourceDir), targetDir);

        ResponseEntity<UpdateDataProductResultRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_UPDATE_DATA_PRODUCT),
                HttpMethod.POST,
                new HttpEntity<>(buildCompositionUpdateRequest(context.blueprintName, false), jsonHeaders()),
                UpdateDataProductResultRes.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getResults()).hasSize(1);

        deleteCreatedBlueprint(context.blueprintUuid);
        deleteCreatedBlueprint(storage.blueprintUuid());
        deleteCreatedBlueprint(serving.blueprintUuid());
    }

    /**
     * Scenario: Same-slot module blueprintVersion bump is allowed
     * Given composition alias and blueprintName and targets unchanged
     * And blueprintVersion points at a newer published 1→1 module
     * When update-data-product runs
     * Then the newer module sources are materialized at their release tag
     */
    @Test
    void whenSameSlotModuleVersionBumpsThenUpdateUsesNextModuleTag(
            @TempDir Path sourceDir,
            @TempDir Path targetDir) throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        writeSafeDescriptor(sourceDir);

        ModuleBlueprint storage = createPublishedModule("odm-blueprint-s3-lake", "3.0.1");
        publishModuleVersion(storage, "3.0.2");
        ModuleBlueprint serving = createPublishedModule("odm-blueprint-api-skeleton", "1.4.0");

        ObjectNode currentManifest = (ObjectNode) manifestMonorepoWithComposition();
        rewriteCompositionRefs(currentManifest, storage, serving);
        ObjectNode nextManifest = currentManifest.deepCopy();
        ((ObjectNode) nextManifest.at("/composition/0")).put("blueprintVersion", "3.0.2");
        rewriteCompositionRefs(nextManifest, storage.withVersion("3.0.2"), serving);

        BlueprintPair context = createBlueprintWithVersions(
                "full-stack-dp", "1.0.0", "2.0.0", currentManifest, nextManifest);

        GitOperation mockGitOperation = stubUpdateHappyPath(List.of(sourceDir, sourceDir, sourceDir), targetDir);

        ResponseEntity<UpdateDataProductResultRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_UPDATE_DATA_PRODUCT),
                HttpMethod.POST,
                new HttpEntity<>(buildCompositionUpdateRequest(context.blueprintName, false), jsonHeaders()),
                UpdateDataProductResultRes.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        ArgumentCaptor<RepositoryPointer> pointerCaptor = ArgumentCaptor.forClass(RepositoryPointer.class);
        verify(mockGitOperation, atLeastOnce()).readRepository(any(), pointerCaptor.capture(), any());
        assertThat(pointerCaptor.getAllValues().stream().map(RepositoryPointer::getRefValue))
                .contains("v3.0.2");

        deleteCreatedBlueprint(context.blueprintUuid);
        deleteCreatedBlueprint(storage.blueprintUuid());
        deleteCreatedBlueprint(serving.blueprintUuid());
    }

    /**
     * Scenario: Next structural problems are all reported with hints
     * Given a next manifest missing root.repository, with empty root.targets, unused keys, and overlapping destinations
     * When update-data-product is called
     * Then the 400 body lists every problem and a hint for each
     * And Git is not invoked
     */
    @Test
    void whenNextManifestHasMultipleStructuralProblemsThenListAllWithHints() throws Exception {
        JsonNode currentManifest = manifestMonorepoNoComposition();
        JsonNode nextManifest = readYamlManifestResource("manifest/invalid/multiple-structural-errors.yaml");

        BlueprintPair context = createBlueprintWithVersions("mesh-dp", "1.0.0", "2.0.0", currentManifest, nextManifest);

        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        GitOperation mockGitOperation = Mockito.mock(GitOperation.class);
        when(mockGitProvider.gitOperation()).thenReturn(mockGitOperation);

        ResponseEntity<ErrorRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_UPDATE_DATA_PRODUCT),
                HttpMethod.POST,
                new HttpEntity<>(buildUpdateRequest(context.blueprintName, false, null), jsonHeaders()),
                ErrorRes.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        String message = response.getBody().getMessage();
        assertThat(message).contains("Blueprint update validation failed");
        assertThat(message).contains("Hint:");
        assertThat(message.split("\n").length).isGreaterThanOrEqualTo(3);
        verify(mockGitOperation, never()).readRepository(any(), any(), any());
        deleteCreatedBlueprint(context.blueprintUuid);
    }

    /**
     * Scenario: Git failure stops later targets
     * Given two polyrepo remotes
     * When the first target Git operation fails after a valid request
     * Then later targets are not processed and the request fails
     */
    @Test
    void whenFirstTargetGitFailsThenDoNotProcessLaterTargets(
            @TempDir Path sourceDir,
            @TempDir Path infraTarget,
            @TempDir Path appTarget) throws Exception {
        writePolyrepoSourceFiles(sourceDir);
        JsonNode manifest = manifestPolyrepoNoComposition();
        BlueprintPair context = createBlueprintWithVersions("split-stack", "1.0.0", "2.0.0", manifest, manifest);

        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        GitOperation mockGitOperation = Mockito.mock(GitOperation.class);
        when(mockGitProvider.gitOperation()).thenReturn(mockGitOperation);
        when(mockGitProvider.getRepository(anyString(), anyString())).thenReturn(Optional.of(new Repository()));

        AtomicInteger targetIndex = new AtomicInteger(0);
        AtomicInteger sourceIndex = new AtomicInteger(0);
        doAnswer(invocation -> {
            RepositoryPointer pointer = invocation.getArgument(1);
            Consumer<File> consumer = invocation.getArgument(2);
            String ref = pointer.getRefValue();
            if (ref.startsWith("blueprint-v")) {
                sourceIndex.set(0);
                if (targetIndex.getAndIncrement() == 0) {
                    throw new GitOperationException("readRepository", "simulated git failure on first target");
                }
                consumer.accept(appTarget.toFile());
            } else {
                consumer.accept(sourceDir.toFile());
                sourceIndex.incrementAndGet();
            }
            return null;
        }).when(mockGitOperation).readRepository(any(), any(), any());

        ResponseEntity<ErrorRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_UPDATE_DATA_PRODUCT),
                HttpMethod.POST,
                new HttpEntity<>(buildPolyrepoUpdateRequest(context.blueprintName, false), jsonHeaders()),
                ErrorRes.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(mockGitOperation, times(2)).readRepository(any(), any(), any());
        verify(mockGitOperation, never()).pushBranch(any(), anyString());
        deleteCreatedBlueprint(context.blueprintUuid);
    }

    private GitOperation stubUpdateHappyPath(Path sourceDir, Path targetDir) {
        return stubUpdateHappyPath(List.of(sourceDir), targetDir);
    }

    private GitOperation stubUpdateHappyPath(Path sourceDir, Path targetDir, AtomicReference<Commit> commitRef) {
        return stubUpdateHappyPath(List.of(sourceDir), commitRef, targetDir);
    }

    private GitOperation stubUpdateHappyPath(List<Path> sourceDirsInCloneOrder, Path... targetDirs) {
        return stubUpdateHappyPath(sourceDirsInCloneOrder, null, targetDirs);
    }

    private GitOperation stubUpdateHappyPath(
            List<Path> sourceDirsInCloneOrder,
            AtomicReference<Commit> commitRef,
            Path... targetDirs) {
        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        GitOperation mockGitOperation = Mockito.mock(GitOperation.class);
        when(mockGitProvider.gitOperation()).thenReturn(mockGitOperation);
        when(mockGitProvider.getRepository(anyString(), anyString())).thenReturn(Optional.of(new Repository()));

        AtomicInteger targetIndex = new AtomicInteger(0);
        AtomicInteger sourceIndex = new AtomicInteger(0);
        doAnswer(invocation -> {
            RepositoryPointer pointer = invocation.getArgument(1);
            Consumer<File> consumer = invocation.getArgument(2);
            String ref = pointer.getRefValue();
            if (ref.startsWith("blueprint-v")) {
                sourceIndex.set(0);
                int idx = Math.min(targetIndex.getAndIncrement(), targetDirs.length - 1);
                consumer.accept(targetDirs[idx].toFile());
            } else {
                int idx = Math.min(sourceIndex.getAndIncrement(), sourceDirsInCloneOrder.size() - 1);
                consumer.accept(sourceDirsInCloneOrder.get(idx).toFile());
            }
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
        return buildTargetRepository("dp-repo", "dp-repo");
    }

    private RepositoryRes buildTargetRepository(String id, String name) {
        RepositoryRes repositoryRes = new RepositoryRes();
        repositoryRes.setId(id);
        repositoryRes.setName(name);
        repositoryRes.setDescription(name);
        repositoryRes.setCloneUrlHttp("https://github.com/org/" + name + ".git");
        repositoryRes.setCloneUrlSsh("git@github.com:org/" + name + ".git");
        repositoryRes.setDefaultBranch("main");
        repositoryRes.setOwnerId("org");
        return repositoryRes;
    }

    private UpdateDataProductCommandRes buildCompositionUpdateRequest(String blueprintName, boolean createPullRequest) {
        UpdateDataProductCommandRes request = new UpdateDataProductCommandRes();
        request.setBlueprintName(blueprintName);
        request.setCurrentVersionNumber("1.0.0");
        request.setNextVersionNumber("2.0.0");
        request.setCreatePullRequest(createPullRequest);

        UpdateDataProductTargetRepositoryRes target = new UpdateDataProductTargetRepositoryRes();
        target.setTargetId(OdmBlueprintManifestAutoFiller.DEFAULT_REPOSITORY_KEY);
        target.setRepository(buildTargetRepository());
        request.setTargetRepositories(List.of(target));

        Map<String, JsonNode> parameters = new LinkedHashMap<>();
        parameters.put("projectSlug", OBJECT_MAPPER.valueToTree("acme-lake"));
        parameters.put("enablePiiMasking", OBJECT_MAPPER.valueToTree(true));
        request.setParameters(parameters);
        return request;
    }

    private UpdateDataProductCommandRes buildPolyrepoUpdateRequest(String blueprintName, boolean createPullRequest) {
        UpdateDataProductCommandRes request = new UpdateDataProductCommandRes();
        request.setBlueprintName(blueprintName);
        request.setCurrentVersionNumber("1.0.0");
        request.setNextVersionNumber("2.0.0");
        request.setCreatePullRequest(createPullRequest);

        UpdateDataProductTargetRepositoryRes infra = new UpdateDataProductTargetRepositoryRes();
        infra.setTargetId("infra-repo");
        infra.setRepository(buildTargetRepository("infra-repository-id", "infra-repo"));
        UpdateDataProductTargetRepositoryRes app = new UpdateDataProductTargetRepositoryRes();
        app.setTargetId("app-repo");
        app.setRepository(buildTargetRepository("app-repository-id", "app-repo"));
        request.setTargetRepositories(List.of(infra, app));
        request.setParameters(Map.of("awsRegion", OBJECT_MAPPER.valueToTree("eu-west-1")));
        return request;
    }

    private UpdateDataProductCommandRes buildPolyrepoCompositionUpdateRequest(String blueprintName) {
        UpdateDataProductCommandRes request = new UpdateDataProductCommandRes();
        request.setBlueprintName(blueprintName);
        request.setCurrentVersionNumber("1.0.0");
        request.setNextVersionNumber("2.0.0");
        request.setCreatePullRequest(false);

        UpdateDataProductTargetRepositoryRes pipeline = new UpdateDataProductTargetRepositoryRes();
        pipeline.setTargetId("pipeline-repo");
        pipeline.setRepository(buildTargetRepository("pipeline-repository-id", "pipeline-repo"));
        UpdateDataProductTargetRepositoryRes api = new UpdateDataProductTargetRepositoryRes();
        api.setTargetId("api-repo");
        api.setRepository(buildTargetRepository("api-repository-id", "api-repo"));
        request.setTargetRepositories(List.of(pipeline, api));
        request.setParameters(Map.of("dataDomain", OBJECT_MAPPER.valueToTree("customer360")));
        return request;
    }

    private ModuleBlueprint createPublishedModule(String blueprintName, String version) throws Exception {
        return createPublishedModule(blueprintName, version, manifestMonorepoNoComposition(), buildBlueprintRepo());
    }

    private ModuleBlueprint createPublishedModule(
            String blueprintName,
            String version,
            JsonNode manifestContent,
            BlueprintRes.BlueprintRepoRes blueprintRepo) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String uniqueBlueprintName = blueprintName + "-" + suffix;
        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName(uniqueBlueprintName);
        blueprint.setDisplayName(uniqueBlueprintName + "-display");
        blueprint.setDescription(uniqueBlueprintName + "-description");
        blueprint.setBlueprintRepo(blueprintRepo);

        ResponseEntity<BlueprintRes> createdBlueprint = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                BlueprintRes.class);
        assertThat(createdBlueprint.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createdBlueprint.getBody()).isNotNull();

        createVersion(createdBlueprint.getBody(), uniqueBlueprintName, version, manifestContent);
        return new ModuleBlueprint(createdBlueprint.getBody().getUuid(), uniqueBlueprintName, version);
    }

    private void publishModuleVersion(ModuleBlueprint module, String version) throws Exception {
        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setUuid(module.blueprintUuid());
        blueprint.setName(module.blueprintName());
        createVersion(blueprint, module.blueprintName(), version, manifestMonorepoNoComposition());
    }

    private void rewriteCompositionRefs(ObjectNode parentManifest, ModuleBlueprint storage, ModuleBlueprint serving) {
        for (JsonNode node : parentManifest.get("composition")) {
            ObjectNode composition = (ObjectNode) node;
            if ("storage".equals(composition.get("module").asText())) {
                composition.put("blueprintName", storage.blueprintName());
                composition.put("blueprintVersion", storage.versionNumber());
            } else if ("serving".equals(composition.get("module").asText())) {
                composition.put("blueprintName", serving.blueprintName());
                composition.put("blueprintVersion", serving.versionNumber());
            }
        }
    }

    private void rewritePolyrepoCompositionRefs(ObjectNode parentManifest, ModuleBlueprint ingest, ModuleBlueprint consume) {
        for (JsonNode node : parentManifest.get("composition")) {
            ObjectNode composition = (ObjectNode) node;
            if ("ingest".equals(composition.get("module").asText())) {
                composition.put("blueprintName", ingest.blueprintName());
                composition.put("blueprintVersion", ingest.versionNumber());
            } else if ("consume".equals(composition.get("module").asText())) {
                composition.put("blueprintName", consume.blueprintName());
                composition.put("blueprintVersion", consume.versionNumber());
            }
        }
    }

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

    private void writePolyrepoSourceFiles(Path sourceDir) throws IOException {
        Files.createDirectories(sourceDir.resolve("terraform"));
        Files.writeString(sourceDir.resolve("terraform/main.tf"), "resource \"null\" \"x\" {}");
        Files.createDirectories(sourceDir.resolve("policies"));
        Files.writeString(sourceDir.resolve("policies/policy.rego"), "package policy");
        Files.createDirectories(sourceDir.resolve("application/templates"));
        Files.writeString(sourceDir.resolve("application/README.md"), "# app");
        Files.writeString(sourceDir.resolve("application/manifest.yaml"), "spec: odm-blueprint-manifest\n");
        Files.createDirectories(sourceDir.resolve("core/templates"));
        Files.writeString(sourceDir.resolve("core/templates/descriptor.json.vm"), """
                {
                  "dataProductDescriptor": "1.0.0",
                  "info": {
                    "name": "polyrepo-composition"
                  }
                }
                """);
        Files.createDirectories(sourceDir.resolve("templates"));
        Files.writeString(sourceDir.resolve("templates/descriptor.json.vm"), """
                {
                  "dataProductDescriptor": "1.0.0",
                  "info": {
                    "name": "polyrepo-product"
                  }
                }
                """);
    }

    private JsonNode manifestMonorepoWithComposition() throws Exception {
        return readYamlManifestResource("manifest/example-2.2-monorepo-composition.yaml");
    }

    private JsonNode manifestPolyrepoNoComposition() throws Exception {
        return readYamlManifestResource("manifest/example-2.3-polyrepo-no-composition.yaml");
    }

    private JsonNode manifestPolyrepoWithComposition() throws Exception {
        return readYamlManifestResource("manifest/example-2.4-polyrepo-composition.yaml");
    }

    private JsonNode readYamlManifestResource(String resourcePath) throws IOException {
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
            return YAML_OBJECT_MAPPER.readTree(inputStream);
        }
    }

    private BlueprintPair createBlueprintWithVersions(String blueprintName, String currentVersion, String nextVersion)
            throws Exception {
        return createBlueprintWithVersions(
                blueprintName, currentVersion, nextVersion,
                manifestMonorepoNoComposition(), manifestMonorepoNoComposition());
    }

    private BlueprintPair createBlueprintWithVersions(
            String blueprintName,
            String currentVersion,
            String nextVersion,
            JsonNode currentManifest,
            JsonNode nextManifest) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String uniqueBlueprintName = blueprintName + "-" + suffix;

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

        createVersion(createdBlueprint.getBody(), uniqueBlueprintName, currentVersion, currentManifest);
        createVersion(createdBlueprint.getBody(), uniqueBlueprintName, nextVersion, nextManifest);
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

    private record ModuleBlueprint(String blueprintUuid, String blueprintName, String versionNumber) {
        ModuleBlueprint withVersion(String version) {
            return new ModuleBlueprint(blueprintUuid, blueprintName, version);
        }
    }
}
