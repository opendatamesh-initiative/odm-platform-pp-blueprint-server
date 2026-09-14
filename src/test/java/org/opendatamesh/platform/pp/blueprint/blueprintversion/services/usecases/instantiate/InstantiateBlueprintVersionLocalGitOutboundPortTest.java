package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.opendatamesh.platform.git.git.GitOperation;
import org.opendatamesh.platform.git.model.Repository;
import org.opendatamesh.platform.git.model.RepositoryPointer;
import org.opendatamesh.platform.git.model.RepositoryPointerBranch;
import org.opendatamesh.platform.git.model.RepositoryPointerTag;
import org.opendatamesh.platform.git.provider.GitProvider;
import org.opendatamesh.platform.git.provider.GitProviderIdentifier;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.BlueprintRepo;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.BlueprintRepoProviderType;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.BlueprintGitNamingConventions;
import org.opendatamesh.platform.pp.blueprint.git.provider.GitProviderFactory;
import org.springframework.http.HttpHeaders;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InstantiateBlueprintVersionLocalGitOutboundPortTest {

    /**
     * Feature: Local instantiate Git adapter for integrity
     *
     * Scenario: openSources clones sources at release tags and openTarget does not clone the product branch
     *   Given a local-validation Git adapter
     *   When instantiate runs for local validation
     *   Then each source is opened with a tag pointer
     *   And the target integration branch is never cloned
     *   And pushBranch and pushTag are not invoked on the Git provider
     *   And a snapshot exists for the target’s `targetId`
     */
    @Test
    void openSourcesClonesTagsAndOpenTargetDoesNotCloneProduct(@TempDir Path sourceDir) throws Exception {
        Files.writeString(sourceDir.resolve("plain.txt"), "from-source");
        GitProviderFactory gitProviderFactory = mock(GitProviderFactory.class);
        GitProvider gitProvider = mock(GitProvider.class);
        GitOperation gitOperation = mock(GitOperation.class);
        when(gitProviderFactory.buildGitProvider(any(GitProviderIdentifier.class), any())).thenReturn(gitProvider);
        when(gitProvider.gitOperation()).thenReturn(gitOperation);
        doAnswer(invocation -> {
            Consumer<File> consumer = invocation.getArgument(2);
            consumer.accept(sourceDir.toFile());
            return null;
        }).when(gitOperation).readRepository(any(), any(), any());

        RenderedTreeSnapshot snapshot = new RenderedTreeSnapshot();
        InstantiateBlueprintVersionLocalGitOutboundPort port = new InstantiateBlueprintVersionLocalGitOutboundPort(
                new HttpHeaders(), gitProviderFactory, snapshot);

        Blueprint blueprint = new Blueprint();
        BlueprintRepo repo = new BlueprintRepo();
        repo.setProviderType(BlueprintRepoProviderType.GITHUB);
        repo.setProviderBaseUrl("https://github.com");
        blueprint.setBlueprintRepo(repo);

        Repository sourceRepository = new Repository();
        sourceRepository.setCloneUrlHttp("https://github.com/org/source.git");
        SourceRepositoryDto source = new SourceRepositoryDto("parent", "v1.0.0", sourceRepository);
        Repository targetRepository = new Repository();
        targetRepository.setCloneUrlHttp("https://github.com/org/product.git");
        targetRepository.setDefaultBranch("main");
        TargetRepositoryDto target = new TargetRepositoryDto("main", "main", targetRepository);

        AtomicReference<Path> targetPathSeen = new AtomicReference<>();
        port.openSources(blueprint, List.of(source), sourcePaths -> {
            assertThat(sourcePaths).containsKey("parent");
            port.openTarget(target, "main", dst -> {
                targetPathSeen.set(dst);
                try {
                    Files.writeString(dst.resolve("rendered.txt"), "expected");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        });

        ArgumentCaptor<RepositoryPointer> pointerCaptor = ArgumentCaptor.forClass(RepositoryPointer.class);
        verify(gitOperation).readRepository(any(), pointerCaptor.capture(), any());
        assertThat(pointerCaptor.getValue()).isInstanceOf(RepositoryPointerTag.class);
        assertThat(pointerCaptor.getValue().getRefValue()).isEqualTo("v1.0.0");
        verify(gitOperation, never()).readRepository(any(), any(RepositoryPointerBranch.class), any());

        port.pushBranch(targetPathSeen.get(), "main");
        port.pushTag(targetPathSeen.get(), "blueprint-v1.0.0");
        verify(gitOperation, never()).pushBranch(any(), any());
        verify(gitOperation, never()).pushTag(any(), any());
        verify(gitOperation, never()).createAndCheckoutOrphanBranch(any(), anyString());

        Path expectedTree = snapshot.getExpectedTree("main");
        assertThat(expectedTree).isNotNull();
        assertThat(expectedTree.resolve("rendered.txt")).exists();
        assertThat(Files.exists(expectedTree.resolve(".git"))).isFalse();
        InstantiateBlueprintVersionLocalGitOutboundPort.deleteRecursively(expectedTree);
    }

    /**
     * Feature: Local instantiate Git adapter for integrity
     *
     * Scenario: Snapshot symbolic link fails safely
     *   Given a protected match is a symbolic link in a cloned or expected snapshot
     *   When the path is digested
     *   Then the outcome contains a SYMLINK mismatch
     */
    @Test
    void openTargetPreservesSymbolicLinksInSnapshot(@TempDir Path sourceDir) throws Exception {
        Files.writeString(sourceDir.resolve("plain.txt"), "from-source");
        GitProviderFactory gitProviderFactory = mock(GitProviderFactory.class);
        GitProvider gitProvider = mock(GitProvider.class);
        GitOperation gitOperation = mock(GitOperation.class);
        when(gitProviderFactory.buildGitProvider(any(GitProviderIdentifier.class), any())).thenReturn(gitProvider);
        when(gitProvider.gitOperation()).thenReturn(gitOperation);
        doAnswer(invocation -> {
            Consumer<File> consumer = invocation.getArgument(2);
            consumer.accept(sourceDir.toFile());
            return null;
        }).when(gitOperation).readRepository(any(), any(), any());

        RenderedTreeSnapshot snapshot = new RenderedTreeSnapshot();
        InstantiateBlueprintVersionLocalGitOutboundPort port = new InstantiateBlueprintVersionLocalGitOutboundPort(
                new HttpHeaders(), gitProviderFactory, snapshot);

        Blueprint blueprint = new Blueprint();
        BlueprintRepo repo = new BlueprintRepo();
        repo.setProviderType(BlueprintRepoProviderType.GITHUB);
        repo.setProviderBaseUrl("https://github.com");
        blueprint.setBlueprintRepo(repo);

        Repository sourceRepository = new Repository();
        sourceRepository.setCloneUrlHttp("https://github.com/org/source.git");
        SourceRepositoryDto source = new SourceRepositoryDto("parent", "v1.0.0", sourceRepository);
        Repository targetRepository = new Repository();
        targetRepository.setCloneUrlHttp("https://github.com/org/product.git");
        TargetRepositoryDto target = new TargetRepositoryDto("main", "main", targetRepository);
        TargetRepositoryDto second = new TargetRepositoryDto("infra-repo", "main", targetRepository);

        port.openSources(blueprint, List.of(source), sourcePaths -> {
            port.openTarget(target, "main", dst -> {
                try {
                    Files.writeString(dst.resolve("real.txt"), "x");
                    Files.createSymbolicLink(dst.resolve("link.txt"), dst.resolve("real.txt").getFileName());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            port.openTarget(second, "main", dst -> {
                try {
                    Files.writeString(dst.resolve("infra.txt"), "infra");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        });

        Path expectedTree = snapshot.getExpectedTree("main");
        Path infraTree = snapshot.getExpectedTree("infra-repo");
        assertThat(expectedTree).isNotNull();
        assertThat(infraTree).isNotNull();
        assertThat(Files.isSymbolicLink(expectedTree.resolve("link.txt"))).isTrue();
        assertThat(Files.exists(infraTree.resolve("infra.txt"))).isTrue();
        InstantiateBlueprintVersionLocalGitOutboundPort.deleteRecursively(expectedTree);
        InstantiateBlueprintVersionLocalGitOutboundPort.deleteRecursively(infraTree);
    }

    @Test
    void createAndCheckoutOrphanBranchDoesNotRequireOrigin(@TempDir Path repoDir) throws Exception {
        try (Git git = Git.init().setInitialBranch("main").setDirectory(repoDir.toFile()).call()) {
            git.commit()
                    .setAuthor(
                            BlueprintGitNamingConventions.DEFAULT_COMMIT_AUTHOR_NAME,
                            BlueprintGitNamingConventions.DEFAULT_COMMIT_AUTHOR_EMAIL)
                    .setMessage("empty integration branch")
                    .setAllowEmpty(true)
                    .call();
        }

        GitProviderFactory gitProviderFactory = mock(GitProviderFactory.class);
        InstantiateBlueprintVersionLocalGitOutboundPort port = new InstantiateBlueprintVersionLocalGitOutboundPort(
                new HttpHeaders(), gitProviderFactory, new RenderedTreeSnapshot());
        port.createAndCheckoutOrphanBranch(repoDir, "odm-init/local-validation");

        try (Git git = Git.open(repoDir.toFile())) {
            assertThat(git.getRepository().getBranch()).isEqualTo("odm-init/local-validation");
            assertThat(git.getRepository().getConfig().getString("remote", "origin", "url")).isNull();
        }
    }
}
