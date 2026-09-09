package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate;

import org.opendatamesh.platform.git.model.Commit;
import org.opendatamesh.platform.git.model.RepositoryPointerBranch;
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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

class InstantiateBlueprintVersionGitOutboundPortImpl implements InstantiateBlueprintVersionGitOutboundPort {

    private final HttpHeaders gitProviderHttpHeaders;
    private final GitProviderFactory gitProviderFactory;
    private GitProvider gitProvider;

    public InstantiateBlueprintVersionGitOutboundPortImpl(HttpHeaders gitProviderHttpHeaders,
            GitProviderFactory gitProviderFactory) {
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
    public void openTarget(
            TargetRepositoryDto target,
            String integrationBranch,
            Consumer<Path> operation) {
        gitProvider.gitOperation().readRepository(
                target.repository(),
                new RepositoryPointerBranch(integrationBranch),
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
        gitProvider.gitOperation().createAndCheckoutOrphanBranch(targetRepository.toFile(), branchName);
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
    public void mergeBranch(Path targetRepository, String sourceBranch, String targetBranch) {
        gitProvider.gitOperation().mergeBranch(targetRepository.toFile(), sourceBranch, targetBranch);
    }

    @Override
    public void pushBranch(Path targetRepository, String branchName) {
        gitProvider.gitOperation().pushBranch(targetRepository.toFile(), branchName);
    }

    @Override
    public void pushTag(Path targetRepository, String tagName) {
        gitProvider.gitOperation().pushTag(targetRepository.toFile(), tagName);
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
