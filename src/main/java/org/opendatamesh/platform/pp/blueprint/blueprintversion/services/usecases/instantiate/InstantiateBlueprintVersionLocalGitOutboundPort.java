package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.dircache.DirCache;
import org.opendatamesh.platform.git.model.Commit;
import org.opendatamesh.platform.git.model.RepositoryPointerTag;
import org.opendatamesh.platform.git.model.Tag;
import org.opendatamesh.platform.git.provider.GitProvider;
import org.opendatamesh.platform.git.provider.GitProviderIdentifier;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.BlueprintGitNamingConventions;
import org.opendatamesh.platform.pp.blueprint.exceptions.InternalException;
import org.opendatamesh.platform.pp.blueprint.git.provider.GitProviderFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Produces expected rendered trees for protected-resources hashing without cloning
 * live product integration branches and without pushing. {@code pushBranch}/{@code pushTag}
 * are no-ops so {@link InstantiateBlueprintVersion} can run unchanged.
 */
class InstantiateBlueprintVersionLocalGitOutboundPort implements InstantiateBlueprintVersionGitOutboundPort {

    private final HttpHeaders gitProviderHttpHeaders;
    private final GitProviderFactory gitProviderFactory;
    private final RenderedTreeSnapshot snapshot;
    private GitProvider gitProvider;

    InstantiateBlueprintVersionLocalGitOutboundPort(
            HttpHeaders gitProviderHttpHeaders,
            GitProviderFactory gitProviderFactory,
            RenderedTreeSnapshot snapshot
    ) {
        this.gitProviderHttpHeaders = gitProviderHttpHeaders;
        this.gitProviderFactory = gitProviderFactory;
        this.snapshot = snapshot;
    }

    @Override
    public void openSources(
            Blueprint parentBlueprint,
            List<SourceRepositoryDto> sources,
            Consumer<Map<String, Path>> operation) {
        initGitProvider(parentBlueprint);
        List<SourceRepositoryDto> uniqueSources = dedupeSources(sources);
        openSourcesRecursively(uniqueSources, 0, new LinkedHashMap<>(), operation);
    }

    @Override
    public void openTarget(
            TargetRepositoryDto target,
            String integrationBranch,
            Consumer<Path> operation) {
        Path throwawayTarget = createTempDirectory("blueprint-integrity-throwaway-target-");
        try {
            initEmptyGitRepo(throwawayTarget, integrationBranch);
            operation.accept(throwawayTarget);
            snapshot.putExpectedTree(target.targetId(), copyWorkingTreeSkippingGit(throwawayTarget));
        } finally {
            deleteRecursively(throwawayTarget);
        }
    }

    private void openSourcesRecursively(
            List<SourceRepositoryDto> sources,
            int index,
            Map<String, Path> sourcePaths,
            Consumer<Map<String, Path>> operation) {
        if (index >= sources.size()) {
            operation.accept(Map.copyOf(sourcePaths));
            return;
        }
        SourceRepositoryDto source = sources.get(index);
        gitProvider.gitOperation().readRepository(
                source.repository(),
                new RepositoryPointerTag(source.tag()),
                sourceRepoDir -> {
                    sourcePaths.put(source.id(), sourceRepoDir.toPath());
                    openSourcesRecursively(sources, index + 1, sourcePaths, operation);
                });
    }

    private void initGitProvider(Blueprint parentBlueprint) {
        if (gitProvider != null) {
            return;
        }
        if (parentBlueprint == null || parentBlueprint.getBlueprintRepo() == null) {
            throw new InternalException("Parent blueprint repository is required to bind a Git provider");
        }
        gitProvider = gitProviderFactory.buildGitProvider(
                new GitProviderIdentifier(
                        parentBlueprint.getBlueprintRepo().getProviderType().name(),
                        parentBlueprint.getBlueprintRepo().getProviderBaseUrl()),
                gitProviderHttpHeaders);
    }

    private static List<SourceRepositoryDto> dedupeSources(List<SourceRepositoryDto> sources) {
        if (sources == null || sources.isEmpty()) {
            throw new InternalException("At least one source repository is required for instantiation");
        }
        Map<String, SourceRepositoryDto> byId = new LinkedHashMap<>();
        for (SourceRepositoryDto source : sources) {
            if (source == null || !StringUtils.hasText(source.id())) {
                throw new InternalException("Each source repository must have a non-empty id");
            }
            byId.putIfAbsent(source.id(), source);
        }
        return new ArrayList<>(byId.values());
    }

