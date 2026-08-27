package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources;

import com.fasterxml.jackson.databind.JsonNode;
import org.opendatamesh.platform.git.model.Repository;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.BlueprintRepo;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.BlueprintGitNamingConventions;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate.BlueprintRepositoryLogicalType;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate.InstantiateBlueprintVersionCommand;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate.InstantiateBlueprintVersionFactory;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate.RenderedTreeSnapshot;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate.TargetRepositoryDto;
import org.opendatamesh.platform.pp.blueprint.validator.config.BlueprintValidatorProperties;
import org.opendatamesh.platform.pp.blueprint.validator.config.ValidatorGitCredentialHeaders;
import org.springframework.http.HttpHeaders;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

class EvaluateProtectedResourcesIntegrityInstantiateOutboundPortImpl
        implements EvaluateProtectedResourcesIntegrityInstantiateOutboundPort {

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
        Path expectedTree = snapshotExpectedTree(command, credentials);
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

    private Path snapshotExpectedTree(EvaluateProtectedResourcesIntegrityCommand command, HttpHeaders credentials) {
        RenderedTreeSnapshot snapshot = new RenderedTreeSnapshot();
        try {
            instantiateFactory.buildInstantiateBlueprintVersionForLocalValidation(
                    buildInstantiateCommand(command),
                    result -> {
                        // expected tree is captured by the local Git port into the snapshot
                    },
                    credentials,
                    snapshot
            ).execute();
            Path expectedTree = snapshot.getExpectedTreeRoot();
            if (expectedTree == null || !Files.isDirectory(expectedTree)) {
                deleteRecursively(expectedTree);
                throw new IllegalStateException(
                        "Cannot check protected resources: failed to rebuild the expected files from the blueprint");
            }
            return expectedTree;
        } catch (RuntimeException e) {
            deleteRecursively(snapshot.getExpectedTreeRoot());
            throw e;
        }
    }

    private InstantiateBlueprintVersionCommand buildInstantiateCommand(
            EvaluateProtectedResourcesIntegrityCommand command
    ) {
        ProductRepoLocator productRepo = command.productRepo();
        TargetRepositoryDto target = new TargetRepositoryDto(
                productRepo.externalIdentifier(),
                BlueprintRepositoryLogicalType.ROOT,
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
