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
import java.nio.file.Files;
import java.nio.file.Path;

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
    public CloseableWorkingTree clonePublishedDataProductVersion(ProductRepoLocator repo, String tag) {
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
        boolean copied = false;
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
            copied = true;
            return publishedTree;
        } finally {
            if (!copied) {
                CloseableWorkingTree.deleteRecursively(publishedTree);
            }
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
            WorkingTreeSnapshotCopier.copySkippingGitPreservingSymlinks(source, destination);
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
}