    @Override
    public void createAndCheckoutOrphanBranch(Path targetRepository, String branchName) {
        // git-utils createAndCheckoutOrphanBranch also ls-remotes origin to refuse name
        // collisions. This throwaway repo is Git.init() with no origin, so checkout locally.
        try (Git git = Git.open(targetRepository.toFile())) {
            git.checkout()
                    .setOrphan(true)
                    .setName(branchName)
                    .call();
            clearIndex(git);
        } catch (Exception e) {
            throw new InternalException(
                    "Failed to create local orphan branch for protected-resources validation", e);
        }
    }

    private static void clearIndex(Git git) throws IOException {
        DirCache index = git.getRepository().lockDirCache();
        boolean committed = false;
        try {
            index.clear();
            index.write();
            committed = index.commit();
        } finally {
            if (!committed) {
                index.unlock();
            }
        }
        if (!committed) {
            throw new InternalException("Cannot replace the repository index for local validation");
        }
    }

    @Override
    public String commitAll(
            Path targetRepository,
            String branchName,
            String commitMessage,
            String commitAuthorName,
            String commitAuthorEmail
    ) {
        File repoDir = targetRepository.toFile();
        gitProvider.gitOperation().addAll(repoDir);
        gitProvider.gitOperation().commit(repoDir, new Commit(
                commitMessage,
                resolveAuthorName(commitAuthorName),
                resolveAuthorEmail(commitAuthorEmail)));
        return gitProvider.gitOperation().getHeadSha(repoDir, branchName);
    }

    @Override
    public void createCheckpointTag(
            Path targetRepository,
            String checkpointTag,
            String commitHash,
            String commitAuthorName,
            String commitAuthorEmail
    ) {
        Tag tag = new Tag(
                checkpointTag,
                commitHash,
                resolveAuthorName(commitAuthorName),
                resolveAuthorEmail(commitAuthorEmail),
                "Checkpoint " + checkpointTag);
        gitProvider.gitOperation().addTag(targetRepository.toFile(), tag);
    }

    @Override
    public void mergeBranch(Path targetRepository, String sourceBranch, String targetBranch) {
        gitProvider.gitOperation().mergeBranch(targetRepository.toFile(), sourceBranch, targetBranch);
    }

    @Override
    public void pushBranch(Path targetRepository, String branchName) {
        // no-op: policy validation must not mutate remotes
    }

    @Override
    public void pushTag(Path targetRepository, String tagName) {
        // no-op: policy validation must not mutate remotes
    }

    private void initEmptyGitRepo(Path dir, String branch) {
        try (Git git = Git.init().setInitialBranch(branch).setDirectory(dir.toFile()).call()) {
            git.commit()
                    .setAuthor(
                            BlueprintGitNamingConventions.DEFAULT_COMMIT_AUTHOR_NAME,
                            BlueprintGitNamingConventions.DEFAULT_COMMIT_AUTHOR_EMAIL)
                    .setMessage("Empty integration branch for local protected-resources validation")
                    .setAllowEmpty(true)
                    .call();
        } catch (Exception e) {
            throw new InternalException("Failed to create throwaway Git repository for local validation", e);
        }
    }

    private Path copyWorkingTreeSkippingGit(Path sourceRoot) {
        Path snapshotRoot = createTempDirectory("blueprint-integrity-expected-snapshot-");
        try {
            Files.walkFileTree(sourceRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (dir.getFileName() != null && ".git".equals(dir.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (Files.isSymbolicLink(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    Path relative = sourceRoot.relativize(dir);
                    if (!relative.toString().isEmpty()) {
                        Files.createDirectories(snapshotRoot.resolve(relative));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (Files.isSymbolicLink(file) || !Files.isRegularFile(file)) {
                        return FileVisitResult.CONTINUE;
                    }
                    Path destination = snapshotRoot.resolve(sourceRoot.relativize(file));
                    if (destination.getParent() != null) {
                        Files.createDirectories(destination.getParent());
                    }
                    Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING);
                    return FileVisitResult.CONTINUE;
                }
            });
            return snapshotRoot;
        } catch (IOException e) {
            deleteRecursively(snapshotRoot);
            throw new InternalException("Failed to snapshot locally re-instantiated tree", e);
        }
    }

    private Path createTempDirectory(String prefix) {
        try {
            return Files.createTempDirectory(prefix);
        } catch (IOException e) {
            throw new InternalException("Failed to create temp directory for local validation", e);
        }
    }

    static void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            });
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }

    private String resolveAuthorName(String commitAuthorName) {
        return StringUtils.hasText(commitAuthorName)
                ? commitAuthorName
                : BlueprintGitNamingConventions.DEFAULT_COMMIT_AUTHOR_NAME;
    }

    private String resolveAuthorEmail(String commitAuthorEmail) {
        return StringUtils.hasText(commitAuthorEmail)
                ? commitAuthorEmail
                : BlueprintGitNamingConventions.DEFAULT_COMMIT_AUTHOR_EMAIL;
    }
}
