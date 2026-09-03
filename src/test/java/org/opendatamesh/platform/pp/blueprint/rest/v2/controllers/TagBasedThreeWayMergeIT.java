package org.opendatamesh.platform.pp.blueprint.rest.v2.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.AdditionalAnswers;
import org.mockito.Mockito;
import org.opendatamesh.platform.git.git.GitCredentialHttps;
import org.opendatamesh.platform.git.git.GitOperation;
import org.opendatamesh.platform.git.git.GitOperationImpl;
import org.opendatamesh.platform.git.model.Repository;
import org.opendatamesh.platform.git.model.RepositoryPointer;
import org.opendatamesh.platform.git.model.RepositoryPointerBranch;
import org.opendatamesh.platform.git.model.RepositoryPointerTag;
import org.opendatamesh.platform.git.provider.GitProvider;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.BlueprintGitNamingConventions;
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
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.updatedataproduct.UpdateDataProductCommandRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.updatedataproduct.UpdateDataProductResultRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.updatedataproduct.UpdateDataProductTargetRepositoryRes;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.manifestautofiller.OdmBlueprintManifestAutoFiller.DEFAULT_REPOSITORY_KEY;

/**
 * API-level integration tests for tag-based 3-way merge semantics (BDMD-5127).
 * <p>
 * GitProvider is mocked, but {@link GitOperation} delegates to a real
 * {@link GitOperationImpl}
 * on durable local repositories. {@code readRepository} is stubbed to hand
 * those directories
 * (instead of cloning), and push is stubbed as a no-op. After instantiate +
 * update through the
 * service API, tests inspect a local merge preview of the update branch into
 * {@code main}.
 */
public class TagBasedThreeWayMergeIT extends BlueprintApplicationIT {

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

    private static final String MAIN = "main";
    private static final String USER_FILE = "user/custom.md";
    private static final String MERGE_FIXTURE = "plain.txt";

    @Autowired
    private GitProviderFactoryMock gitProviderFactoryMock;

    @BeforeEach
    @AfterEach
    void resetGitMocks() {
        gitProviderFactoryMock.reset();
    }

    /**
     * Scenario 1 — pre-existing user files survive update merge preview because the
     * checkpoint
     * baseline is a pure orphan commit produced by Instantiation.
     */
    @Test
    void scenario1_userFilesPreservedAfterInstantiateAndUpdate(@TempDir Path sourceDir, @TempDir Path targetDir)
            throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        Files.writeString(sourceDir.resolve(MERGE_FIXTURE), "blueprint-v1\n");
        initTargetRepositoryWithUserFiles(targetDir, Map.of(USER_FILE, "# user custom\n"));
        stubRealLocalGitOperation(sourceDir, targetDir);

        BlueprintPair context = createBlueprintWithVersions("mesh-dp", "1.0.0", "2.0.0");
        instantiateViaApi(context.blueprintName(), "1.0.0");

        assertThat(targetDir.resolve(USER_FILE)).hasContent("# user custom\n");
        assertThat(listTreePaths(targetDir, BlueprintGitNamingConventions.checkpointTag("1.0.0")))
                .doesNotContain(USER_FILE)
                .contains(MERGE_FIXTURE);

