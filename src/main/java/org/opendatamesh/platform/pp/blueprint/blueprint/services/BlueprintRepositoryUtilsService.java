package org.opendatamesh.platform.pp.blueprint.blueprint.services;

import org.opendatamesh.platform.git.exceptions.GitOperationException;
import org.opendatamesh.platform.git.model.*;
import org.opendatamesh.platform.git.provider.GitProvider;
import org.opendatamesh.platform.git.provider.GitProviderIdentifier;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.BlueprintRepo;
import org.opendatamesh.platform.pp.blueprint.blueprint.services.core.BlueprintService;
import org.opendatamesh.platform.pp.blueprint.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.blueprint.exceptions.InternalException;
import org.opendatamesh.platform.pp.blueprint.git.provider.GitProviderFactory;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.repository.InitRepositoryCommandRes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Service
public class BlueprintRepositoryUtilsService {

    private static final Logger log = LoggerFactory.getLogger(BlueprintRepositoryUtilsService.class);

    private final BlueprintService blueprintService;
    private final GitProviderFactory gitProviderFactory;

    public BlueprintRepositoryUtilsService(BlueprintService blueprintService, GitProviderFactory gitProviderFactory) {
        this.blueprintService = blueprintService;
        this.gitProviderFactory = gitProviderFactory;
    }

    public void initBlueprintRepository(String blueprintUuid, InitRepositoryCommandRes initRepositoryCommand, HttpHeaders headers) {
        validateInitRepositoryCommand(initRepositoryCommand);
        BlueprintRepo blueprintRepo = blueprintService.findOne(blueprintUuid).getBlueprintRepo();

        String branch = StringUtils.hasText(initRepositoryCommand.getBranch())
                ? initRepositoryCommand.getBranch()
                : blueprintRepo.getDefaultBranch();

        log.info("Initializing blueprint repository content: blueprintUuid={}, branch={}, resourceCount={}",
                blueprintUuid, branch, initRepositoryCommand.getResources().size());

        GitProvider provider = gitProviderFactory.buildGitProvider(
                new GitProviderIdentifier(blueprintRepo.getProviderType().name(), blueprintRepo.getProviderBaseUrl()),
                headers
        );

        Repository gitRepo = provider
                .getRepository(blueprintRepo.getExternalIdentifier(), blueprintRepo.getOwnerId())
                .orElseThrow(() -> new BadRequestException(
                        "Repository not found in Git provider for externalId=%s and ownerId=%s"
                                .formatted(blueprintRepo.getExternalIdentifier(), blueprintRepo.getOwnerId())));

        RepositoryPointer repositoryPointer = buildRepositoryPointer(new GitReference(null, branch, null));

        try {
            provider.gitOperation().readRepository(gitRepo, repositoryPointer, repository -> {
                try {

                    List<File> files = new ArrayList<>();

                    Path repoBasePath = Paths.get(repository.getAbsolutePath()).normalize();
                    for (InitRepositoryCommandRes.RepositoryResource repositoryResource : initRepositoryCommand.getResources()) {
                        String relativeFilePath = repositoryResource.getFilePath();
                        Path targetPath = repoBasePath.resolve(relativeFilePath).normalize();
                        if (!targetPath.startsWith(repoBasePath)) {
                            throw new BadRequestException("Invalid file path (path traversal detected): " + relativeFilePath);
                        }
                        Path parentDir = targetPath.getParent();
                        if (parentDir != null && !Files.exists(parentDir)) {
                            Files.createDirectories(parentDir);
                        }
                        Files.writeString(targetPath, repositoryResource.getFileContent(), StandardCharsets.UTF_8);
                        files.add(targetPath.toFile());
                    }

                    provider.gitOperation().addFiles(repository, files);

                    provider.gitOperation().commit(repository,
                            new Commit("Init Commit", initRepositoryCommand.getAuthorName(), initRepositoryCommand.getAuthorEmail()));

                    provider.gitOperation().push(repository, false);
                } catch (IOException e) {
                    log.warn("Failed to write repository files for blueprint {}: {}", blueprintUuid, e.getMessage());
                    throw new InternalException("Failed to write or create descriptor file: " + e.getMessage(), e);
                }
            });
            log.info("Finished initializing blueprint repository content for blueprintUuid={}", blueprintUuid);
        } catch (GitOperationException e) {
            log.warn("Git operation failed while initializing blueprint {}: {}", blueprintUuid, e.getMessage());
            throw new BadRequestException("Failed to access repository (e.g. branch not found): " + e.getMessage(), e);
        }
    }

    private void validateInitRepositoryCommand(InitRepositoryCommandRes initRepositoryCommand) {
        if (initRepositoryCommand == null) {
            throw new BadRequestException("Init repository command is required");
        }
        if (initRepositoryCommand.getResources() == null || initRepositoryCommand.getResources().isEmpty()) {
            throw new BadRequestException("At least one repository resource is required");
        }
        if (!StringUtils.hasText(initRepositoryCommand.getAuthorName())) {
            throw new BadRequestException("Author name is required");
        }
        if (!StringUtils.hasText(initRepositoryCommand.getAuthorEmail())) {
            throw new BadRequestException("Author email is required");
        }
        int index = 0;
        for (InitRepositoryCommandRes.RepositoryResource resource : initRepositoryCommand.getResources()) {
            if (resource == null) {
                throw new BadRequestException("Repository resource at index " + index + " must not be null");
            }
            if (!StringUtils.hasText(resource.getFilePath())) {
                throw new BadRequestException("Each resource must have a non-empty file path");
            }
            if (resource.getFileContent() == null) {
                throw new BadRequestException("Each resource must have file content");
            }
            index++;
        }
    }

    private RepositoryPointer buildRepositoryPointer(GitReference pointer) {
        return switch (pointer.type()) {
            case TAG -> new RepositoryPointerTag(pointer.tag());
            case BRANCH -> new RepositoryPointerBranch(pointer.branch());
            case COMMIT -> new RepositoryPointerCommit(pointer.commit());
        };
    }

    private record GitReference(String tag, String branch, String commit) {
        enum VersionType {TAG, BRANCH, COMMIT}

        VersionType type() {
            if (tag != null) return VersionType.TAG;
            if (branch != null) return VersionType.BRANCH;
            if (commit != null) return VersionType.COMMIT;
            return VersionType.BRANCH;
        }
    }
}
