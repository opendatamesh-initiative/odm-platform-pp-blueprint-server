package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.updatedataproduct;

import org.opendatamesh.platform.git.exceptions.GitOperationException;
import org.opendatamesh.platform.git.model.*;
import org.opendatamesh.platform.git.provider.GitProvider;
import org.opendatamesh.platform.git.provider.GitProviderIdentifier;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.BlueprintGitNamingConventions;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate.SourceRepositoryDto;
import org.opendatamesh.platform.pp.blueprint.exceptions.InternalException;
import org.opendatamesh.platform.pp.blueprint.git.provider.GitProviderFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

class UpdateDataProductGitOutboundPortImpl implements UpdateDataProductGitOutboundPort {

    private final HttpHeaders gitProviderHttpHeaders;
    private final GitProviderFactory gitProviderFactory;
    private GitProvider gitProvider;

    UpdateDataProductGitOutboundPortImpl(HttpHeaders gitProviderHttpHeaders, GitProviderFactory gitProviderFactory) {
        this.gitProviderHttpHeaders = gitProviderHttpHeaders;
        this.gitProviderFactory = gitProviderFactory;
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
    public void openTargetAtCheckpoint(
            UpdateDataProductTargetRepositoryDto target,
            String currentCheckpointTag,
            Consumer<Path> operation) {
        gitProvider.gitOperation().readRepository(
                target.repository(),
                new RepositoryPointerTag(currentCheckpointTag),
                targetRepoDir -> operation.accept(targetRepoDir.toPath()));
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

    private List<SourceRepositoryDto> dedupeSources(List<SourceRepositoryDto> sources) {
        if (sources == null || sources.isEmpty()) {
            throw new InternalException("At least one source repository is required for update");
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
