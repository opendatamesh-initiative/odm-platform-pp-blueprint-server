package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources;

import com.fasterxml.jackson.databind.JsonNode;
import org.opendatamesh.platform.git.model.Repository;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.BlueprintRepo;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.BlueprintGitNamingConventions;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate.InstantiateBlueprintVersionCommand;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate.InstantiateBlueprintVersionFactory;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate.RenderedTreeSnapshot;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate.TargetRepositoryDto;
import org.opendatamesh.platform.pp.blueprint.validator.config.BlueprintValidatorProperties;
import org.opendatamesh.platform.pp.blueprint.validator.config.ValidatorGitCredentialHeaders;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

class EvaluateProtectedResourcesIntegrityInstantiateOutboundPortImpl
        implements EvaluateProtectedResourcesIntegrityInstantiateOutboundPort {

    private static final String FAILED_TO_REBUILD =
            "Cannot check protected resources: failed to rebuild the expected files from the blueprint";

    private final InstantiateBlueprintVersionFactory instantiateFactory;
    private final BlueprintValidatorProperties validatorProperties;

    EvaluateProtectedResourcesIntegrityInstantiateOutboundPortImpl(
            InstantiateBlueprintVersionFactory instantiateFactory,
            BlueprintValidatorProperties validatorProperties
    ) {
        this.instantiateFactory = instantiateFactory;
        this.validatorProperties = validatorProperties;
    }

    @Override
    public WorkingTree reinstantiateBlueprintLocally(
            BlueprintVersion blueprintVersion,
            EvaluateProtectedResourcesIntegrityCommand command
    ) {
        HttpHeaders credentials = resolveBlueprintCredentials(blueprintVersion);
        Path expectedTree = snapshotExpectedTree(blueprintVersion, command, credentials);
        return new CloseableWorkingTree(expectedTree);
    }

    private HttpHeaders resolveBlueprintCredentials(BlueprintVersion blueprintVersion) {
        BlueprintRepo blueprintRepo = blueprintVersion.getBlueprint().getBlueprintRepo();
        String providerType = blueprintRepo.getProviderType().name();
        return ValidatorGitCredentialHeaders.resolve(
                        validatorProperties,
                        providerType,
                        blueprintRepo.getProviderBaseUrl())
                .orElseThrow(() -> new IllegalStateException(
                        "Cannot check protected resources: Git access is not configured for provider "
                                + providerType));
    }

    private Path snapshotExpectedTree(
            BlueprintVersion blueprintVersion,
            EvaluateProtectedResourcesIntegrityCommand command,
            HttpHeaders credentials
    ) {
        String rootKey = retrieveRootTargetRepositoryKey(blueprintVersion);
        RenderedTreeSnapshot snapshot = new RenderedTreeSnapshot();
        try {
            instantiateFactory.buildInstantiateBlueprintVersionForLocalValidation(
                    buildInstantiateCommand(rootKey, command),
                    result -> {
                        // expected tree is captured by the local Git port into the snapshot
                    },
                    credentials,
                    snapshot
            ).execute();
            Path expectedTree = expectedTreeForRoot(snapshot, rootKey);
            if (expectedTree == null || !Files.isDirectory(expectedTree)) {
                deleteSnapshotTrees(snapshot);
                throw new IllegalStateException(FAILED_TO_REBUILD);
            }
            deleteLeftoverSnapshotTrees(snapshot, expectedTree);
            return expectedTree;
        } catch (RuntimeException e) {
            deleteSnapshotTrees(snapshot);
            throw e;
        }
    }

    private InstantiateBlueprintVersionCommand buildInstantiateCommand(
            String rootKey,
            EvaluateProtectedResourcesIntegrityCommand command
    ) {
        ProductRepoLocator productRepo = command.productRepo();
        TargetRepositoryDto target = new TargetRepositoryDto(
                rootKey,
                productRepo.defaultBranch(),
                toGitRepository(productRepo)
        );
        Map<String, JsonNode> parameters = command.lineageParameters() == null
                ? Map.of()
                : command.lineageParameters();
        return new InstantiateBlueprintVersionCommand(
                command.blueprintName(),
                command.blueprintVersionNumber(),
                List.of(target),
                parameters,
                BlueprintGitNamingConventions.DEFAULT_COMMIT_AUTHOR_NAME,
                BlueprintGitNamingConventions.DEFAULT_COMMIT_AUTHOR_EMAIL
        );
    }

    private String retrieveRootTargetRepositoryKey(BlueprintVersion blueprintVersion) {
        JsonNode content = blueprintVersion.getContent();
        JsonNode rootRepository = content == null
                ? null
                : content.path("instantiation").path("root").path("repository");
        if (rootRepository == null || !rootRepository.isTextual() || !StringUtils.hasText(rootRepository.asText())) {
            throw new IllegalStateException(FAILED_TO_REBUILD);
        }
        return rootRepository.asText().trim();
    }

    private Path expectedTreeForRoot(RenderedTreeSnapshot snapshot, String rootKey) {
        Path expectedTree = snapshot.getExpectedTree(rootKey);
        if (expectedTree != null) {
            return expectedTree;
        }
        if (snapshot.values().size() == 1) {
            return snapshot.values().iterator().next();
        }
        return null;
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

    private static void deleteLeftoverSnapshotTrees(RenderedTreeSnapshot snapshot, Path keep) {
        for (Path path : snapshot.values()) {
            if (path != null && !path.equals(keep)) {
                deleteRecursively(path);
            }
        }
    }

    private static void deleteSnapshotTrees(RenderedTreeSnapshot snapshot) {
        for (Path path : snapshot.values()) {
            deleteRecursively(path);
        }
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
