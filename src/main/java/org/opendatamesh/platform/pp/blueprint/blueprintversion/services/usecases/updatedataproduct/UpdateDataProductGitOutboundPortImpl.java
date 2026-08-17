package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.updatedataproduct;

import org.opendatamesh.platform.git.exceptions.GitOperationException;
import org.opendatamesh.platform.git.model.Commit;
import org.opendatamesh.platform.git.model.CreatePullRequest;
import org.opendatamesh.platform.git.model.PullRequest;
import org.opendatamesh.platform.git.model.Repository;
import org.opendatamesh.platform.git.model.RepositoryPointerTag;
import org.opendatamesh.platform.git.model.Tag;
import org.opendatamesh.platform.git.provider.GitProvider;
import org.opendatamesh.platform.git.provider.GitProviderIdentifier;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.BlueprintGitNamingConventions;
import org.opendatamesh.platform.pp.blueprint.git.provider.GitProviderFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.function.BiConsumer;

class UpdateDataProductGitOutboundPortImpl implements UpdateDataProductGitOutboundPort {

    private final HttpHeaders gitProviderHttpHeaders;
    private final GitProviderFactory gitProviderFactory;
    private GitProvider gitProvider;

    UpdateDataProductGitOutboundPortImpl(HttpHeaders gitProviderHttpHeaders, GitProviderFactory gitProviderFactory) {
        this.gitProviderHttpHeaders = gitProviderHttpHeaders;
        this.gitProviderFactory = gitProviderFactory;
    }

    @Override
    public void init(Blueprint blueprint) {
        gitProvider = gitProviderFactory.buildGitProvider(
                new GitProviderIdentifier(
                        blueprint.getBlueprintRepo().getProviderType().name(),
                        blueprint.getBlueprintRepo().getProviderBaseUrl()),
                gitProviderHttpHeaders);
    }

    @Override
    public void withClonedSourceAndTargetAtCheckpoint(
            Repository sourceRepository,
            String sourceTag,
            Repository targetRepository,
            String currentCheckpointTag,
            BiConsumer<Path, Path> operation) {
        gitProvider.gitOperation().readRepository(
                targetRepository,
                new RepositoryPointerTag(currentCheckpointTag),
                targetRepoDir -> gitProvider.gitOperation().readRepository(
                        sourceRepository,
                        new RepositoryPointerTag(sourceTag),
                        sourceRepoDir -> operation.accept(sourceRepoDir.toPath(), targetRepoDir.toPath())));
    }

    @Override
    public void createAndCheckoutBranch(Path targetRepository, String branchName) {
        gitProvider.gitOperation().createAndCheckoutBranch(targetRepository.toFile(), branchName);
    }

    @Override
    public void cleanWorkingTreePreservingGit(Path targetRepository) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(targetRepository)) {
            for (Path entry : stream) {
                if (".git".equals(entry.getFileName().toString())) {
                    continue;
                }
                deleteRecursively(entry);
            }
        } catch (IOException e) {
            throw new GitOperationException(
                    "cleanWorkingTree",
                    "Failed to clean working tree while preserving .git: " + e.getMessage(),
                    e);
        }
    }

    @Override
    public String commitAll(
            Path targetRepository,
            String branchName,
            String commitMessage,
            String commitAuthorName,
            String commitAuthorEmail) {
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
            String commitAuthorEmail) {
        Tag tag = new Tag(
                checkpointTag,
                commitHash,
                resolveAuthorName(commitAuthorName),
                resolveAuthorEmail(commitAuthorEmail),
                "Checkpoint " + checkpointTag);
        gitProvider.gitOperation().addTag(targetRepository.toFile(), tag);
    }

    @Override
    public void pushBranch(Path targetRepository, String branchName) {
        gitProvider.gitOperation().pushBranch(targetRepository.toFile(), branchName);
    }

    @Override
    public void pushTag(Path targetRepository, String tagName) {
        gitProvider.gitOperation().pushTag(targetRepository.toFile(), tagName);
    }

    @Override
    public String openPullRequest(
            Repository repository,
            String sourceBranch,
            String targetBranch,
            String title,
            String body
    ) {
        CreatePullRequest createPullRequest = new CreatePullRequest(sourceBranch, targetBranch, title, body);
        PullRequest pullRequest = gitProvider.createPullRequest(repository, createPullRequest);
        return pullRequest != null ? pullRequest.getWebUrl() : null;
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

    private void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
