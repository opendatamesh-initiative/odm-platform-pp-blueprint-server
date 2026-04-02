package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate;

import org.opendatamesh.platform.git.model.Commit;
import org.opendatamesh.platform.git.model.RepositoryPointer;
import org.opendatamesh.platform.git.model.RepositoryPointerBranch;
import org.opendatamesh.platform.git.model.RepositoryPointerTag;
import org.opendatamesh.platform.git.provider.GitProvider;
import org.opendatamesh.platform.git.provider.GitProviderIdentifier;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint;
import org.opendatamesh.platform.pp.blueprint.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.blueprint.git.provider.GitProviderFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

class InstantiateBlueprintVersionGitOutboundPortImpl implements InstantiateBlueprintVersionGitOutboundPort {

    private static final String DEFAULT_COMMIT_AUTHOR_NAME = "odm-blueprint-server";
    private static final String DEFAULT_COMMIT_AUTHOR_EMAIL = "odm-blueprint-server@local";

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
    public void cloneRepositories(List<SourceRepositoryDto> sourceRepositories,
                                  List<TargetRepositoryDto> targetRepositories,
                                  BiConsumer<List<Path>, List<Path>> clonedRepositoriesConsumer) {
        List<Path> sourceRepositoryPaths = new ArrayList<>();
        List<Path> targetRepositoryPaths = new ArrayList<>();
        cloneSourceRepositoriesRecursively(sourceRepositories, sourceRepositoryPaths, () ->
                cloneTargetRepositoriesRecursively(targetRepositories, targetRepositoryPaths, () ->
                        clonedRepositoriesConsumer.accept(sourceRepositoryPaths, targetRepositoryPaths))
        );
    }

    private void cloneSourceRepositoriesRecursively(
            List<SourceRepositoryDto> sourceRepositories,
            List<Path> sourceRepositoryPaths,
            Runnable onCompleted) {
        if (sourceRepositoryPaths.size() == sourceRepositories.size()) {
            onCompleted.run();
            return;
        }
        SourceRepositoryDto sourceRepository = sourceRepositories.get(sourceRepositoryPaths.size());
        if (sourceRepository == null) {
            throw new BadRequestException("Source repository at index %s is required".formatted(sourceRepositoryPaths.size()));
        }
        //Sources are frozen with tags snapshots
        RepositoryPointer sourcePointer = new RepositoryPointerTag(sourceRepository.tag());
        gitProvider.gitOperation().readRepository(sourceRepository.repository(), sourcePointer, sourceRepositoryFile -> {
            sourceRepositoryPaths.add(sourceRepositoryFile.toPath());
            cloneSourceRepositoriesRecursively(sourceRepositories, sourceRepositoryPaths, onCompleted);
        });
    }

    private void cloneTargetRepositoriesRecursively(
            List<TargetRepositoryDto> targetRepositories,
            List<Path> targetRepositoryPaths,
            Runnable onCompleted) {
        if (targetRepositoryPaths.size() == targetRepositories.size()) {
            onCompleted.run();
            return;
        }
        TargetRepositoryDto targetRepository = targetRepositories.get(targetRepositoryPaths.size());
        if (targetRepository == null || targetRepository.repository() == null) {
            throw new BadRequestException("Target repository at index %s is required".formatted(targetRepositoryPaths.size()));
        }
        //Target repositories always point to a branch, which can be specified by the user OR the default one
        RepositoryPointer targetPointer = new RepositoryPointerBranch(StringUtils.hasText(targetRepository.branch()) ? targetRepository.branch() : targetRepository.repository().getDefaultBranch());
        gitProvider.gitOperation().readRepository(targetRepository.repository(), targetPointer, targetRepositoryFile -> {
            targetRepositoryPaths.add(targetRepositoryFile.toPath());
            cloneTargetRepositoriesRecursively(targetRepositories, targetRepositoryPaths, onCompleted);
        });
    }

    @Override
    public void commitAndPush(
            Path targetRepositoryPath,
            String commitMessage,
            String commitAuthorName,
            String commitAuthorEmail) {
        File repositoryPathFile = targetRepositoryPath.toFile();
        gitProvider.gitOperation().addFiles(repositoryPathFile, List.of(repositoryPathFile));
        gitProvider.gitOperation().commit(repositoryPathFile, new Commit(
                commitMessage,
                StringUtils.hasText(commitAuthorName) ? commitAuthorName : DEFAULT_COMMIT_AUTHOR_NAME,
                StringUtils.hasText(commitAuthorEmail) ? commitAuthorEmail : DEFAULT_COMMIT_AUTHOR_EMAIL));
        gitProvider.gitOperation().push(repositoryPathFile, false);
    }

}