        Files.writeString(sourceDir.resolve(MERGE_FIXTURE), "blueprint-v2\n");
        ResponseEntity<UpdateDataProductResultRes> updateResponse = updateViaApi(context.blueprintName());
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody().getResults().getFirst().getUpdateBranchName())
                .isEqualTo(BlueprintGitNamingConventions.updateBranchName("2.0.0"));

        String updateBranch = BlueprintGitNamingConventions.updateBranchName("2.0.0");
        String checkpointV1 = resolvePeeledTagSha(targetDir, BlueprintGitNamingConventions.checkpointTag("1.0.0"));
        assertThat(resolveMergeBase(targetDir, MAIN, updateBranch)).isEqualTo(checkpointV1);
        assertThat(listTreePaths(targetDir, BlueprintGitNamingConventions.checkpointTag("2.0.0")))
                .doesNotContain(USER_FILE)
                .contains(MERGE_FIXTURE);

        MergePreview preview = previewMerge(targetDir, updateBranch, MAIN);
        assertThat(preview.status().isSuccessful()).isTrue();
        assertThat(preview.status()).isEqualTo(MergeResult.MergeStatus.MERGED_NOT_COMMITTED);
        assertThat(preview.conflicts()).isEmpty();
        assertThat(preview.fileContent(USER_FILE)).isEqualTo("# user custom\n");
        assertThat(preview.fileContent(MERGE_FIXTURE)).isEqualTo("blueprint-v2\n");

        deleteCreatedBlueprint(context.blueprintUuid());
    }

    /**
     * Scenario 2A — non-overlapping line edits on the same file auto-combine in the
     * PR merge preview.
     */
    @Test
    void scenario2a_nonOverlappingLineEditsMergeCleanly(@TempDir Path sourceDir, @TempDir Path targetDir)
            throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        Files.writeString(sourceDir.resolve(MERGE_FIXTURE), """
                line1-v1
                line2-v1
                line3-v1
                """);
        initTargetRepositoryWithUserFiles(targetDir, Map.of(USER_FILE, "keep-me\n"));
        stubRealLocalGitOperation(sourceDir, targetDir);

        BlueprintPair context = createBlueprintWithVersions("mesh-dp", "1.0.0", "2.0.0");
        instantiateViaApi(context.blueprintName(), "1.0.0");

        commitOnMain(targetDir, Map.of(MERGE_FIXTURE, """
                line1-v1
                line2-v1
                line3-user
                """), "user edits line 3");

        Files.writeString(sourceDir.resolve(MERGE_FIXTURE), """
                line1-v2
                line2-v1
                line3-v1
                """);
        assertThat(updateViaApi(context.blueprintName()).getStatusCode()).isEqualTo(HttpStatus.OK);

        String updateBranch = BlueprintGitNamingConventions.updateBranchName("2.0.0");
        assertThat(resolveMergeBase(targetDir, MAIN, updateBranch))
                .isEqualTo(resolvePeeledTagSha(targetDir, BlueprintGitNamingConventions.checkpointTag("1.0.0")));

        MergePreview preview = previewMerge(targetDir, updateBranch, MAIN);
        assertThat(preview.status().isSuccessful()).isTrue();
        assertThat(preview.conflicts()).isEmpty();
        assertThat(preview.fileContent(MERGE_FIXTURE)).isEqualTo("""
                line1-v2
                line2-v1
                line3-user
                """);
        assertThat(preview.fileContent(USER_FILE)).isEqualTo("keep-me\n");

        deleteCreatedBlueprint(context.blueprintUuid());
    }

    /**
     * Scenario 2B — overlapping line edits produce a conflict in the merge preview.
     */
    @Test
    void scenario2b_sameLineEditsProduceMergeConflict(@TempDir Path sourceDir, @TempDir Path targetDir)
            throws Exception {
        writeSourceBlueprintFiles(sourceDir);
        Files.writeString(sourceDir.resolve(MERGE_FIXTURE), """
                line1-v1
                line2-v1
                line3-v1
                """);
        initTargetRepositoryWithUserFiles(targetDir, Map.of(USER_FILE, "keep-me\n"));
        stubRealLocalGitOperation(sourceDir, targetDir);

        BlueprintPair context = createBlueprintWithVersions("mesh-dp", "1.0.0", "2.0.0");
        instantiateViaApi(context.blueprintName(), "1.0.0");

        commitOnMain(targetDir, Map.of(MERGE_FIXTURE, """
                line1-v1
                line2-user
                line3-v1
                """), "user edits line 2");

        Files.writeString(sourceDir.resolve(MERGE_FIXTURE), """
                line1-v1
                line2-v2
                line3-v1
                """);
        assertThat(updateViaApi(context.blueprintName()).getStatusCode()).isEqualTo(HttpStatus.OK);

        String updateBranch = BlueprintGitNamingConventions.updateBranchName("2.0.0");
        MergePreview preview = previewMerge(targetDir, updateBranch, MAIN);

        assertThat(preview.status()).isEqualTo(MergeResult.MergeStatus.CONFLICTING);
        assertThat(preview.conflicts()).containsKey(MERGE_FIXTURE);
        assertThat(preview.conflicts()).doesNotContainKey(USER_FILE);

        deleteCreatedBlueprint(context.blueprintUuid());
    }

    // --- Git mock: real local ops, stubbed clone/push ---

    private void stubRealLocalGitOperation(Path sourceDir, Path targetDir) {
        GitCredentialHttps credential = new GitCredentialHttps();
        HttpHeaders headers = new HttpHeaders();
        headers.add("username", "user");
        headers.add("password", "token");
        credential.setHttpAuthHeaders(headers);

        GitOperation realGit = new GitOperationImpl(credential);
        GitOperation gitOperation = Mockito.mock(GitOperation.class, AdditionalAnswers.delegatesTo(realGit));

        doAnswer(invocation -> {
            RepositoryPointer pointer = invocation.getArgument(1);
            Consumer<File> consumer = invocation.getArgument(2);
            if (pointer instanceof RepositoryPointerBranch branchPointer) {
                checkoutRef(targetDir, branchPointer.getRefValue());
                consumer.accept(targetDir.toFile());
                return null;
            }
            if (pointer instanceof RepositoryPointerTag tagPointer) {
                String tag = tagPointer.getRefValue();
                if (tag.startsWith("blueprint-v")) {
                    checkoutRef(targetDir, tag);
                    consumer.accept(targetDir.toFile());
                } else {
                    // Blueprint source release tag (v1.0.0 / v2.0.0): serve the local source tree.
                    consumer.accept(sourceDir.toFile());
                }
                return null;
            }
            throw new IllegalArgumentException("Unexpected repository pointer: " + pointer);
        }).when(gitOperation).readRepository(any(), any(), any());

        doNothing().when(gitOperation).pushBranch(any(), anyString());
        doNothing().when(gitOperation).pushTag(any(), anyString());

        GitProvider mockGitProvider = gitProviderFactoryMock.getMockGitProvider();
        when(mockGitProvider.gitOperation()).thenReturn(gitOperation);
        when(mockGitProvider.getRepository(anyString(), anyString())).thenReturn(Optional.of(new Repository()));
    }

    private void initTargetRepositoryWithUserFiles(Path targetDir, Map<String, String> userFiles) throws Exception {
        Path bareRemote = targetDir.getParent().resolve(targetDir.getFileName() + "-remote.git");
        try (Git ignored = Git.init().setBare(true).setDirectory(bareRemote.toFile()).call()) {
            // bare origin for createAndCheckout* remote-collision checks
        }

        try (Git git = Git.init().setInitialBranch(MAIN).setDirectory(targetDir.toFile()).call()) {
            StoredConfig config = git.getRepository().getConfig();
            config.setString("user", null, "name", BlueprintGitNamingConventions.DEFAULT_COMMIT_AUTHOR_NAME);
            config.setString("user", null, "email", BlueprintGitNamingConventions.DEFAULT_COMMIT_AUTHOR_EMAIL);
            config.save();
            git.remoteAdd()
                    .setName(Constants.DEFAULT_REMOTE_NAME)
                    .setUri(new URIish(bareRemote.toUri().toString()))
                    .call();

            writeFiles(targetDir, userFiles);
            git.add().addFilepattern(".").call();
            git.commit().setMessage("seed user files on main").call();
        }
    }

    private void instantiateViaApi(String blueprintName, String version) {
        InstantiateBlueprintVersionCommandRes request = new InstantiateBlueprintVersionCommandRes();
        request.setBlueprintName(blueprintName);
        request.setBlueprintVersionNumber(version);

        InstantiateBlueprintVersionTargetRepositoryRes target = new InstantiateBlueprintVersionTargetRepositoryRes();
        target.setTargetId(DEFAULT_REPOSITORY_KEY);
        target.setRepository(buildTargetRepository());
        request.setTargetRepositories(List.of(target));

        Map<String, JsonNode> parameters = new LinkedHashMap<>();
        parameters.put("environment", OBJECT_MAPPER.valueToTree("prod"));
        parameters.put("retentionDays", OBJECT_MAPPER.valueToTree(365));
        request.setParameters(parameters);

        ResponseEntity<InstantiateBlueprintVersionResponseRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_INSTANTIATE),
                HttpMethod.POST,
                new HttpEntity<>(request, jsonHeaders()),
                InstantiateBlueprintVersionResponseRes.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<UpdateDataProductResultRes> updateViaApi(String blueprintName) {
        UpdateDataProductCommandRes request = new UpdateDataProductCommandRes();
        request.setBlueprintName(blueprintName);
        request.setCurrentVersionNumber("1.0.0");
        request.setNextVersionNumber("2.0.0");
        request.setCreatePullRequest(false);

        UpdateDataProductTargetRepositoryRes target = new UpdateDataProductTargetRepositoryRes();
        target.setTargetId(DEFAULT_REPOSITORY_KEY);
        target.setRepository(buildTargetRepository());
        request.setTargetRepositories(List.of(target));

        Map<String, JsonNode> parameters = new LinkedHashMap<>();
        parameters.put("environment", OBJECT_MAPPER.valueToTree("prod"));
        parameters.put("retentionDays", OBJECT_MAPPER.valueToTree(365));
        request.setParameters(parameters);

        return rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS_UPDATE_DATA_PRODUCT),
                HttpMethod.POST,
                new HttpEntity<>(request, jsonHeaders()),
                UpdateDataProductResultRes.class);
    }

    private void commitOnMain(Path repoDir, Map<String, String> files, String message) throws Exception {
        try (Git git = Git.open(repoDir.toFile())) {
            git.checkout().setName(MAIN).call();
            writeFiles(repoDir, files);
            git.add().addFilepattern(".").call();
            git.commit().setMessage(message).call();
        }
    }

    private MergePreview previewMerge(Path repoDir, String sourceBranch, String targetBranch) throws Exception {
        try (Git git = Git.open(repoDir.toFile())) {
            ObjectId targetTip = git.getRepository().resolve(Constants.R_HEADS + targetBranch);
            assertThat(targetTip).as("target branch tip").isNotNull();

            git.checkout().setName(targetBranch).call();
            MergeResult result = git.merge()
                    .include(git.getRepository().resolve(sourceBranch))
                    .setCommit(false)
                    .setFastForward(MergeCommand.FastForwardMode.NO_FF)
                    .call();

            Map<String, String> contents = new LinkedHashMap<>();
            Set<String> paths = listWorkTreePaths(repoDir);
            for (String path : paths) {
                Path file = repoDir.resolve(path);
                if (Files.isRegularFile(file)) {
                    contents.put(path, Files.readString(file));
                }
            }

            Map<String, int[][]> conflicts = result.getConflicts() == null
                    ? Map.of()
                    : Map.copyOf(result.getConflicts());
            MergePreview preview = new MergePreview(result.getMergeStatus(), conflicts, contents);

            git.reset().setMode(ResetCommand.ResetType.HARD).setRef(targetTip.getName()).call();
            assertThat(git.getRepository().getRepositoryState()).isEqualTo(RepositoryState.SAFE);
            assertThat(git.status().call().isClean()).isTrue();
            return preview;
        }
    }

    private void checkoutRef(Path repoDir, String ref) throws Exception {
        try (Git git = Git.open(repoDir.toFile())) {
            git.checkout().setName(ref).call();
        }
    }

    private String resolveMergeBase(Path repoDir, String refA, String refB) throws Exception {
        try (Git git = Git.open(repoDir.toFile());
                RevWalk walk = new RevWalk(git.getRepository())) {
            RevCommit a = walk.parseCommit(git.getRepository().resolve(refA));
            RevCommit b = walk.parseCommit(git.getRepository().resolve(refB));
            walk.setRevFilter(RevFilter.MERGE_BASE);
            walk.markStart(a);
            walk.markStart(b);
            RevCommit base = walk.next();
            assertThat(base).as("merge-base of %s and %s", refA, refB).isNotNull();
            return base.getName();
        }
    }

    private String resolvePeeledTagSha(Path repoDir, String tagName) throws Exception {
        try (Git git = Git.open(repoDir.toFile())) {
            var tag = git.getRepository().exactRef(Constants.R_TAGS + tagName);
            assertThat(tag).as("tag %s", tagName).isNotNull();
            var peeled = git.getRepository().getRefDatabase().peel(tag);
            ObjectId target = peeled.getPeeledObjectId() == null ? peeled.getObjectId() : peeled.getPeeledObjectId();
            return target.getName();
        }
    }

    private Set<String> listTreePaths(Path repoDir, String ref) throws Exception {
        Set<String> paths = new LinkedHashSet<>();
        try (Git git = Git.open(repoDir.toFile());
                RevWalk walk = new RevWalk(git.getRepository());
                TreeWalk treeWalk = new TreeWalk(git.getRepository())) {
            RevCommit commit = walk.parseCommit(git.getRepository().resolve(ref));
            treeWalk.addTree(commit.getTree());
            treeWalk.setRecursive(true);
            while (treeWalk.next()) {
                paths.add(treeWalk.getPathString());
            }
        }
        return paths;
    }

    private Set<String> listWorkTreePaths(Path workTree) throws IOException {
        Set<String> paths = new LinkedHashSet<>();
        try (var stream = Files.walk(workTree)) {
            Path gitDir = workTree.resolve(".git").toAbsolutePath().normalize();
            stream.filter(Files::isRegularFile)
                    .filter(path -> !path.toAbsolutePath().normalize().startsWith(gitDir))
                    .forEach(path -> paths.add(workTree.relativize(path).toString().replace('\\', '/')));
        }
        return paths;
    }

    private void writeFiles(Path repoDir, Map<String, String> files) throws IOException {
        for (Map.Entry<String, String> entry : files.entrySet()) {
            Path target = repoDir.resolve(entry.getKey());
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.writeString(target, entry.getValue());
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

    private JsonNode manifestMonorepoNoComposition() throws Exception {
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("manifest/example-2.1-monorepo-no-composition.yaml")) {
            return YAML_OBJECT_MAPPER.readTree(inputStream);
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

    private record MergePreview(
            MergeResult.MergeStatus status,
            Map<String, int[][]> conflicts,
            Map<String, String> fileContents) {
        String fileContent(String path) {
            return fileContents.get(path);
        }
    }
}
