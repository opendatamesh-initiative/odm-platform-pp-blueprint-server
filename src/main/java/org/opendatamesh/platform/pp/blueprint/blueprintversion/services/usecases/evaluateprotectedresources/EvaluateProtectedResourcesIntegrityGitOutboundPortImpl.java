package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources;

import org.opendatamesh.platform.git.model.Repository;
import org.opendatamesh.platform.git.model.RepositoryPointerTag;
import org.opendatamesh.platform.git.provider.GitProvider;
import org.opendatamesh.platform.git.provider.GitProviderIdentifier;
import org.opendatamesh.platform.pp.blueprint.git.provider.GitProviderFactory;
import org.springframework.http.HttpHeaders;

import java.nio.file.Path;
import java.util.function.Consumer;

class EvaluateProtectedResourcesIntegrityGitOutboundPortImpl
        implements EvaluateProtectedResourcesIntegrityGitOutboundPort {

    private final GitProviderFactory gitProviderFactory;

    EvaluateProtectedResourcesIntegrityGitOutboundPortImpl(GitProviderFactory gitProviderFactory) {
        this.gitProviderFactory = gitProviderFactory;
    }

    @Override
    public void withClonedProductAtTag(
            ProductRepoLocator repo,
            String tag,
            HttpHeaders productGitHeaders,
            Consumer<Path> operation
    ) {
        GitProvider gitProvider = gitProviderFactory.buildGitProvider(
                new GitProviderIdentifier(repo.providerType(), repo.providerBaseUrl()),
                productGitHeaders
        );
        Repository repository = toGitRepository(repo);
        gitProvider.gitOperation().readRepository(
                repository,
                new RepositoryPointerTag(tag),
                dir -> operation.accept(dir.toPath())
        );
    }

    static Repository toGitRepository(ProductRepoLocator repo) {
        Repository repository = new Repository();
        repository.setId(repo.externalIdentifier());
        repository.setName(repo.name());
        repository.setDefaultBranch(repo.defaultBranch());
        repository.setOwnerId(repo.ownerId());
        repository.setCloneUrlHttp(repo.remoteUrlHttp());
        return repository;
    }
}
