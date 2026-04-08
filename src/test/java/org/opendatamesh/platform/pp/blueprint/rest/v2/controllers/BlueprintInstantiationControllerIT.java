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
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.opendatamesh.platform.git.exceptions.GitOperationException;
import org.opendatamesh.platform.git.git.GitOperation;
import org.opendatamesh.platform.git.model.Commit;
import org.opendatamesh.platform.git.model.Repository;
import org.opendatamesh.platform.git.model.RepositoryPointer;
import org.opendatamesh.platform.git.model.RepositoryPointerBranch;
import org.opendatamesh.platform.git.model.RepositoryPointerTag;
import org.opendatamesh.platform.git.provider.GitProvider;
import org.opendatamesh.platform.pp.blueprint.rest.v2.BlueprintApplicationIT;
import org.opendatamesh.platform.pp.blueprint.rest.v2.RoutesV2;
import org.opendatamesh.platform.pp.blueprint.rest.v2.mocks.GitProviderFactoryMock;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintRepoOwnerTypeRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintRepoProviderTypeRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.BlueprintVersionRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.instantiate.InstantiateBlueprintVersionCommandRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.instantiate.InstantiateBlueprintVersionResponseRes;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate.BlueprintRepositoryLogicalType;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.instantiate.InstantiateBlueprintVersionTargetRepositoryRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.gitproviders.RepositoryRes;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests for blueprint instantiation endpoint.
 */
public class BlueprintInstantiationControllerIT extends BlueprintApplicationIT {

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

    /**
     * specs.md — Scenario: Successful monorepo population and push.
     */
    @Test
    void whenInstantiateMonorepoThenReturn200(@TempDir Path sourceDir, @TempDir Path targetDir) throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        BlueprintContext context = createBlueprintAndVersion("analytics-lakehouse", "1.2.0", manifestMonorepoNoComposition());
        GitOperation mockGitOperation = stubGitHappyPath(sourceDir, targetDir);

