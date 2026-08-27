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
import org.opendatamesh.dpds.model.DataProductVersion;
import org.opendatamesh.dpds.parser.Parser;
import org.opendatamesh.dpds.parser.ParserFactory;
import org.opendatamesh.platform.git.exceptions.GitOperationException;
import org.opendatamesh.platform.git.git.GitOperation;
import org.opendatamesh.platform.git.model.*;
import org.opendatamesh.platform.git.provider.GitProvider;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.manifestautofiller.OdmBlueprintManifestAutoFiller;
import org.opendatamesh.platform.pp.blueprint.rest.v2.BlueprintApplicationIT;
import org.opendatamesh.platform.pp.blueprint.rest.v2.RoutesV2;
import org.opendatamesh.platform.pp.blueprint.rest.v2.mocks.GitProviderFactoryMock;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintRepoOwnerTypeRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintRepoProviderTypeRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.BlueprintVersionRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.instantiate.InstantiateBlueprintVersionCommandRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.instantiate.InstantiateBlueprintVersionResponseRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.instantiate.InstantiateBlueprintVersionTargetRepositoryRes;
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
            "instantiate/source-repo/scripts/bootstrap.sh");

    @Autowired
    private GitProviderFactoryMock gitProviderFactoryMock;

    @BeforeEach
    @AfterEach
    void resetGitMocks() {
        gitProviderFactoryMock.reset();
    }

    /**
     * Spec — Scenario: Successful monorepo population and push.
     */
    @Test
    void whenInstantiateMonorepoThenReturn200(@TempDir Path sourceDir, @TempDir Path targetDir) throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        BlueprintContext context = createBlueprintAndVersion("analytics-lakehouse", "1.2.0",
                manifestMonorepoNoComposition());
        GitOperation mockGitOperation = stubGitHappyPath(sourceDir, targetDir);

        ResponseEntity<InstantiateBlueprintVersionResponseRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_INSTANTIATE),
                HttpMethod.POST,
                new HttpEntity<>(buildInstantiateRequest(context.blueprintName, context.versionNumber, "prod", 365),
                        jsonHeaders()),
                InstantiateBlueprintVersionResponseRes.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        // Git: first read clones the blueprint repo at the released version tag;
        // second read clones the target repo at the branch we populate.
        ArgumentCaptor<RepositoryPointer> pointerCaptor = ArgumentCaptor.forClass(RepositoryPointer.class);
        verify(mockGitOperation, times(2)).readRepository(any(), pointerCaptor.capture(), any());
        List<RepositoryPointer> pointers = pointerCaptor.getAllValues();
        assertThat(pointers.get(0)).isInstanceOf(RepositoryPointerBranch.class);
        assertThat(pointers.get(0).getRefValue()).isEqualTo("main");
        assertThat(pointers.get(1)).isInstanceOf(RepositoryPointerTag.class);
        assertThat(pointers.get(1).getRefValue()).isEqualTo("v" + context.versionNumber);
        // Git: populate flow stages changes, commits with the expected message, then
        // pushes (no force-push).
        InOrder gitOpOrder = Mockito.inOrder(mockGitOperation);
        ArgumentCaptor<Commit> commitCaptor = ArgumentCaptor.forClass(Commit.class);
        gitOpOrder.verify(mockGitOperation).createAndCheckoutOrphanBranch(eq(targetDir.toFile()), anyString());
        gitOpOrder.verify(mockGitOperation).addAll(eq(targetDir.toFile()));
        gitOpOrder.verify(mockGitOperation).commit(eq(targetDir.toFile()), commitCaptor.capture());
        gitOpOrder.verify(mockGitOperation).addTag(eq(targetDir.toFile()), any(Tag.class));
        gitOpOrder.verify(mockGitOperation).mergeBranch(eq(targetDir.toFile()), anyString(), anyString());
        gitOpOrder.verify(mockGitOperation).pushBranch(eq(targetDir.toFile()), anyString());
        gitOpOrder.verify(mockGitOperation).pushTag(eq(targetDir.toFile()), anyString());
        assertThat(commitCaptor.getValue().getMessage()).isEqualTo(
                "Populate repository from blueprint " + context.blueprintName + "@" + context.versionNumber);

        Path descriptorOnTarget = targetDir.resolve("templates/descriptor.json");
        assertThat(Files.isRegularFile(descriptorOnTarget)).isTrue();
        JsonNode descriptorRoot = OBJECT_MAPPER.readTree(descriptorOnTarget.toFile());
        Parser dpdsParser = ParserFactory.getParser();
        DataProductVersion parsedDescriptor = dpdsParser.deserialize(descriptorRoot);
        assertThat(parsedDescriptor.getblueprint()).isNotNull();
        assertThat(parsedDescriptor.getblueprint().getBlueprintVersionUuid()).isEqualTo(context.blueprintVersionUuid);
        assertThat(parsedDescriptor.getblueprint().getBlueprintUuid()).isEqualTo(context.blueprintUuid);

        deleteCreatedBlueprint(context);
    }

    /**
     * Spec — Scenario: Commit author can be customized with default fallback
     * (provided identity).
     */
    @Test
    void whenCommitAuthorIsProvidedThenCommitUsesProvidedIdentity(@TempDir Path sourceDir, @TempDir Path targetDir)
            throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        BlueprintContext context = createBlueprintAndVersion("analytics-lakehouse", "1.2.0",
                manifestMonorepoNoComposition());

        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        GitOperation mockGitOperation = Mockito.mock(GitOperation.class);
        when(mockGitProvider.gitOperation()).thenReturn(mockGitOperation);
        when(mockGitProvider.getRepository(anyString(), anyString())).thenReturn(Optional.of(new Repository()));
        AtomicInteger callCounter = new AtomicInteger(0);
        AtomicReference<Commit> commitRef = new AtomicReference<>();
        doAnswer(invocation -> {
            Consumer<File> consumer = invocation.getArgument(2);
            consumer.accept(callCounter.getAndIncrement() == 0 ? targetDir.toFile() : sourceDir.toFile());
            return null;
        }).when(mockGitOperation).readRepository(any(), any(), any());
        doNothing().when(mockGitOperation).createAndCheckoutOrphanBranch(any(), anyString());
        doNothing().when(mockGitOperation).addAll(any());
        doAnswer(invocation -> {
            commitRef.set(invocation.getArgument(1));
            return null;
        }).when(mockGitOperation).commit(any(), any());
        when(mockGitOperation.getHeadSha(any(), anyString())).thenReturn("deadbeefcafebabe");
        doNothing().when(mockGitOperation).addTag(any(), any(Tag.class));
        when(mockGitOperation.mergeBranch(any(), anyString(), anyString())).thenReturn("deadbeefcafebabe");
        doNothing().when(mockGitOperation).pushBranch(any(), anyString());
        doNothing().when(mockGitOperation).pushTag(any(), anyString());

        InstantiateBlueprintVersionCommandRes request = buildInstantiateRequest(context.blueprintName,
                context.versionNumber, "prod", 365, "Jane Doe", "jane.doe@example.org", null);

        ResponseEntity<InstantiateBlueprintVersionResponseRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_INSTANTIATE),
                HttpMethod.POST,
                new HttpEntity<>(request, jsonHeaders()),
                InstantiateBlueprintVersionResponseRes.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(commitRef.get()).isNotNull();
        assertThat(commitRef.get().getAuthor()).isEqualTo("Jane Doe");
        assertThat(commitRef.get().getAuthorEmail()).isEqualTo("jane.doe@example.org");
        // Git: same clone pointers as default flow;
        // commit message still identifies blueprint version.
        ArgumentCaptor<RepositoryPointer> pointerCaptor = ArgumentCaptor.forClass(RepositoryPointer.class);
        verify(mockGitOperation, times(2)).readRepository(any(), pointerCaptor.capture(), any());
        List<RepositoryPointer> pointers = pointerCaptor.getAllValues();
        assertThat(pointers.get(0)).isInstanceOf(RepositoryPointerBranch.class);
        assertThat(pointers.get(0).getRefValue()).isEqualTo("main");
        assertThat(pointers.get(1)).isInstanceOf(RepositoryPointerTag.class);
        assertThat(pointers.get(1).getRefValue()).isEqualTo("v" + context.versionNumber);
        InOrder gitOpOrder = Mockito.inOrder(mockGitOperation);
        ArgumentCaptor<Commit> commitCaptor = ArgumentCaptor.forClass(Commit.class);
        gitOpOrder.verify(mockGitOperation).createAndCheckoutOrphanBranch(eq(targetDir.toFile()), anyString());
        gitOpOrder.verify(mockGitOperation).addAll(eq(targetDir.toFile()));
        gitOpOrder.verify(mockGitOperation).commit(eq(targetDir.toFile()), commitCaptor.capture());
        gitOpOrder.verify(mockGitOperation).addTag(eq(targetDir.toFile()), any(Tag.class));
        gitOpOrder.verify(mockGitOperation).mergeBranch(eq(targetDir.toFile()), anyString(), anyString());
        gitOpOrder.verify(mockGitOperation).pushBranch(eq(targetDir.toFile()), anyString());
        gitOpOrder.verify(mockGitOperation).pushTag(eq(targetDir.toFile()), anyString());
        assertThat(commitCaptor.getValue().getMessage()).isEqualTo(
                "Populate repository from blueprint " + context.blueprintName + "@" + context.versionNumber);
        deleteCreatedBlueprint(context);
    }

    /**
     * Spec — Scenario: Commit author can be customized with default fallback
     * (defaults when omitted).
     */
    @Test
    void whenCommitAuthorOmittedThenCommitUsesServerDefaults(@TempDir Path sourceDir, @TempDir Path targetDir)
            throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        BlueprintContext context = createBlueprintAndVersion("analytics-lakehouse", "1.2.0",
                manifestMonorepoNoComposition());

        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        GitOperation mockGitOperation = Mockito.mock(GitOperation.class);
        when(mockGitProvider.gitOperation()).thenReturn(mockGitOperation);
        when(mockGitProvider.getRepository(anyString(), anyString())).thenReturn(Optional.of(new Repository()));
        AtomicInteger callCounter = new AtomicInteger(0);
        AtomicReference<Commit> commitRef = new AtomicReference<>();
        doAnswer(invocation -> {
            Consumer<File> consumer = invocation.getArgument(2);
            consumer.accept(callCounter.getAndIncrement() == 0 ? targetDir.toFile() : sourceDir.toFile());
            return null;
        }).when(mockGitOperation).readRepository(any(), any(), any());
        doNothing().when(mockGitOperation).createAndCheckoutOrphanBranch(any(), anyString());
        doNothing().when(mockGitOperation).addAll(any());
        doAnswer(invocation -> {
            commitRef.set(invocation.getArgument(1));
            return null;
        }).when(mockGitOperation).commit(any(), any());
        when(mockGitOperation.getHeadSha(any(), anyString())).thenReturn("deadbeefcafebabe");
        doNothing().when(mockGitOperation).addTag(any(), any(Tag.class));
        when(mockGitOperation.mergeBranch(any(), anyString(), anyString())).thenReturn("deadbeefcafebabe");
        doNothing().when(mockGitOperation).pushBranch(any(), anyString());
        doNothing().when(mockGitOperation).pushTag(any(), anyString());

        InstantiateBlueprintVersionCommandRes request = buildInstantiateRequest(context.blueprintName,
                context.versionNumber, "prod", 365, null, null, null);

        ResponseEntity<InstantiateBlueprintVersionResponseRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_INSTANTIATE),
                HttpMethod.POST,
                new HttpEntity<>(request, jsonHeaders()),
                InstantiateBlueprintVersionResponseRes.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(commitRef.get()).isNotNull();
        assertThat(commitRef.get().getAuthor()).isEqualTo("odm-blueprint-server");
        assertThat(commitRef.get().getAuthorEmail()).isEqualTo("odm-blueprint-server@local");
        // Git: clone pointers unchanged;
        // only the commit identity differs from the explicit-author test.
        ArgumentCaptor<RepositoryPointer> pointerCaptor = ArgumentCaptor.forClass(RepositoryPointer.class);
        verify(mockGitOperation, times(2)).readRepository(any(), pointerCaptor.capture(), any());
        List<RepositoryPointer> pointers = pointerCaptor.getAllValues();
        assertThat(pointers.get(0)).isInstanceOf(RepositoryPointerBranch.class);
        assertThat(pointers.get(0).getRefValue()).isEqualTo("main");
        assertThat(pointers.get(1)).isInstanceOf(RepositoryPointerTag.class);
        assertThat(pointers.get(1).getRefValue()).isEqualTo("v" + context.versionNumber);
        InOrder gitOpOrder = Mockito.inOrder(mockGitOperation);
        ArgumentCaptor<Commit> commitCaptor = ArgumentCaptor.forClass(Commit.class);
        gitOpOrder.verify(mockGitOperation).createAndCheckoutOrphanBranch(eq(targetDir.toFile()), anyString());
        gitOpOrder.verify(mockGitOperation).addAll(eq(targetDir.toFile()));
        gitOpOrder.verify(mockGitOperation).commit(eq(targetDir.toFile()), commitCaptor.capture());
        gitOpOrder.verify(mockGitOperation).addTag(eq(targetDir.toFile()), any(Tag.class));
        gitOpOrder.verify(mockGitOperation).mergeBranch(eq(targetDir.toFile()), anyString(), anyString());
        gitOpOrder.verify(mockGitOperation).pushBranch(eq(targetDir.toFile()), anyString());
        gitOpOrder.verify(mockGitOperation).pushTag(eq(targetDir.toFile()), anyString());
        assertThat(commitCaptor.getValue().getMessage()).isEqualTo(
                "Populate repository from blueprint " + context.blueprintName + "@" + context.versionNumber);
        deleteCreatedBlueprint(context);
    }

    /**
     * Spec — Scenario: Instantiation strategy is derived from manifest metadata.
     */
    @Test
    void whenInstantiateWithoutMethodFieldThenNotRejectedForMissingMethod(@TempDir Path sourceDir,
            @TempDir Path targetDir) throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        BlueprintContext context = createBlueprintAndVersion("analytics-lakehouse", "1.2.0",
                manifestMonorepoNoComposition());
        GitOperation mockGitOperation = stubGitHappyPath(sourceDir, targetDir);

        ResponseEntity<InstantiateBlueprintVersionResponseRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_INSTANTIATE),
                HttpMethod.POST,
                new HttpEntity<>(buildInstantiateRequest(context.blueprintName, context.versionNumber, "prod", 365),
                        jsonHeaders()),
                InstantiateBlueprintVersionResponseRes.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Git: omitting manifest `method` does not block instantiation; clone and push
        // behavior matches the happy path.
        ArgumentCaptor<RepositoryPointer> pointerCaptor = ArgumentCaptor.forClass(RepositoryPointer.class);
        verify(mockGitOperation, times(2)).readRepository(any(), pointerCaptor.capture(), any());
        List<RepositoryPointer> pointers = pointerCaptor.getAllValues();
        assertThat(pointers.get(0)).isInstanceOf(RepositoryPointerBranch.class);
        assertThat(pointers.get(0).getRefValue()).isEqualTo("main");
        assertThat(pointers.get(1)).isInstanceOf(RepositoryPointerTag.class);
        assertThat(pointers.get(1).getRefValue()).isEqualTo("v" + context.versionNumber);
        InOrder gitOpOrder = Mockito.inOrder(mockGitOperation);
        ArgumentCaptor<Commit> commitCaptor = ArgumentCaptor.forClass(Commit.class);
        gitOpOrder.verify(mockGitOperation).createAndCheckoutOrphanBranch(eq(targetDir.toFile()), anyString());
        gitOpOrder.verify(mockGitOperation).addAll(eq(targetDir.toFile()));
        gitOpOrder.verify(mockGitOperation).commit(eq(targetDir.toFile()), commitCaptor.capture());
        gitOpOrder.verify(mockGitOperation).addTag(eq(targetDir.toFile()), any(Tag.class));
        gitOpOrder.verify(mockGitOperation).mergeBranch(eq(targetDir.toFile()), anyString(), anyString());
        gitOpOrder.verify(mockGitOperation).pushBranch(eq(targetDir.toFile()), anyString());
        gitOpOrder.verify(mockGitOperation).pushTag(eq(targetDir.toFile()), anyString());
        assertThat(commitCaptor.getValue().getMessage()).isEqualTo(
                "Populate repository from blueprint " + context.blueprintName + "@" + context.versionNumber);
        deleteCreatedBlueprint(context);
    }

    /**
     * Spec — Scenario: Target branch defaults to repository default.
     */
    @Test
    void whenTargetBranchOmittedThenGitUsesRepositoryDefaultBranch(@TempDir Path sourceDir, @TempDir Path targetDir)
            throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        BlueprintContext context = createBlueprintAndVersion("analytics-lakehouse", "1.2.0",
                manifestMonorepoNoComposition());

        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        GitOperation mockGitOperation = Mockito.mock(GitOperation.class);
        when(mockGitProvider.gitOperation()).thenReturn(mockGitOperation);
        when(mockGitProvider.getRepository(anyString(), anyString())).thenReturn(Optional.of(new Repository()));
        AtomicInteger callCounter = new AtomicInteger(0);
        doAnswer(invocation -> {
            Consumer<File> consumer = invocation.getArgument(2);
            consumer.accept(callCounter.getAndIncrement() == 0 ? targetDir.toFile() : sourceDir.toFile());
            return null;
        }).when(mockGitOperation).readRepository(any(), any(), any());
        doNothing().when(mockGitOperation).createAndCheckoutOrphanBranch(any(), anyString());
        doNothing().when(mockGitOperation).addAll(any());
        doNothing().when(mockGitOperation).commit(any(), any());
        when(mockGitOperation.getHeadSha(any(), anyString())).thenReturn("deadbeefcafebabe");
        doNothing().when(mockGitOperation).addTag(any(), any(Tag.class));
        when(mockGitOperation.mergeBranch(any(), anyString(), anyString())).thenReturn("deadbeefcafebabe");
        doNothing().when(mockGitOperation).pushBranch(any(), anyString());
        doNothing().when(mockGitOperation).pushTag(any(), anyString());

        rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_INSTANTIATE),
                HttpMethod.POST,
                new HttpEntity<>(buildInstantiateRequest(context.blueprintName, context.versionNumber, "prod", 365,
                        null, null, null), jsonHeaders()),
                InstantiateBlueprintVersionResponseRes.class);

        // Git: no explicit target branch in request → second read uses repository
        // default branch (main).
        ArgumentCaptor<RepositoryPointer> pointerCaptor = ArgumentCaptor.forClass(RepositoryPointer.class);
        verify(mockGitOperation, times(2)).readRepository(any(), pointerCaptor.capture(), any());
        List<RepositoryPointer> pointers = pointerCaptor.getAllValues();
        assertThat(pointers.get(0)).isInstanceOf(RepositoryPointerBranch.class);
        assertThat(pointers.get(0).getRefValue()).isEqualTo("main");
        assertThat(pointers.get(1)).isInstanceOf(RepositoryPointerTag.class);
        assertThat(pointers.get(1).getRefValue()).isEqualTo("v" + context.versionNumber);
        InOrder gitOpOrder = Mockito.inOrder(mockGitOperation);
        ArgumentCaptor<Commit> commitCaptor = ArgumentCaptor.forClass(Commit.class);
        gitOpOrder.verify(mockGitOperation).createAndCheckoutOrphanBranch(eq(targetDir.toFile()), anyString());
        gitOpOrder.verify(mockGitOperation).addAll(eq(targetDir.toFile()));
        gitOpOrder.verify(mockGitOperation).commit(eq(targetDir.toFile()), commitCaptor.capture());
        gitOpOrder.verify(mockGitOperation).addTag(eq(targetDir.toFile()), any(Tag.class));
        gitOpOrder.verify(mockGitOperation).mergeBranch(eq(targetDir.toFile()), anyString(), anyString());
        gitOpOrder.verify(mockGitOperation).pushBranch(eq(targetDir.toFile()), anyString());
        gitOpOrder.verify(mockGitOperation).pushTag(eq(targetDir.toFile()), anyString());
        assertThat(commitCaptor.getValue().getMessage()).isEqualTo(
                "Populate repository from blueprint " + context.blueprintName + "@" + context.versionNumber);

        deleteCreatedBlueprint(context);
    }

    /**
     * Spec — Scenario: Target branch can be overridden.
     */
    @Test
    void whenTargetBranchSetThenGitUsesThatBranch(@TempDir Path sourceDir, @TempDir Path targetDir) throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        BlueprintContext context = createBlueprintAndVersion("analytics-lakehouse", "1.2.0",
                manifestMonorepoNoComposition());

        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        GitOperation mockGitOperation = Mockito.mock(GitOperation.class);
        when(mockGitProvider.gitOperation()).thenReturn(mockGitOperation);
        when(mockGitProvider.getRepository(anyString(), anyString())).thenReturn(Optional.of(new Repository()));
        AtomicInteger callCounter = new AtomicInteger(0);
        doAnswer(invocation -> {
            Consumer<File> consumer = invocation.getArgument(2);
            consumer.accept(callCounter.getAndIncrement() == 0 ? targetDir.toFile() : sourceDir.toFile());
            return null;
        }).when(mockGitOperation).readRepository(any(), any(), any());
        doNothing().when(mockGitOperation).createAndCheckoutOrphanBranch(any(), anyString());
        doNothing().when(mockGitOperation).addAll(any());
        doNothing().when(mockGitOperation).commit(any(), any());
        when(mockGitOperation.getHeadSha(any(), anyString())).thenReturn("deadbeefcafebabe");
        doNothing().when(mockGitOperation).addTag(any(), any(Tag.class));
        when(mockGitOperation.mergeBranch(any(), anyString(), anyString())).thenReturn("deadbeefcafebabe");
        doNothing().when(mockGitOperation).pushBranch(any(), anyString());
        doNothing().when(mockGitOperation).pushTag(any(), anyString());

        InstantiateBlueprintVersionCommandRes request = buildInstantiateRequest(context.blueprintName,
                context.versionNumber, "prod", 365, null, null, "feature/custom");

        rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_INSTANTIATE),
                HttpMethod.POST,
                new HttpEntity<>(request, jsonHeaders()),
                InstantiateBlueprintVersionResponseRes.class);

        // Git: second read uses the requested branch instead of the repository default.
        ArgumentCaptor<RepositoryPointer> pointerCaptor = ArgumentCaptor.forClass(RepositoryPointer.class);
        verify(mockGitOperation, times(2)).readRepository(any(), pointerCaptor.capture(), any());
        List<RepositoryPointer> pointers = pointerCaptor.getAllValues();
        assertThat(pointers.get(0)).isInstanceOf(RepositoryPointerBranch.class);
        assertThat(pointers.get(0).getRefValue()).isEqualTo("feature/custom");
        assertThat(pointers.get(1)).isInstanceOf(RepositoryPointerTag.class);
        assertThat(pointers.get(1).getRefValue()).isEqualTo("v" + context.versionNumber);
        InOrder gitOpOrder = Mockito.inOrder(mockGitOperation);
        ArgumentCaptor<Commit> commitCaptor = ArgumentCaptor.forClass(Commit.class);
        gitOpOrder.verify(mockGitOperation).createAndCheckoutOrphanBranch(eq(targetDir.toFile()), anyString());
        gitOpOrder.verify(mockGitOperation).addAll(eq(targetDir.toFile()));
        gitOpOrder.verify(mockGitOperation).commit(eq(targetDir.toFile()), commitCaptor.capture());
        gitOpOrder.verify(mockGitOperation).addTag(eq(targetDir.toFile()), any(Tag.class));
        gitOpOrder.verify(mockGitOperation).mergeBranch(eq(targetDir.toFile()), anyString(), anyString());
        gitOpOrder.verify(mockGitOperation).pushBranch(eq(targetDir.toFile()), anyString());
        gitOpOrder.verify(mockGitOperation).pushTag(eq(targetDir.toFile()), anyString());
        assertThat(commitCaptor.getValue().getMessage()).isEqualTo(
                "Populate repository from blueprint " + context.blueprintName + "@" + context.versionNumber);

        deleteCreatedBlueprint(context);
    }

    /**
     * Spec — Scenario: Exactly one root target is required in this phase (empty
     * list).
     */
    @Test
    void whenNoTargetRepositoriesThenReturn400() throws Exception {
        BlueprintContext context = createBlueprintAndVersion("analytics-lakehouse", "1.2.0",
                manifestMonorepoNoComposition());

        InstantiateBlueprintVersionCommandRes request = buildInstantiateRequest(context.blueprintName,
                context.versionNumber, "prod", 365);
        request.setTargetRepositories(List.of());

        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_INSTANTIATE),
                HttpMethod.POST,
                new HttpEntity<>(request, jsonHeaders()),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        deleteCreatedBlueprint(context);
    }

    /**
     * Spec — Scenario: Exactly one root target is required in this phase (more than
     * one).
     */
    @Test
    void whenMoreThanOneTargetRepositoryThenReturn400(@TempDir Path sourceDir, @TempDir Path targetDir)
            throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        BlueprintContext context = createBlueprintAndVersion("analytics-lakehouse", "1.2.0",
                manifestMonorepoNoComposition());
        stubGitHappyPath(sourceDir, targetDir);

        InstantiateBlueprintVersionCommandRes request = buildInstantiateRequest(context.blueprintName,
                context.versionNumber, "prod", 365);
        InstantiateBlueprintVersionTargetRepositoryRes t1 = new InstantiateBlueprintVersionTargetRepositoryRes();
        t1.setTargetId(OdmBlueprintManifestAutoFiller.DEFAULT_REPOSITORY_KEY);
        t1.setRepository(buildTargetRepository());
        InstantiateBlueprintVersionTargetRepositoryRes t2 = new InstantiateBlueprintVersionTargetRepositoryRes();
        t2.setTargetId(OdmBlueprintManifestAutoFiller.DEFAULT_REPOSITORY_KEY);
        t2.setRepository(buildTargetRepository());
        request.setTargetRepositories(List.of(t1, t2));

        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_INSTANTIATE),
                HttpMethod.POST,
                new HttpEntity<>(request, jsonHeaders()),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        deleteCreatedBlueprint(context);
    }

    /**
     * Spec — Scenario: targetId must match the sole instantiation.repositories[].key.
     */
    @Test
    void whenTargetIdDoesNotMatchRepositoryKeyThenReturn400(@TempDir Path sourceDir, @TempDir Path targetDir)
            throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        BlueprintContext context = createBlueprintAndVersion("analytics-lakehouse", "1.2.0",
                manifestMonorepoNoComposition());
        stubGitHappyPath(sourceDir, targetDir);

        InstantiateBlueprintVersionCommandRes request = buildInstantiateRequest(context.blueprintName,
                context.versionNumber, "prod", 365);
        request.getTargetRepositories().getFirst().setTargetId("not-the-manifest-repo-key");

        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_INSTANTIATE),
                HttpMethod.POST,
                new HttpEntity<>(request, jsonHeaders()),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        deleteCreatedBlueprint(context);
    }

    /**
     * Spec — Scenario: Missing required parameters are rejected.
     */
    @Test
    void whenRequiredParameterMissingThenReturn400() throws Exception {
        BlueprintContext context = createBlueprintAndVersion("analytics-lakehouse", "1.2.0",
                manifestMonorepoNoComposition());

        InstantiateBlueprintVersionCommandRes request = buildInstantiateRequest(context.blueprintName,
                context.versionNumber, null, 365);
        request.getParameters().remove("environment");
        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_INSTANTIATE),
                HttpMethod.POST,
                new HttpEntity<>(request, jsonHeaders()),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        deleteCreatedBlueprint(context);
    }

    /**
     * Spec — Scenario: Invalid parameter types or constraints are rejected.
     */
    @Test
    void whenParameterTypeOrConstraintInvalidThenReturn400() throws Exception {
        BlueprintContext context = createBlueprintAndVersion("analytics-lakehouse", "1.2.0",
                manifestMonorepoNoComposition());

        InstantiateBlueprintVersionCommandRes request = buildInstantiateRequest(context.blueprintName,
                context.versionNumber, "invalid-env", -1);
        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_INSTANTIATE),
                HttpMethod.POST,
                new HttpEntity<>(request, jsonHeaders()),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        deleteCreatedBlueprint(context);
    }

    /*
     * Feature: Instantiate N→1 monorepo with composition
     * Scenario: Parent and modules land on distinct paths in one target
     *   Given a published parent with one repository key "main"
     *   And composition modules "storage" and "serving" that are published 1→1 versions
     *   And parent root.targets writes to "core/" on "main"
     *   And modules write to "data-plane/storage" and "app/serving" on "main"
     *   And parameterMapping uses { $param: projectSlug } and { value: eu-west-1 }
     *   When the client instantiates mapping "main" to one Git repo
     *   Then the response status is 200
     *   And the target tree contains rendered parent files under core/ and module files under the composition destination paths
     *   And lineage on the root target records only the parent version and parent parameters
     */
    @Test
    void whenInstantiateMonorepoWithCompositionThenReturn200AndParentLineageOnly(
            @TempDir Path sourceDir, @TempDir Path targetDir) throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        writeSafeDescriptor(sourceDir);

        BlueprintContext storage = createPublishedModule("odm-blueprint-s3-lake", "3.0.1");
        BlueprintContext serving = createPublishedModule("odm-blueprint-api-skeleton", "1.4.0");
        ObjectNode parentManifest = (ObjectNode) manifestMonorepoWithComposition();
        rewriteCompositionRefs(parentManifest, storage, serving);
        BlueprintContext parent = createBlueprintAndVersion("full-stack-dp", "2.1.0", parentManifest);

        GitOperation mockGitOperation = stubGitHappyPath(sourceDir, targetDir);

        InstantiateBlueprintVersionCommandRes request = new InstantiateBlueprintVersionCommandRes();
        request.setBlueprintName(parent.blueprintName);
        request.setBlueprintVersionNumber(parent.versionNumber);
        InstantiateBlueprintVersionTargetRepositoryRes target = new InstantiateBlueprintVersionTargetRepositoryRes();
        target.setTargetId(OdmBlueprintManifestAutoFiller.DEFAULT_REPOSITORY_KEY);
        target.setRepository(buildTargetRepository());
        request.setTargetRepositories(List.of(target));
        Map<String, JsonNode> parameters = new LinkedHashMap<>();
        parameters.put("projectSlug", OBJECT_MAPPER.valueToTree("acme-lake"));
        parameters.put("enablePiiMasking", OBJECT_MAPPER.valueToTree(true));
        request.setParameters(parameters);

        ResponseEntity<InstantiateBlueprintVersionResponseRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_INSTANTIATE),
                HttpMethod.POST,
                new HttpEntity<>(request, jsonHeaders()),
                InstantiateBlueprintVersionResponseRes.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Files.isDirectory(targetDir.resolve(".odm/blueprint"))).isTrue();
        assertThat(Files.exists(targetDir.resolve(".odm/blueprint/blueprint-manifest.yaml"))).isTrue();
        assertThat(Files.isRegularFile(targetDir.resolve("core/templates/descriptor.json"))).isTrue();
        assertThat(Files.isDirectory(targetDir.resolve("data-plane/storage"))).isTrue();
        assertThat(Files.isDirectory(targetDir.resolve("app/serving"))).isTrue();
        // N→1: target + parent source + 2 module sources
        verify(mockGitOperation, times(4)).readRepository(any(), any(), any());

        deleteCreatedBlueprint(parent);
        deleteCreatedBlueprint(storage);
        deleteCreatedBlueprint(serving);
    }

    /*
     * Feature: Instantiate 1→N polyrepo without composition
     * Scenario: Parent routes to two keys with lineage only on the designated root
     *   Given a published parent with keys "infra-repo" and "app-repo"
     *   And root.targets send terraform/ and policies/ to "infra-repo" at sibling destinations
     *   And root.targets send application/ to "app-repo" at "./"
     *   And parent BlueprintRepo has descriptorTemplatePath at repo root (not covered by any route)
     *   When the client POSTs instantiate
     *   Then both targets are cloned, checkpointed, merged, and pushed independently
     *   And infra-repo contains only infra routes and has no .odm/blueprint/ lineage copy
     *   And app-repo is the designated root and contains the implicitly rendered descriptor plus .odm/blueprint/
     */
    @Test
    void whenInstantiatePolyrepoNoCompositionThenCheckpointEachTargetAndLineageOnRoot(
            @TempDir Path sourceDir, @TempDir Path infraTarget, @TempDir Path appTarget) throws Exception {
        writePolyrepoSourceFiles(sourceDir);
        BlueprintContext context = createBlueprintAndVersion(
                "split-stack-template",
                "0.5.0",
                manifestPolyrepoNoComposition());

        GitOperation mockGitOperation = stubGitHappyPath(sourceDir, infraTarget, appTarget);

        InstantiateBlueprintVersionCommandRes request = new InstantiateBlueprintVersionCommandRes();
        request.setBlueprintName(context.blueprintName);
        request.setBlueprintVersionNumber(context.versionNumber);
        InstantiateBlueprintVersionTargetRepositoryRes infra = new InstantiateBlueprintVersionTargetRepositoryRes();
        infra.setTargetId("infra-repo");
        infra.setRepository(buildTargetRepository("infra-repository-id", "infra-repo"));
        InstantiateBlueprintVersionTargetRepositoryRes app = new InstantiateBlueprintVersionTargetRepositoryRes();
        app.setTargetId("app-repo");
        app.setRepository(buildTargetRepository("app-repository-id", "app-repo"));
        request.setTargetRepositories(List.of(infra, app));
        request.setParameters(Map.of("awsRegion", OBJECT_MAPPER.valueToTree("eu-west-1")));

        ResponseEntity<InstantiateBlueprintVersionResponseRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_INSTANTIATE),
                HttpMethod.POST,
                new HttpEntity<>(request, jsonHeaders()),
                InstantiateBlueprintVersionResponseRes.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(mockGitOperation, times(2)).pushBranch(any(), anyString());
        assertThat(Files.exists(appTarget.resolve(".odm/blueprint/blueprint-manifest.yaml"))).isTrue();
        assertThat(Files.isRegularFile(appTarget.resolve("templates/descriptor.json"))).isTrue();
        assertThat(Files.exists(infraTarget.resolve(".odm/blueprint"))).isFalse();
        assertThat(Files.exists(infraTarget.resolve("terraform"))).isTrue();
        assertThat(Files.exists(infraTarget.resolve("governance/policies"))).isTrue();

        deleteCreatedBlueprint(context);
    }

    /*
     * Feature: Instantiate 1→N polyrepo without composition
     * Scenario: Incomplete target map is rejected before Git
     *   Given a polyrepo parent with keys "infra-repo" and "app-repo"
     *   And the request maps only "app-repo"
     *   When the client POSTs instantiate
     *   Then the response status is 400
     *   And the message names the missing key and a hint to supply targetRepositories for every instantiation.repositories[].key
     *   And no Git mutation runs
     */
    @Test
    void whenPolyrepoInstantiateOmitsAKeyThenReturn400AndNoGit() throws Exception {
        BlueprintContext context = createBlueprintAndVersion("split-stack-template", "0.5.0",
                manifestPolyrepoNoComposition());
        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        GitOperation mockGitOperation = Mockito.mock(GitOperation.class);
        when(mockGitProvider.gitOperation()).thenReturn(mockGitOperation);

        InstantiateBlueprintVersionCommandRes request = new InstantiateBlueprintVersionCommandRes();
        request.setBlueprintName(context.blueprintName);
        request.setBlueprintVersionNumber(context.versionNumber);
        InstantiateBlueprintVersionTargetRepositoryRes app = new InstantiateBlueprintVersionTargetRepositoryRes();
        app.setTargetId("app-repo");
        app.setRepository(buildTargetRepository("app-repository-id", "app-repo"));
        request.setTargetRepositories(List.of(app));
        request.setParameters(Map.of("awsRegion", OBJECT_MAPPER.valueToTree("eu-west-1")));

        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_INSTANTIATE),
                HttpMethod.POST,
                new HttpEntity<>(request, jsonHeaders()),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("infra-repo");
        assertThat(response.getBody()).containsIgnoringCase("hint");
        verify(mockGitOperation, never()).readRepository(any(), any(), any());
        deleteCreatedBlueprint(context);
    }

    /*
     * Feature: Instantiate 1→1 monorepo without composition
     * Scenario: Path-split routes into the same key
     *   Given a published parent with one key "prod"
     *   And two root.targets into "prod" with sibling destinations "core/" and "docs/" (not nested)
     *   When the client instantiates with a complete target map
     *   Then files from each sourcePath appear only under the matching destination path
     *   And lineage is written only once on that single root target
     */
    @Test
    void whenInstantiateMonorepoPathSplitThenFilesLandOnSiblingPaths(
            @TempDir Path sourceDir, @TempDir Path targetDir) throws Exception {
        writePathSplitSourceFiles(sourceDir);
        ObjectNode manifest = (ObjectNode) manifestMonorepoNoComposition();
        ObjectNode instantiation = (ObjectNode) manifest.get("instantiation");
        ObjectNode root = (ObjectNode) instantiation.get("root");
        root.set("targets", OBJECT_MAPPER.createArrayNode()
                .add(OBJECT_MAPPER.createObjectNode()
                        .put("sourcePath", "core-src/")
                        .put("repository", OdmBlueprintManifestAutoFiller.DEFAULT_REPOSITORY_KEY)
                        .put("path", "core/"))
                .add(OBJECT_MAPPER.createObjectNode()
                        .put("sourcePath", "docs-src/")
                        .put("repository", OdmBlueprintManifestAutoFiller.DEFAULT_REPOSITORY_KEY)
                        .put("path", "docs/")));

        BlueprintContext context = createBlueprintAndVersion(
                "analytics-lakehouse",
                "1.2.0",
                manifest,
                "core-src/templates/descriptor.json.vm");
        GitOperation mockGitOperation = stubGitHappyPath(sourceDir, targetDir);

        ResponseEntity<InstantiateBlueprintVersionResponseRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_INSTANTIATE),
                HttpMethod.POST,
                new HttpEntity<>(buildInstantiateRequest(context.blueprintName, context.versionNumber, "prod", 365),
                        jsonHeaders()),
                InstantiateBlueprintVersionResponseRes.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Files.exists(targetDir.resolve("core/from-core.txt"))).isTrue();
        assertThat(Files.exists(targetDir.resolve("docs/from-docs.txt"))).isTrue();
        assertThat(Files.exists(targetDir.resolve(".odm/blueprint/blueprint-manifest.yaml"))).isTrue();
        verify(mockGitOperation, times(1)).pushBranch(any(), anyString());
        deleteCreatedBlueprint(context);
    }

    /*
     * Feature: Target mapping and Git constraints
     * Scenario: Duplicate targetId is rejected
     *   Given two targetRepositories entries with the same targetId
     *   When instantiate runs
     *   Then 400 with a hint to send each key once
     */
    @Test
    void whenDuplicateTargetIdThenReturn400(@TempDir Path sourceDir, @TempDir Path targetDir) throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        BlueprintContext context = createBlueprintAndVersion("analytics-lakehouse", "1.2.0",
                manifestMonorepoNoComposition());
        stubGitHappyPath(sourceDir, targetDir);

        InstantiateBlueprintVersionCommandRes request = buildInstantiateRequest(context.blueprintName,
                context.versionNumber, "prod", 365);
        InstantiateBlueprintVersionTargetRepositoryRes t1 = new InstantiateBlueprintVersionTargetRepositoryRes();
        t1.setTargetId(OdmBlueprintManifestAutoFiller.DEFAULT_REPOSITORY_KEY);
        t1.setRepository(buildTargetRepository());
        InstantiateBlueprintVersionTargetRepositoryRes t2 = new InstantiateBlueprintVersionTargetRepositoryRes();
        t2.setTargetId(OdmBlueprintManifestAutoFiller.DEFAULT_REPOSITORY_KEY);
        t2.setRepository(buildTargetRepository());
        request.setTargetRepositories(List.of(t1, t2));

        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_INSTANTIATE),
                HttpMethod.POST,
                new HttpEntity<>(request, jsonHeaders()),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsIgnoringCase("Duplicate");
        deleteCreatedBlueprint(context);
    }

    /*
     * Feature: Target mapping and Git constraints
     * Scenario: Unknown targetId is rejected
     *   Given a targetId that is not a declared repository key
     *   Then 400 with a hint to match instantiation.repositories[].key
     */
    @Test
    void whenUnknownTargetIdThenReturn400(@TempDir Path sourceDir, @TempDir Path targetDir) throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        BlueprintContext context = createBlueprintAndVersion("analytics-lakehouse", "1.2.0",
                manifestMonorepoNoComposition());
        stubGitHappyPath(sourceDir, targetDir);

        InstantiateBlueprintVersionCommandRes request = buildInstantiateRequest(context.blueprintName,
                context.versionNumber, "prod", 365);
        request.getTargetRepositories().getFirst().setTargetId("not-the-manifest-repo-key");

        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_INSTANTIATE),
                HttpMethod.POST,
                new HttpEntity<>(request, jsonHeaders()),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsIgnoringCase("Unknown");
        deleteCreatedBlueprint(context);
    }

    /**
     * Spec — Scenario: Git operation failures are surfaced as client or server
     * errors per global handling.
     */
    @Test
    void whenGitPushFailsThenResponseReflectsGlobalExceptionHandling(@TempDir Path sourceDir, @TempDir Path targetDir)
            throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        BlueprintContext context = createBlueprintAndVersion("analytics-lakehouse", "1.2.0",
                manifestMonorepoNoComposition());

        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        GitOperation mockGitOperation = Mockito.mock(GitOperation.class);
        when(mockGitProvider.gitOperation()).thenReturn(mockGitOperation);
        when(mockGitProvider.getRepository(anyString(), anyString())).thenReturn(Optional.of(new Repository()));
        AtomicInteger callCounter = new AtomicInteger(0);
        doAnswer(invocation -> {
            Consumer<File> consumer = invocation.getArgument(2);
            consumer.accept(callCounter.getAndIncrement() == 0 ? targetDir.toFile() : sourceDir.toFile());
            return null;
        }).when(mockGitOperation).readRepository(any(), any(), any());
        doNothing().when(mockGitOperation).createAndCheckoutOrphanBranch(any(), anyString());
        doNothing().when(mockGitOperation).addAll(any());
        doNothing().when(mockGitOperation).commit(any(), any());
        when(mockGitOperation.getHeadSha(any(), anyString())).thenReturn("deadbeefcafebabe");
        doNothing().when(mockGitOperation).addTag(any(), any(Tag.class));
        when(mockGitOperation.mergeBranch(any(), anyString(), anyString())).thenReturn("deadbeefcafebabe");
        doThrow(new GitOperationException("push failed")).when(mockGitOperation).pushBranch(any(), anyString());

        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_INSTANTIATE),
                HttpMethod.POST,
                new HttpEntity<>(buildInstantiateRequest(context.blueprintName, context.versionNumber, "prod", 365),
                        jsonHeaders()),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // Git: clone source and target, stage and commit succeed; pushBranch fails and
        // surfaces as client error.
        verify(mockGitOperation, times(2)).readRepository(any(), any(), any());
        verify(mockGitOperation).addAll(any());
        verify(mockGitOperation).commit(any(), any());
        verify(mockGitOperation).pushBranch(eq(targetDir.toFile()), anyString());
        deleteCreatedBlueprint(context);
    }

    /**
     * Spec — Scenario: Blueprint README declared in repository metadata is not left
     * at repository root.
     */
    @Test
    void whenPopulateThenReadmeIsRelocatedUnderDotOdmBlueprint(@TempDir Path sourceDir, @TempDir Path targetDir)
            throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        BlueprintContext context = createBlueprintAndVersion("analytics-lakehouse", "1.2.0",
                manifestMonorepoNoComposition());
        GitOperation mockGitOperation = stubGitHappyPath(sourceDir, targetDir);

        ResponseEntity<InstantiateBlueprintVersionResponseRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_INSTANTIATE),
                HttpMethod.POST,
                new HttpEntity<>(buildInstantiateRequest(context.blueprintName, context.versionNumber, "prod", 365),
                        jsonHeaders()),
                InstantiateBlueprintVersionResponseRes.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Files.exists(targetDir.resolve("README.md"))).isFalse();
        assertThat(Files.exists(targetDir.resolve(".odm/blueprint/README.md"))).isTrue();
        assertThat(Files.exists(targetDir.resolve("manifest.yaml"))).isFalse();
        assertThat(Files.exists(targetDir.resolve(".odm/blueprint/blueprint-manifest.yaml"))).isTrue();
        // Git: standard tag/branch reads and populate push;
        // file layout above asserts README and manifest.yaml were relocated under
        // .odm/blueprint.
        ArgumentCaptor<RepositoryPointer> pointerCaptor = ArgumentCaptor.forClass(RepositoryPointer.class);
        verify(mockGitOperation, times(2)).readRepository(any(), pointerCaptor.capture(), any());
        List<RepositoryPointer> pointers = pointerCaptor.getAllValues();
        assertThat(pointers.get(0)).isInstanceOf(RepositoryPointerBranch.class);
        assertThat(pointers.get(0).getRefValue()).isEqualTo("main");
        assertThat(pointers.get(1)).isInstanceOf(RepositoryPointerTag.class);
        assertThat(pointers.get(1).getRefValue()).isEqualTo("v" + context.versionNumber);
        InOrder gitOpOrder = Mockito.inOrder(mockGitOperation);
        ArgumentCaptor<Commit> commitCaptor = ArgumentCaptor.forClass(Commit.class);
        gitOpOrder.verify(mockGitOperation).createAndCheckoutOrphanBranch(eq(targetDir.toFile()), anyString());
        gitOpOrder.verify(mockGitOperation).addAll(eq(targetDir.toFile()));
        gitOpOrder.verify(mockGitOperation).commit(eq(targetDir.toFile()), commitCaptor.capture());
        gitOpOrder.verify(mockGitOperation).addTag(eq(targetDir.toFile()), any(Tag.class));
        gitOpOrder.verify(mockGitOperation).mergeBranch(eq(targetDir.toFile()), anyString(), anyString());
        gitOpOrder.verify(mockGitOperation).pushBranch(eq(targetDir.toFile()), anyString());
        gitOpOrder.verify(mockGitOperation).pushTag(eq(targetDir.toFile()), anyString());
        assertThat(commitCaptor.getValue().getMessage()).isEqualTo(
                "Populate repository from blueprint " + context.blueprintName + "@" + context.versionNumber);

        deleteCreatedBlueprint(context);
    }

    /**
     * Spec — Scenario: Manifest lineage snapshot is persisted under
     * `.odm/blueprint/`.
     */
    @Test
    void whenPopulateThenManifestSnapshotIsWrittenUnderDotOdmBlueprint(@TempDir Path sourceDir, @TempDir Path targetDir)
            throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        BlueprintContext context = createBlueprintAndVersion("analytics-lakehouse", "1.2.0",
                manifestMonorepoNoComposition());
        GitOperation mockGitOperation = stubGitHappyPath(sourceDir, targetDir);

        ResponseEntity<InstantiateBlueprintVersionResponseRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_INSTANTIATE),
                HttpMethod.POST,
                new HttpEntity<>(buildInstantiateRequest(context.blueprintName, context.versionNumber, "prod", 365),
                        jsonHeaders()),
                InstantiateBlueprintVersionResponseRes.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Files.exists(targetDir.resolve("manifest.yaml"))).isFalse();
        Path lineagePath = targetDir.resolve(".odm/blueprint/blueprint-manifest.yaml");
        assertThat(Files.exists(lineagePath)).isTrue();
        // Git: same clone/push shape as other populate tests;
        // source manifest.yaml is removed from root; snapshot is written as
        // blueprint-manifest.yaml under .odm/blueprint/.
        ArgumentCaptor<RepositoryPointer> pointerCaptor = ArgumentCaptor.forClass(RepositoryPointer.class);
        verify(mockGitOperation, times(2)).readRepository(any(), pointerCaptor.capture(), any());
        List<RepositoryPointer> pointers = pointerCaptor.getAllValues();
        assertThat(pointers.get(0)).isInstanceOf(RepositoryPointerBranch.class);
        assertThat(pointers.get(0).getRefValue()).isEqualTo("main");
        assertThat(pointers.get(1)).isInstanceOf(RepositoryPointerTag.class);
        assertThat(pointers.get(1).getRefValue()).isEqualTo("v" + context.versionNumber);
        InOrder gitOpOrder = Mockito.inOrder(mockGitOperation);
        ArgumentCaptor<Commit> commitCaptor = ArgumentCaptor.forClass(Commit.class);
        gitOpOrder.verify(mockGitOperation).createAndCheckoutOrphanBranch(eq(targetDir.toFile()), anyString());
        gitOpOrder.verify(mockGitOperation).addAll(eq(targetDir.toFile()));
        gitOpOrder.verify(mockGitOperation).commit(eq(targetDir.toFile()), commitCaptor.capture());
        gitOpOrder.verify(mockGitOperation).addTag(eq(targetDir.toFile()), any(Tag.class));
        gitOpOrder.verify(mockGitOperation).mergeBranch(eq(targetDir.toFile()), anyString(), anyString());
        gitOpOrder.verify(mockGitOperation).pushBranch(eq(targetDir.toFile()), anyString());
        gitOpOrder.verify(mockGitOperation).pushTag(eq(targetDir.toFile()), anyString());
        assertThat(commitCaptor.getValue().getMessage()).isEqualTo(
                "Populate repository from blueprint " + context.blueprintName + "@" + context.versionNumber);

        deleteCreatedBlueprint(context);
    }

    private GitOperation stubGitHappyPath(Path sourceDir, Path... targetDirs) {
        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        GitOperation mockGitOperation = Mockito.mock(GitOperation.class);
        when(mockGitProvider.gitOperation()).thenReturn(mockGitOperation);
        when(mockGitProvider.getRepository(anyString(), anyString())).thenReturn(Optional.of(new Repository()));

        AtomicInteger targetIndex = new AtomicInteger(0);
        doAnswer(invocation -> {
            Consumer<File> consumer = invocation.getArgument(2);
            RepositoryPointer pointer = invocation.getArgument(1);
            if (pointer instanceof RepositoryPointerBranch) {
                Path target = targetDirs[Math.min(targetIndex.getAndIncrement(), targetDirs.length - 1)];
                consumer.accept(target.toFile());
            } else {
                consumer.accept(sourceDir.toFile());
            }
            return null;
        }).when(mockGitOperation).readRepository(any(), any(), any());

        doNothing().when(mockGitOperation).createAndCheckoutOrphanBranch(any(), anyString());
        doNothing().when(mockGitOperation).addAll(any());
        doNothing().when(mockGitOperation).commit(any(), any());
        when(mockGitOperation.getHeadSha(any(), anyString())).thenReturn("deadbeefcafebabe");
        doNothing().when(mockGitOperation).addTag(any(), any(Tag.class));
        when(mockGitOperation.mergeBranch(any(), anyString(), anyString())).thenReturn("deadbeefcafebabe");
        doNothing().when(mockGitOperation).pushBranch(any(), anyString());
        doNothing().when(mockGitOperation).pushTag(any(), anyString());
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

    private void writePathSplitSourceFiles(Path sourceDir) throws IOException {
        Files.createDirectories(sourceDir.resolve("core-src/templates"));
        Files.writeString(sourceDir.resolve("core-src/from-core.txt"), "core");
        Files.writeString(sourceDir.resolve("core-src/README.md"), "# core");
        Files.writeString(sourceDir.resolve("core-src/manifest.yaml"), "spec: odm-blueprint-manifest\n");
        Files.writeString(sourceDir.resolve("core-src/templates/descriptor.json.vm"), """
                {
                  "dataProductDescriptor": "1.0.0",
                  "info": {
                    "name": "path-split"
                  }
                }
                """);
        Files.createDirectories(sourceDir.resolve("docs-src"));
        Files.writeString(sourceDir.resolve("docs-src/from-docs.txt"), "docs");
    }

    private BlueprintContext createPublishedModule(String blueprintName, String version) throws Exception {
        return createBlueprintAndVersion(blueprintName, version, manifestMonorepoNoComposition());
    }

    private void rewriteCompositionRefs(ObjectNode parentManifest, BlueprintContext storage, BlueprintContext serving) {
        for (JsonNode node : parentManifest.get("composition")) {
            ObjectNode composition = (ObjectNode) node;
            if ("storage".equals(composition.get("module").asText())) {
                composition.put("blueprintName", storage.blueprintName);
                composition.put("blueprintVersion", storage.versionNumber);
            } else if ("serving".equals(composition.get("module").asText())) {
                composition.put("blueprintName", serving.blueprintName);
                composition.put("blueprintVersion", serving.versionNumber);
            }
        }
    }

    private BlueprintContext createBlueprintAndVersion(String blueprintName, String version, JsonNode manifestContent)
            throws Exception {
        return createBlueprintAndVersion(blueprintName, version, manifestContent, "templates/descriptor.json.vm");
    }

    private BlueprintContext createBlueprintAndVersion(
            String blueprintName,
            String version,
            JsonNode manifestContent,
            String descriptorTemplatePath)
            throws Exception {
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
        blueprint.setBlueprintRepo(buildBlueprintRepo(descriptorTemplatePath));

        ResponseEntity<BlueprintRes> createdBlueprint = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                BlueprintRes.class);
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
                BlueprintVersionRes.class);
        assertThat(createdVersion.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createdVersion.getBody()).isNotNull();
        return new BlueprintContext(
                createdBlueprint.getBody().getUuid(),
                uniqueBlueprintName,
                version,
                createdVersion.getBody().getUuid());
    }

    private BlueprintRes.BlueprintRepoRes buildBlueprintRepo() {
        return buildBlueprintRepo("templates/descriptor.json.vm");
    }

    private BlueprintRes.BlueprintRepoRes buildBlueprintRepo(String descriptorTemplatePath) {
        BlueprintRes.BlueprintRepoRes blueprintRepo = new BlueprintRes.BlueprintRepoRes();
        blueprintRepo.setExternalIdentifier("source-blueprint-repository");
        blueprintRepo.setName("source-blueprint-repository");
        blueprintRepo.setDescription("source");
        blueprintRepo.setManifestRootPath("/manifest.yaml");
        blueprintRepo.setDescriptorTemplatePath(descriptorTemplatePath);
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
            Integer retentionDays) {
        return buildInstantiateRequest(blueprintName, blueprintVersion, environment, retentionDays, null, null, null);
    }

    private InstantiateBlueprintVersionCommandRes buildInstantiateRequest(
            String blueprintName,
            String blueprintVersion,
            String environment,
            Integer retentionDays,
            String commitAuthorName,
            String commitAuthorEmail,
            String targetBranch) {
        InstantiateBlueprintVersionCommandRes request = new InstantiateBlueprintVersionCommandRes();
        request.setBlueprintName(blueprintName);
        request.setBlueprintVersionNumber(blueprintVersion);

        InstantiateBlueprintVersionTargetRepositoryRes target = new InstantiateBlueprintVersionTargetRepositoryRes();
        target.setTargetId(OdmBlueprintManifestAutoFiller.DEFAULT_REPOSITORY_KEY);
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
        return buildTargetRepository("target-repository-id", "customer360");
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
        private final String blueprintVersionUuid;

        private BlueprintContext(String blueprintUuid, String blueprintName, String versionNumber,
                String blueprintVersionUuid) {
            this.blueprintUuid = blueprintUuid;
            this.blueprintName = blueprintName;
            this.versionNumber = versionNumber;
            this.blueprintVersionUuid = blueprintVersionUuid;
        }
    }
}
