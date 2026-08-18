package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate;

import org.opendatamesh.platform.git.model.Commit;
import org.opendatamesh.platform.git.model.RepositoryPointerBranch;
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
import java.nio.file.Path;
import java.util.function.BiConsumer;

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
    public void init(Blueprint blueprint) {
        gitProvider = gitProviderFactory.buildGitProvider(
                new GitProviderIdentifier(blueprint.getBlueprintRepo().getProviderType().name(),
                        blueprint.getBlueprintRepo().getProviderBaseUrl()),
                gitProviderHttpHeaders);
    }

    @Override
    public void withClonedSourceAndTarget(
            SourceRepositoryDto source,
            TargetRepositoryDto target,
            String integrationBranch,
            BiConsumer<Path, Path> operation) {
        // Target repositories always point to a branch, sources are frozen with tag snapshots.
        gitProvider.gitOperation().readRepository(
                target.repository(),
                new RepositoryPointerBranch(integrationBranch),
                targetRepoDir -> gitProvider.gitOperation().readRepository(
                        source.repository(),
                        new RepositoryPointerTag(source.tag()),
                        sourceRepoDir -> operation.accept(sourceRepoDir.toPath(), targetRepoDir.toPath())));
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