        ResponseEntity<InstantiateBlueprintVersionResponseRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_INSTANTIATE),
                HttpMethod.POST,
                new HttpEntity<>(buildInstantiateRequest(context.blueprintName, context.versionNumber, "prod", 365), jsonHeaders()),
                InstantiateBlueprintVersionResponseRes.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        // Git: first read clones the blueprint repo at the released version tag;
        //      second read clones the target repo at the branch we populate.
        ArgumentCaptor<RepositoryPointer> pointerCaptor = ArgumentCaptor.forClass(RepositoryPointer.class);
        verify(mockGitOperation, times(2)).readRepository(any(), pointerCaptor.capture(), any());
        List<RepositoryPointer> pointers = pointerCaptor.getAllValues();
        assertThat(pointers.get(0)).isInstanceOf(RepositoryPointerTag.class);
        assertThat(pointers.get(0).getRefValue()).isEqualTo("v" + context.versionNumber);
        assertThat(pointers.get(1)).isInstanceOf(RepositoryPointerBranch.class);
        assertThat(pointers.get(1).getRefValue()).isEqualTo("main");
        // Git: populate flow stages changes, commits with the expected message, then pushes (no force-push).
        InOrder gitOpOrder = Mockito.inOrder(mockGitOperation);
        ArgumentCaptor<Commit> commitCaptor = ArgumentCaptor.forClass(Commit.class);
        gitOpOrder.verify(mockGitOperation).addAll(eq(targetDir.toFile()));
        gitOpOrder.verify(mockGitOperation).commit(eq(targetDir.toFile()), commitCaptor.capture());
        gitOpOrder.verify(mockGitOperation).push(eq(targetDir.toFile()), eq(false));
        assertThat(commitCaptor.getValue().getMessage()).isEqualTo(
                "Populate repository from blueprint " + context.blueprintName + "@" + context.versionNumber);

        deleteCreatedBlueprint(context);
    }

    /**
     * specs.md — Scenario: Commit author can be customized with default fallback (provided identity).
     */
    @Test
    void whenCommitAuthorIsProvidedThenCommitUsesProvidedIdentity(@TempDir Path sourceDir, @TempDir Path targetDir) throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        BlueprintContext context = createBlueprintAndVersion("analytics-lakehouse", "1.2.0", manifestMonorepoNoComposition());

        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        GitOperation mockGitOperation = Mockito.mock(GitOperation.class);
        when(mockGitProvider.gitOperation()).thenReturn(mockGitOperation);
        when(mockGitProvider.getRepository(anyString(), anyString())).thenReturn(Optional.of(new Repository()));
        AtomicInteger callCounter = new AtomicInteger(0);
        AtomicReference<Commit> commitRef = new AtomicReference<>();
        doAnswer(invocation -> {
            Consumer<File> consumer = invocation.getArgument(2);
            consumer.accept(callCounter.getAndIncrement() == 0 ? sourceDir.toFile() : targetDir.toFile());
            return null;
        }).when(mockGitOperation).readRepository(any(), any(), any());
        doNothing().when(mockGitOperation).addAll(any());
        doAnswer(invocation -> {
            commitRef.set(invocation.getArgument(1));
            return null;
        }).when(mockGitOperation).commit(any(), any());
        doNothing().when(mockGitOperation).push(any(), anyBoolean());

        InstantiateBlueprintVersionCommandRes request =
                buildInstantiateRequest(context.blueprintName, context.versionNumber, "prod", 365, "Jane Doe", "jane.doe@example.org", null);

        ResponseEntity<InstantiateBlueprintVersionResponseRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_INSTANTIATE),
                HttpMethod.POST,
                new HttpEntity<>(request, jsonHeaders()),
                InstantiateBlueprintVersionResponseRes.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(commitRef.get()).isNotNull();
        assertThat(commitRef.get().getAuthor()).isEqualTo("Jane Doe");
        assertThat(commitRef.get().getAuthorEmail()).isEqualTo("jane.doe@example.org");
        // Git: same clone pointers as default flow;
        //      commit message still identifies blueprint version.
        ArgumentCaptor<RepositoryPointer> pointerCaptor = ArgumentCaptor.forClass(RepositoryPointer.class);
        verify(mockGitOperation, times(2)).readRepository(any(), pointerCaptor.capture(), any());
        List<RepositoryPointer> pointers = pointerCaptor.getAllValues();
        assertThat(pointers.get(0)).isInstanceOf(RepositoryPointerTag.class);
        assertThat(pointers.get(0).getRefValue()).isEqualTo("v" + context.versionNumber);
        assertThat(pointers.get(1)).isInstanceOf(RepositoryPointerBranch.class);
        assertThat(pointers.get(1).getRefValue()).isEqualTo("main");
        InOrder gitOpOrder = Mockito.inOrder(mockGitOperation);
        ArgumentCaptor<Commit> commitCaptor = ArgumentCaptor.forClass(Commit.class);
        gitOpOrder.verify(mockGitOperation).addAll(eq(targetDir.toFile()));
        gitOpOrder.verify(mockGitOperation).commit(eq(targetDir.toFile()), commitCaptor.capture());
        gitOpOrder.verify(mockGitOperation).push(eq(targetDir.toFile()), eq(false));
        assertThat(commitCaptor.getValue().getMessage()).isEqualTo(
                "Populate repository from blueprint " + context.blueprintName + "@" + context.versionNumber);
        deleteCreatedBlueprint(context);
    }

    /**
     * specs.md — Scenario: Commit author can be customized with default fallback (defaults when omitted).
     */
    @Test
    void whenCommitAuthorOmittedThenCommitUsesServerDefaults(@TempDir Path sourceDir, @TempDir Path targetDir) throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        BlueprintContext context = createBlueprintAndVersion("analytics-lakehouse", "1.2.0", manifestMonorepoNoComposition());

        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        GitOperation mockGitOperation = Mockito.mock(GitOperation.class);
        when(mockGitProvider.gitOperation()).thenReturn(mockGitOperation);
        when(mockGitProvider.getRepository(anyString(), anyString())).thenReturn(Optional.of(new Repository()));
        AtomicInteger callCounter = new AtomicInteger(0);
        AtomicReference<Commit> commitRef = new AtomicReference<>();
        doAnswer(invocation -> {
            Consumer<File> consumer = invocation.getArgument(2);
            consumer.accept(callCounter.getAndIncrement() == 0 ? sourceDir.toFile() : targetDir.toFile());
            return null;
        }).when(mockGitOperation).readRepository(any(), any(), any());
        doNothing().when(mockGitOperation).addAll(any());
        doAnswer(invocation -> {
            commitRef.set(invocation.getArgument(1));
            return null;
        }).when(mockGitOperation).commit(any(), any());
        doNothing().when(mockGitOperation).push(any(), anyBoolean());

        InstantiateBlueprintVersionCommandRes request =
                buildInstantiateRequest(context.blueprintName, context.versionNumber, "prod", 365, null, null, null);

        ResponseEntity<InstantiateBlueprintVersionResponseRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_INSTANTIATE),
                HttpMethod.POST,
                new HttpEntity<>(request, jsonHeaders()),
                InstantiateBlueprintVersionResponseRes.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(commitRef.get()).isNotNull();
        assertThat(commitRef.get().getAuthor()).isEqualTo("odm-blueprint-server");
        assertThat(commitRef.get().getAuthorEmail()).isEqualTo("odm-blueprint-server@local");
        // Git: clone pointers unchanged;
        //      only the commit identity differs from the explicit-author test.
        ArgumentCaptor<RepositoryPointer> pointerCaptor = ArgumentCaptor.forClass(RepositoryPointer.class);
        verify(mockGitOperation, times(2)).readRepository(any(), pointerCaptor.capture(), any());
        List<RepositoryPointer> pointers = pointerCaptor.getAllValues();
        assertThat(pointers.get(0)).isInstanceOf(RepositoryPointerTag.class);
        assertThat(pointers.get(0).getRefValue()).isEqualTo("v" + context.versionNumber);
        assertThat(pointers.get(1)).isInstanceOf(RepositoryPointerBranch.class);
        assertThat(pointers.get(1).getRefValue()).isEqualTo("main");
        InOrder gitOpOrder = Mockito.inOrder(mockGitOperation);
        ArgumentCaptor<Commit> commitCaptor = ArgumentCaptor.forClass(Commit.class);
        gitOpOrder.verify(mockGitOperation).addAll(eq(targetDir.toFile()));
        gitOpOrder.verify(mockGitOperation).commit(eq(targetDir.toFile()), commitCaptor.capture());
        gitOpOrder.verify(mockGitOperation).push(eq(targetDir.toFile()), eq(false));
        assertThat(commitCaptor.getValue().getMessage()).isEqualTo(
                "Populate repository from blueprint " + context.blueprintName + "@" + context.versionNumber);
        deleteCreatedBlueprint(context);
    }

    /**
     * specs.md — Scenario: Instantiation strategy is derived from manifest metadata.
     */
    @Test
    void whenInstantiateWithoutMethodFieldThenNotRejectedForMissingMethod(@TempDir Path sourceDir, @TempDir Path targetDir) throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        BlueprintContext context = createBlueprintAndVersion("analytics-lakehouse", "1.2.0", manifestMonorepoNoComposition());
        GitOperation mockGitOperation = stubGitHappyPath(sourceDir, targetDir);

        ResponseEntity<InstantiateBlueprintVersionResponseRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_INSTANTIATE),
                HttpMethod.POST,
                new HttpEntity<>(buildInstantiateRequest(context.blueprintName, context.versionNumber, "prod", 365), jsonHeaders()),
                InstantiateBlueprintVersionResponseRes.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Git: omitting manifest `method` does not block instantiation; clone and push behavior matches the happy path.
        ArgumentCaptor<RepositoryPointer> pointerCaptor = ArgumentCaptor.forClass(RepositoryPointer.class);
        verify(mockGitOperation, times(2)).readRepository(any(), pointerCaptor.capture(), any());
        List<RepositoryPointer> pointers = pointerCaptor.getAllValues();
        assertThat(pointers.get(0)).isInstanceOf(RepositoryPointerTag.class);
        assertThat(pointers.get(0).getRefValue()).isEqualTo("v" + context.versionNumber);
        assertThat(pointers.get(1)).isInstanceOf(RepositoryPointerBranch.class);
        assertThat(pointers.get(1).getRefValue()).isEqualTo("main");
        InOrder gitOpOrder = Mockito.inOrder(mockGitOperation);
        ArgumentCaptor<Commit> commitCaptor = ArgumentCaptor.forClass(Commit.class);
        gitOpOrder.verify(mockGitOperation).addAll(eq(targetDir.toFile()));
        gitOpOrder.verify(mockGitOperation).commit(eq(targetDir.toFile()), commitCaptor.capture());
        gitOpOrder.verify(mockGitOperation).push(eq(targetDir.toFile()), eq(false));
        assertThat(commitCaptor.getValue().getMessage()).isEqualTo(
                "Populate repository from blueprint " + context.blueprintName + "@" + context.versionNumber);
        deleteCreatedBlueprint(context);
    }

    /**
     * specs.md — Scenario: Target branch defaults to repository default.
     */
    @Test
    void whenTargetBranchOmittedThenGitUsesRepositoryDefaultBranch(@TempDir Path sourceDir, @TempDir Path targetDir) throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        BlueprintContext context = createBlueprintAndVersion("analytics-lakehouse", "1.2.0", manifestMonorepoNoComposition());

        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        GitOperation mockGitOperation = Mockito.mock(GitOperation.class);
        when(mockGitProvider.gitOperation()).thenReturn(mockGitOperation);
        when(mockGitProvider.getRepository(anyString(), anyString())).thenReturn(Optional.of(new Repository()));
        AtomicInteger callCounter = new AtomicInteger(0);
        doAnswer(invocation -> {
            Consumer<File> consumer = invocation.getArgument(2);
            consumer.accept(callCounter.getAndIncrement() == 0 ? sourceDir.toFile() : targetDir.toFile());
            return null;
        }).when(mockGitOperation).readRepository(any(), any(), any());
        doNothing().when(mockGitOperation).addAll(any());
        doNothing().when(mockGitOperation).commit(any(), any());
        doNothing().when(mockGitOperation).push(any(), anyBoolean());

        rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_INSTANTIATE),
                HttpMethod.POST,
                new HttpEntity<>(buildInstantiateRequest(context.blueprintName, context.versionNumber, "prod", 365, null, null, null), jsonHeaders()),
                InstantiateBlueprintVersionResponseRes.class
        );

        // Git: no explicit target branch in request → second read uses repository default branch (main).
        ArgumentCaptor<RepositoryPointer> pointerCaptor = ArgumentCaptor.forClass(RepositoryPointer.class);
        verify(mockGitOperation, times(2)).readRepository(any(), pointerCaptor.capture(), any());
        List<RepositoryPointer> pointers = pointerCaptor.getAllValues();
        assertThat(pointers.get(0)).isInstanceOf(RepositoryPointerTag.class);
        assertThat(pointers.get(0).getRefValue()).isEqualTo("v" + context.versionNumber);
        assertThat(pointers.get(1)).isInstanceOf(RepositoryPointerBranch.class);
        assertThat(pointers.get(1).getRefValue()).isEqualTo("main");
        InOrder gitOpOrder = Mockito.inOrder(mockGitOperation);
        ArgumentCaptor<Commit> commitCaptor = ArgumentCaptor.forClass(Commit.class);
        gitOpOrder.verify(mockGitOperation).addAll(eq(targetDir.toFile()));
        gitOpOrder.verify(mockGitOperation).commit(eq(targetDir.toFile()), commitCaptor.capture());
        gitOpOrder.verify(mockGitOperation).push(eq(targetDir.toFile()), eq(false));
        assertThat(commitCaptor.getValue().getMessage()).isEqualTo(
                "Populate repository from blueprint " + context.blueprintName + "@" + context.versionNumber);

        deleteCreatedBlueprint(context);
    }

    /**
     * specs.md — Scenario: Target branch can be overridden.
     */
    @Test
    void whenTargetBranchSetThenGitUsesThatBranch(@TempDir Path sourceDir, @TempDir Path targetDir) throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        BlueprintContext context = createBlueprintAndVersion("analytics-lakehouse", "1.2.0", manifestMonorepoNoComposition());

        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        GitOperation mockGitOperation = Mockito.mock(GitOperation.class);
        when(mockGitProvider.gitOperation()).thenReturn(mockGitOperation);
        when(mockGitProvider.getRepository(anyString(), anyString())).thenReturn(Optional.of(new Repository()));
        AtomicInteger callCounter = new AtomicInteger(0);
        doAnswer(invocation -> {
            Consumer<File> consumer = invocation.getArgument(2);
            consumer.accept(callCounter.getAndIncrement() == 0 ? sourceDir.toFile() : targetDir.toFile());
            return null;
        }).when(mockGitOperation).readRepository(any(), any(), any());
        doNothing().when(mockGitOperation).addAll(any());
        doNothing().when(mockGitOperation).commit(any(), any());
        doNothing().when(mockGitOperation).push(any(), anyBoolean());

        InstantiateBlueprintVersionCommandRes request =
                buildInstantiateRequest(context.blueprintName, context.versionNumber, "prod", 365, null, null, "feature/custom");

        rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_INSTANTIATE),
                HttpMethod.POST,
                new HttpEntity<>(request, jsonHeaders()),
                InstantiateBlueprintVersionResponseRes.class
        );

        // Git: second read uses the requested branch instead of the repository default.
        ArgumentCaptor<RepositoryPointer> pointerCaptor = ArgumentCaptor.forClass(RepositoryPointer.class);
        verify(mockGitOperation, times(2)).readRepository(any(), pointerCaptor.capture(), any());
        List<RepositoryPointer> pointers = pointerCaptor.getAllValues();
        assertThat(pointers.get(0)).isInstanceOf(RepositoryPointerTag.class);
        assertThat(pointers.get(0).getRefValue()).isEqualTo("v" + context.versionNumber);
        assertThat(pointers.get(1)).isInstanceOf(RepositoryPointerBranch.class);
        assertThat(pointers.get(1).getRefValue()).isEqualTo("feature/custom");
        InOrder gitOpOrder = Mockito.inOrder(mockGitOperation);
        ArgumentCaptor<Commit> commitCaptor = ArgumentCaptor.forClass(Commit.class);
        gitOpOrder.verify(mockGitOperation).addAll(eq(targetDir.toFile()));
        gitOpOrder.verify(mockGitOperation).commit(eq(targetDir.toFile()), commitCaptor.capture());
        gitOpOrder.verify(mockGitOperation).push(eq(targetDir.toFile()), eq(false));
        assertThat(commitCaptor.getValue().getMessage()).isEqualTo(
                "Populate repository from blueprint " + context.blueprintName + "@" + context.versionNumber);

        deleteCreatedBlueprint(context);
    }

    /**
     * specs.md — Scenario: Exactly one root target is required in this phase (empty list).
     */
    @Test
    void whenNoTargetRepositoriesThenReturn400() throws Exception {
        BlueprintContext context = createBlueprintAndVersion("analytics-lakehouse", "1.2.0", manifestMonorepoNoComposition());

        InstantiateBlueprintVersionCommandRes request = buildInstantiateRequest(context.blueprintName, context.versionNumber, "prod", 365);
        request.setTargetRepositories(List.of());

        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_INSTANTIATE),
                HttpMethod.POST,
                new HttpEntity<>(request, jsonHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        deleteCreatedBlueprint(context);
    }

    /**
     * specs.md — Scenario: Exactly one root target is required in this phase (more than one).
     */
    @Test
    void whenMoreThanOneTargetRepositoryThenReturn400(@TempDir Path sourceDir, @TempDir Path targetDir) throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        BlueprintContext context = createBlueprintAndVersion("analytics-lakehouse", "1.2.0", manifestMonorepoNoComposition());
        stubGitHappyPath(sourceDir, targetDir);

        InstantiateBlueprintVersionCommandRes request = buildInstantiateRequest(context.blueprintName, context.versionNumber, "prod", 365);
        InstantiateBlueprintVersionTargetRepositoryRes t1 = new InstantiateBlueprintVersionTargetRepositoryRes();
        t1.setType(BlueprintRepositoryLogicalType.ROOT);
        t1.setRepository(buildTargetRepository());
        InstantiateBlueprintVersionTargetRepositoryRes t2 = new InstantiateBlueprintVersionTargetRepositoryRes();
        t2.setType(BlueprintRepositoryLogicalType.ROOT);
        t2.setRepository(buildTargetRepository());
        request.setTargetRepositories(List.of(t1, t2));

        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_INSTANTIATE),
                HttpMethod.POST,
                new HttpEntity<>(request, jsonHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        deleteCreatedBlueprint(context);
    }

    /**
     * specs.md — Scenario: Exactly one root target is required in this phase (wrong type).
     */
    @Test
    void whenTargetTypeNotRootThenReturn400(@TempDir Path sourceDir, @TempDir Path targetDir) throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        BlueprintContext context = createBlueprintAndVersion("analytics-lakehouse", "1.2.0", manifestMonorepoNoComposition());
        stubGitHappyPath(sourceDir, targetDir);

        InstantiateBlueprintVersionCommandRes request = buildInstantiateRequest(context.blueprintName, context.versionNumber, "prod", 365);
        ObjectNode requestBody = OBJECT_MAPPER.valueToTree(request);
        ((ObjectNode) requestBody.get("targetRepositories").get(0)).put("type", "apps");

        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_INSTANTIATE),
                HttpMethod.POST,
                new HttpEntity<>(requestBody.toString(), jsonHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        deleteCreatedBlueprint(context);
    }

    /**
     * specs.md — Scenario: Missing required parameters are rejected.
     */
    @Test
    void whenRequiredParameterMissingThenReturn400() throws Exception {
        BlueprintContext context = createBlueprintAndVersion("analytics-lakehouse", "1.2.0", manifestMonorepoNoComposition());

        InstantiateBlueprintVersionCommandRes request = buildInstantiateRequest(context.blueprintName, context.versionNumber, null, 365);
        request.getParameters().remove("environment");
        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_INSTANTIATE),
                HttpMethod.POST,
                new HttpEntity<>(request, jsonHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        deleteCreatedBlueprint(context);
    }

    /**
     * specs.md — Scenario: Invalid parameter types or constraints are rejected.
     */
    @Test
    void whenParameterTypeOrConstraintInvalidThenReturn400() throws Exception {
        BlueprintContext context = createBlueprintAndVersion("analytics-lakehouse", "1.2.0", manifestMonorepoNoComposition());

        InstantiateBlueprintVersionCommandRes request = buildInstantiateRequest(context.blueprintName, context.versionNumber, "invalid-env", -1);
        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_INSTANTIATE),
                HttpMethod.POST,
                new HttpEntity<>(request, jsonHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        deleteCreatedBlueprint(context);
    }

    /**
     * specs.md — Scenario: Unsupported composition manifests are rejected.
     */
    @Test
    void whenManifestContainsCompositionThenReturn400() throws Exception {
        BlueprintContext context = createBlueprintAndVersion("full-stack-dp", "2.1.0", manifestMonorepoWithComposition());

        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_INSTANTIATE),
                HttpMethod.POST,
                new HttpEntity<>(buildInstantiateRequest(context.blueprintName, context.versionNumber, "prod", 365), jsonHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        deleteCreatedBlueprint(context);
    }

    /**
     * specs.md — Scenario: Unsupported non-monorepo strategy is rejected.
     */
    @Test
    void whenManifestIsPolyrepoThenReturn400() throws Exception {
        BlueprintContext context = createBlueprintAndVersion("split-stack-template", "0.5.0", manifestPolyrepoNoComposition());

        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_INSTANTIATE),
                HttpMethod.POST,
                new HttpEntity<>(buildInstantiateRequest(context.blueprintName, context.versionNumber, "eu-west-1", 365), jsonHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        deleteCreatedBlueprint(context);
    }

    /**
     * specs.md — Scenario: Git operation failures are surfaced as client or server errors per global handling.
     */
    @Test
    void whenGitPushFailsThenResponseReflectsGlobalExceptionHandling(@TempDir Path sourceDir, @TempDir Path targetDir) throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        BlueprintContext context = createBlueprintAndVersion("analytics-lakehouse", "1.2.0", manifestMonorepoNoComposition());

        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        GitOperation mockGitOperation = Mockito.mock(GitOperation.class);
        when(mockGitProvider.gitOperation()).thenReturn(mockGitOperation);
        when(mockGitProvider.getRepository(anyString(), anyString())).thenReturn(Optional.of(new Repository()));
        AtomicInteger callCounter = new AtomicInteger(0);
        doAnswer(invocation -> {
            Consumer<File> consumer = invocation.getArgument(2);
            consumer.accept(callCounter.getAndIncrement() == 0 ? sourceDir.toFile() : targetDir.toFile());
            return null;
        }).when(mockGitOperation).readRepository(any(), any(), any());
        doNothing().when(mockGitOperation).addAll(any());
        doNothing().when(mockGitOperation).commit(any(), any());
        doThrow(new GitOperationException("push failed")).when(mockGitOperation).push(any(), anyBoolean());

        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_INSTANTIATE),
                HttpMethod.POST,
                new HttpEntity<>(buildInstantiateRequest(context.blueprintName, context.versionNumber, "prod", 365), jsonHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // Git: clone source and target, stage and commit succeed; push fails and surfaces as client error.
        verify(mockGitOperation, times(2)).readRepository(any(), any(), any());
        verify(mockGitOperation).addAll(any());
        verify(mockGitOperation).commit(any(), any());
        verify(mockGitOperation).push(eq(targetDir.toFile()), eq(false));
        deleteCreatedBlueprint(context);
    }

    /**
     * specs.md — Scenario: Blueprint README declared in repository metadata is not left at repository root.
     */
    @Test
    void whenPopulateThenReadmeIsRelocatedUnderDotOdmBlueprint(@TempDir Path sourceDir, @TempDir Path targetDir) throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        BlueprintContext context = createBlueprintAndVersion("analytics-lakehouse", "1.2.0", manifestMonorepoNoComposition());
        GitOperation mockGitOperation = stubGitHappyPath(sourceDir, targetDir);

        ResponseEntity<InstantiateBlueprintVersionResponseRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_INSTANTIATE),
                HttpMethod.POST,
                new HttpEntity<>(buildInstantiateRequest(context.blueprintName, context.versionNumber, "prod", 365), jsonHeaders()),
                InstantiateBlueprintVersionResponseRes.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Files.exists(targetDir.resolve("README.md"))).isFalse();
        assertThat(Files.exists(targetDir.resolve(".odm/blueprint/README.md"))).isTrue();
        assertThat(Files.exists(targetDir.resolve("manifest.yaml"))).isFalse();
        assertThat(Files.exists(targetDir.resolve(".odm/blueprint/blueprint-manifest.yaml"))).isTrue();
        // Git: standard tag/branch reads and populate push;
        //      file layout above asserts README and manifest.yaml were relocated under .odm/blueprint.
        ArgumentCaptor<RepositoryPointer> pointerCaptor = ArgumentCaptor.forClass(RepositoryPointer.class);
        verify(mockGitOperation, times(2)).readRepository(any(), pointerCaptor.capture(), any());
        List<RepositoryPointer> pointers = pointerCaptor.getAllValues();
        assertThat(pointers.get(0)).isInstanceOf(RepositoryPointerTag.class);
        assertThat(pointers.get(0).getRefValue()).isEqualTo("v" + context.versionNumber);
        assertThat(pointers.get(1)).isInstanceOf(RepositoryPointerBranch.class);
        assertThat(pointers.get(1).getRefValue()).isEqualTo("main");
        InOrder gitOpOrder = Mockito.inOrder(mockGitOperation);
        ArgumentCaptor<Commit> commitCaptor = ArgumentCaptor.forClass(Commit.class);
        gitOpOrder.verify(mockGitOperation).addAll(eq(targetDir.toFile()));
        gitOpOrder.verify(mockGitOperation).commit(eq(targetDir.toFile()), commitCaptor.capture());
        gitOpOrder.verify(mockGitOperation).push(eq(targetDir.toFile()), eq(false));
        assertThat(commitCaptor.getValue().getMessage()).isEqualTo(
                "Populate repository from blueprint " + context.blueprintName + "@" + context.versionNumber);

        deleteCreatedBlueprint(context);
    }

    /**
     * specs.md — Scenario: Manifest lineage snapshot is persisted under `.odm/blueprint/`.
     */
    @Test
    void whenPopulateThenManifestSnapshotIsWrittenUnderDotOdmBlueprint(@TempDir Path sourceDir, @TempDir Path targetDir) throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        BlueprintContext context = createBlueprintAndVersion("analytics-lakehouse", "1.2.0", manifestMonorepoNoComposition());
        GitOperation mockGitOperation = stubGitHappyPath(sourceDir, targetDir);

        ResponseEntity<InstantiateBlueprintVersionResponseRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_INSTANTIATE),
                HttpMethod.POST,
                new HttpEntity<>(buildInstantiateRequest(context.blueprintName, context.versionNumber, "prod", 365), jsonHeaders()),
                InstantiateBlueprintVersionResponseRes.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Files.exists(targetDir.resolve("manifest.yaml"))).isFalse();
        Path lineagePath = targetDir.resolve(".odm/blueprint/blueprint-manifest.yaml");
        assertThat(Files.exists(lineagePath)).isTrue();
        // Git: same clone/push shape as other populate tests;
        //      source manifest.yaml is removed from root; snapshot is written as blueprint-manifest.yaml under .odm/blueprint/.
        ArgumentCaptor<RepositoryPointer> pointerCaptor = ArgumentCaptor.forClass(RepositoryPointer.class);
        verify(mockGitOperation, times(2)).readRepository(any(), pointerCaptor.capture(), any());
        List<RepositoryPointer> pointers = pointerCaptor.getAllValues();
        assertThat(pointers.get(0)).isInstanceOf(RepositoryPointerTag.class);
        assertThat(pointers.get(0).getRefValue()).isEqualTo("v" + context.versionNumber);
        assertThat(pointers.get(1)).isInstanceOf(RepositoryPointerBranch.class);
        assertThat(pointers.get(1).getRefValue()).isEqualTo("main");
        InOrder gitOpOrder = Mockito.inOrder(mockGitOperation);
        ArgumentCaptor<Commit> commitCaptor = ArgumentCaptor.forClass(Commit.class);
        gitOpOrder.verify(mockGitOperation).addAll(eq(targetDir.toFile()));
        gitOpOrder.verify(mockGitOperation).commit(eq(targetDir.toFile()), commitCaptor.capture());
        gitOpOrder.verify(mockGitOperation).push(eq(targetDir.toFile()), eq(false));
        assertThat(commitCaptor.getValue().getMessage()).isEqualTo(
                "Populate repository from blueprint " + context.blueprintName + "@" + context.versionNumber);

        deleteCreatedBlueprint(context);
    }


    private GitOperation stubGitHappyPath(Path sourceDir, Path targetDir) {
        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        GitOperation mockGitOperation = Mockito.mock(GitOperation.class);
        when(mockGitProvider.gitOperation()).thenReturn(mockGitOperation);
        when(mockGitProvider.getRepository(anyString(), anyString())).thenReturn(Optional.of(new Repository()));

        AtomicInteger callCounter = new AtomicInteger(0);
        doAnswer(invocation -> {
            Consumer<File> consumer = invocation.getArgument(2);
            consumer.accept(callCounter.getAndIncrement() == 0 ? sourceDir.toFile() : targetDir.toFile());
            return null;
        }).when(mockGitOperation).readRepository(any(), any(), any());

        doNothing().when(mockGitOperation).addAll(any());
        doNothing().when(mockGitOperation).commit(any(), any());
        doNothing().when(mockGitOperation).push(any(), anyBoolean());
        return mockGitOperation;
    }

    private void writeSourceBlueprintFiles(Path sourceDir) throws IOException {
        for (String resourcePath : SOURCE_REPO_RESOURCE_FILES) {
            String relativePath = resourcePath.replaceFirst("^instantiate/source-repo/", "");
            Path destination = sourceDir.resolve(relativePath);
            if (destination.getParent() != null) {
                Files.createDirectories(destination.getParent());
            }
            copyResourceToPath(resourcePath, destination);
        }
    }

    private BlueprintContext createBlueprintAndVersion(String blueprintName, String version, JsonNode manifestContent) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String uniqueBlueprintName = blueprintName + "-" + suffix;
        ObjectNode content = (ObjectNode) manifestContent.deepCopy();
        content.put("name", uniqueBlueprintName);
        content.put("version", version);
        String prefix = "instantiate-" + version.replace(".", "-") + "-" + suffix;
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
        assertThat(createdVersion.getBody()).isNotNull();
        return new BlueprintContext(createdBlueprint.getBody().getUuid(), uniqueBlueprintName, version);
    }

    private BlueprintRes.BlueprintRepoRes buildBlueprintRepo() {
        BlueprintRes.BlueprintRepoRes blueprintRepo = new BlueprintRes.BlueprintRepoRes();
        blueprintRepo.setExternalIdentifier("source-blueprint-repository");
        blueprintRepo.setName("source-blueprint-repository");
        blueprintRepo.setDescription("source");
        blueprintRepo.setManifestRootPath("/manifest.yaml");
        blueprintRepo.setDescriptorTemplatePath("/templates");
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

    private InstantiateBlueprintVersionCommandRes buildInstantiateRequest(
            String blueprintName,
            String blueprintVersion,
            String environment,
            Integer retentionDays
    ) {
        return buildInstantiateRequest(blueprintName, blueprintVersion, environment, retentionDays, null, null, null);
    }

    private InstantiateBlueprintVersionCommandRes buildInstantiateRequest(
            String blueprintName,
            String blueprintVersion,
            String environment,
            Integer retentionDays,
            String commitAuthorName,
            String commitAuthorEmail,
            String targetBranch
    ) {
        InstantiateBlueprintVersionCommandRes request = new InstantiateBlueprintVersionCommandRes();
        request.setBlueprintName(blueprintName);
        request.setBlueprintVersionNumber(blueprintVersion);

        InstantiateBlueprintVersionTargetRepositoryRes target = new InstantiateBlueprintVersionTargetRepositoryRes();
        target.setType(BlueprintRepositoryLogicalType.ROOT);
        if (targetBranch != null) {
            target.setBranch(targetBranch);
        }
        target.setRepository(buildTargetRepository());
        request.setTargetRepositories(List.of(target));

        Map<String, JsonNode> parameters = new LinkedHashMap<>();
        if (environment != null) {
            parameters.put("environment", OBJECT_MAPPER.valueToTree(environment));
        }
        if (retentionDays != null) {
            parameters.put("retentionDays", OBJECT_MAPPER.valueToTree(retentionDays));
        }
        request.setParameters(parameters);
        request.setCommitAuthorName(commitAuthorName);
        request.setCommitAuthorEmail(commitAuthorEmail);
        return request;
    }

    private RepositoryRes buildTargetRepository() {
        RepositoryRes repositoryRes = new RepositoryRes();
        repositoryRes.setId("target-repository-id");
        repositoryRes.setName("customer360");
        repositoryRes.setDescription("Customer 360 monorepo");
        repositoryRes.setCloneUrlHttp("https://github.com/org/customer360.git");
        repositoryRes.setCloneUrlSsh("git@github.com:org/customer360.git");
        repositoryRes.setDefaultBranch("main");
        repositoryRes.setOwnerId("org");
        return repositoryRes;
    }

    private JsonNode manifestMonorepoNoComposition() throws Exception {
        return readYamlManifestResource("manifest/example-2.1-monorepo-no-composition.yaml");
    }

    private JsonNode manifestMonorepoWithComposition() throws Exception {
        return readYamlManifestResource("manifest/example-2.2-monorepo-composition.yaml");
    }

    private JsonNode manifestPolyrepoNoComposition() throws Exception {
        return readYamlManifestResource("manifest/example-2.3-polyrepo-no-composition.yaml");
    }


    private JsonNode readYamlManifestResource(String resourcePath) throws IOException {
        try (InputStream inputStream = getResourceAsStream(resourcePath)) {
            return YAML_OBJECT_MAPPER.readTree(inputStream);
        }
    }

    private void copyResourceToPath(String resourcePath, Path destinationPath) throws IOException {
        try (InputStream inputStream = getResourceAsStream(resourcePath)) {
            Files.copy(inputStream, destinationPath, StandardCopyOption.REPLACE_EXISTING);
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
        headers.set("x-odm-gpauth-type", "PAT");
        headers.set("x-odm-gpauth-param-token", "test-token");
        headers.set("x-odm-gpauth-param-username", "test-user");
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
