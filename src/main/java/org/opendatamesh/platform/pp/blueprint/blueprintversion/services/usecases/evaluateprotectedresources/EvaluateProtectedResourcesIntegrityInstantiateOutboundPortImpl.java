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
import org.opendatamesh.platform.pp.blueprint.manifest.model.Manifest;
import org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation.ManifestTargetRepository;
import org.opendatamesh.platform.pp.blueprint.manifest.parser.ManifestParserFactory;
import org.opendatamesh.platform.pp.blueprint.validator.config.BlueprintValidatorProperties;
import org.opendatamesh.platform.pp.blueprint.validator.config.ValidatorGitCredentialHeaders;
import org.springframework.http.HttpHeaders;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class EvaluateProtectedResourcesIntegrityInstantiateOutboundPortImpl
        implements EvaluateProtectedResourcesIntegrityInstantiateOutboundPort {

    private static final String FAILED_TO_REBUILD =
            "Cannot check protected resources: failed to rebuild the expected files from the blueprint";
    private static final String LOCAL_BRANCH = "main";

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
    public TargetWorkingTrees reinstantiateBlueprintLocally(
            BlueprintVersion blueprintVersion,
            EvaluateProtectedResourcesIntegrityCommand command
    ) {
        HttpHeaders credentials = resolveBlueprintCredentials(blueprintVersion);
        return snapshotExpectedTrees(blueprintVersion, command, credentials);
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

    /**
     * Re-instantiate locally and copy each destination tree out of the Git workspace.
     * The Git library deletes the clone when the operation returns; the snapshot copy is what hashing uses afterwards.
     */
    private TargetWorkingTrees snapshotExpectedTrees(
            BlueprintVersion blueprintVersion,
            EvaluateProtectedResourcesIntegrityCommand command,
            HttpHeaders credentials
    ) {
        List<String> declaredKeys = declaredTargetKeys(blueprintVersion);
        RenderedTreeSnapshot snapshot = new RenderedTreeSnapshot();
        try {
            instantiateFactory.buildInstantiateBlueprintVersionForLocalValidation(
                    buildInstantiateCommand(declaredKeys, command),
                    result -> {
                        // presenter unused; expected trees are snapshotted by the local Git port
                    },
                    credentials,
                    snapshot
            ).execute();
            return adaptSnapshot(declaredKeys, snapshot);
        } catch (RuntimeException e) {
            deleteSnapshotTrees(snapshot);
            throw e;
        }
    }

    private InstantiateBlueprintVersionCommand buildInstantiateCommand(
            List<String> declaredKeys,
            EvaluateProtectedResourcesIntegrityCommand command
    ) {
        List<TargetRepositoryDto> targets = new ArrayList<>();
        for (String key : declaredKeys) {
            targets.add(new TargetRepositoryDto(key, LOCAL_BRANCH, syntheticRepository(key)));
        }
        Map<String, JsonNode> parameters = command.lineageParameters();
        return new InstantiateBlueprintVersionCommand(
                command.blueprintName(),
                command.blueprintVersionNumber(),
                targets,
                parameters,
                BlueprintGitNamingConventions.DEFAULT_COMMIT_AUTHOR_NAME,
                BlueprintGitNamingConventions.DEFAULT_COMMIT_AUTHOR_EMAIL
        );
    }

    private List<String> declaredTargetKeys(BlueprintVersion blueprintVersion) {
        Manifest manifest = parseManifest(blueprintVersion);
        if (manifest.getTargetRepositories() == null || manifest.getTargetRepositories().isEmpty()) {
            throw new IllegalStateException(FAILED_TO_REBUILD);
        }
        String rootKey = null;
        int rootCount = 0;
        List<String> keys = new ArrayList<>();
        for (ManifestTargetRepository repository : manifest.getTargetRepositories()) {
            if (repository == null || repository.getKey() == null || repository.getKey().isBlank()) {
                continue;
            }
            String key = repository.getKey().trim();
            keys.add(key);
            if (Boolean.TRUE.equals(repository.getIsRoot())) {
                rootCount++;
                rootKey = key;
            }
        }
        if (keys.isEmpty() || rootCount != 1 || rootKey == null) {
            throw new IllegalStateException(FAILED_TO_REBUILD);
        }
        return keys;
    }

    private Manifest parseManifest(BlueprintVersion blueprintVersion) {
        try {
            return ManifestParserFactory.getParser().deserialize(blueprintVersion.getContent());
        } catch (IOException e) {
            throw new IllegalStateException(FAILED_TO_REBUILD, e);
        }
    }

    private TargetWorkingTrees adaptSnapshot(List<String> declaredKeys, RenderedTreeSnapshot snapshot) {
        Map<String, CloseableWorkingTree> trees = new LinkedHashMap<>();
        for (String key : declaredKeys) {
            Path expectedTree = snapshot.getExpectedTree(key);
            if (expectedTree == null || !Files.isDirectory(expectedTree)) {
                deleteSnapshotTrees(snapshot);
                throw new IllegalStateException(
                        "Cannot check protected resources: expected tree for repository key '%s' was not produced"
                                .formatted(key));
            }
            trees.put(key, new CloseableWorkingTree(expectedTree));
        }
        return TargetWorkingTrees.of(trees);
    }

    private Repository syntheticRepository(String key) {
        Repository repository = new Repository();
        repository.setId("integrity-expected-" + key);
        repository.setName("integrity-expected-" + key);
        repository.setDefaultBranch(LOCAL_BRANCH);
        repository.setCloneUrlHttp("https://integrity.local/expected/" + key);
        return repository;
    }

    private static void deleteSnapshotTrees(RenderedTreeSnapshot snapshot) {
        for (Path path : snapshot.values()) {
            CloseableWorkingTree.deleteRecursively(path);
        }
    }
}
