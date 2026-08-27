package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources;

import org.opendatamesh.platform.git.model.Repository;
import org.opendatamesh.platform.git.model.RepositoryPointerTag;
import org.opendatamesh.platform.git.provider.GitProvider;
import org.opendatamesh.platform.git.provider.GitProviderIdentifier;
import org.opendatamesh.platform.pp.blueprint.git.provider.GitProviderFactory;
import org.opendatamesh.platform.pp.blueprint.validator.config.BlueprintValidatorProperties;
import org.opendatamesh.platform.pp.blueprint.validator.config.ValidatorGitCredentialHeaders;
import org.springframework.http.HttpHeaders;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.stream.Stream;

class EvaluateProtectedResourcesIntegrityGitOutboundPortImpl
        implements EvaluateProtectedResourcesIntegrityGitOutboundPort {

    private final GitProviderFactory gitProviderFactory;
    private final BlueprintValidatorProperties validatorProperties;

    EvaluateProtectedResourcesIntegrityGitOutboundPortImpl(
            GitProviderFactory gitProviderFactory,
            BlueprintValidatorProperties validatorProperties
    ) {
        this.gitProviderFactory = gitProviderFactory;
        this.validatorProperties = validatorProperties;
    }

    @Override
    public WorkingTree clonePublishedDataProductVersion(ProductRepoLocator repo, String tag) {
        HttpHeaders credentials = resolveProductCredentials(repo);
        Path publishedTree = copyPublishedTreeAtTag(repo, tag, credentials);
        return new CloseableWorkingTree(publishedTree);
    }

    private HttpHeaders resolveProductCredentials(ProductRepoLocator repo) {
        return ValidatorGitCredentialHeaders.resolve(
                        validatorProperties,
                        repo.providerType(),
                        repo.providerBaseUrl())
                .orElseThrow(() -> new IllegalStateException(
                        "Cannot check protected resources: Git access is not configured for provider "
                                + repo.providerType()));
    }

    private Path copyPublishedTreeAtTag(ProductRepoLocator repo, String tag, HttpHeaders credentials) {
        Path publishedTree = createTempDirectory();
        try {
            GitProvider gitProvider = gitProviderFactory.buildGitProvider(
                    new GitProviderIdentifier(repo.providerType(), repo.providerBaseUrl()),
                    credentials
            );
            gitProvider.gitOperation().readRepository(
                    toGitRepository(repo),
                    new RepositoryPointerTag(tag),
                    cloneDir -> copyTreeSkippingGit(cloneDir.toPath(), publishedTree)
            );
            return publishedTree;
        } catch (RuntimeException e) {
            deleteRecursively(publishedTree);
            throw e;
        }
    }

    private Path createTempDirectory() {
        try {
            return Files.createTempDirectory("blueprint-integrity-actual-");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to copy the data product version files", e);
        }
    }

    private void copyTreeSkippingGit(Path source, Path destination) {
        try {
            Files.walkFileTree(source, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (dir.getFileName() != null && ".git".equals(dir.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    Path relative = source.relativize(dir);
                    if (!relative.toString().isEmpty()) {
                        Files.createDirectories(destination.resolve(relative));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (Files.isSymbolicLink(file) || !Files.isRegularFile(file)) {
                        return FileVisitResult.CONTINUE;
                    }
                    Path target = destination.resolve(source.relativize(file));
                    if (target.getParent() != null) {
                        Files.createDirectories(target.getParent());
                    }
                    Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to copy the data product version files", e);
        }
    }

    private Repository toGitRepository(ProductRepoLocator repo) {
        Repository repository = new Repository();
        repository.setId(repo.externalIdentifier());
        repository.setName(repo.name());
        repository.setDefaultBranch(repo.defaultBranch());
        repository.setOwnerId(repo.ownerId());
        repository.setCloneUrlHttp(repo.remoteUrlHttp());
        return repository;
    }

    private static void deleteRecursively(Path path) {
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

    private static final class CloseableWorkingTree implements WorkingTree {
        private final Path root;

        private CloseableWorkingTree(Path root) {
            this.root = root;
        }

        @Override
        public Path path() {
            return root;
        }

        @Override
        public void close() {
            deleteRecursively(root);
        }
    }
}
