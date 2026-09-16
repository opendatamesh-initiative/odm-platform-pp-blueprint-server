package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.BlueprintRepo;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.BlueprintRepoProviderType;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate.InstantiateBlueprintVersionCommand;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate.InstantiateBlueprintVersionFactory;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate.RenderedTreeSnapshot;
import org.opendatamesh.platform.pp.blueprint.utils.usecases.UseCase;
import org.opendatamesh.platform.pp.blueprint.validator.config.BlueprintValidatorProperties;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Local expected-tree adapter for target-keyed re-instantiation.
 * Scenarios trace to {@code spdd/prompt/BDMD-5124-202609031327-[Feat]-service-protected-resources-n1-integrity.md} (Gherkin).
 */
class EvaluateProtectedResourcesIntegrityInstantiateOutboundPortImplTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Feature: Local snapshots
     *
     * Scenario: Every target retained and closed
     *   Given a parent manifest declares multiple targetRepositories
     *   When local re-instantiation runs
     *   Then every declared key has an expected tree
     *   And closing the result deletes those trees
     */
    @Test
    void whenManifestHasMultipleTargetsThenReturnAllExpectedTreesAndCleanup() throws Exception {
        InstantiateBlueprintVersionFactory factory = mock(InstantiateBlueprintVersionFactory.class);
        when(factory.buildInstantiateBlueprintVersionForLocalValidation(any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    InstantiateBlueprintVersionCommand command = invocation.getArgument(0);
                    RenderedTreeSnapshot snapshot = invocation.getArgument(3);
                    return (UseCase) () -> {
                        try {
                            for (var target : command.targetRepositories()) {
                                Path tree = Files.createTempDirectory("integrity-expected-" + target.targetId() + "-");
                                snapshot.putExpectedTree(target.targetId(), tree);
                            }
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    };
                });

        EvaluateProtectedResourcesIntegrityInstantiateOutboundPortImpl port =
                new EvaluateProtectedResourcesIntegrityInstantiateOutboundPortImpl(factory, validatorProperties());
        BlueprintVersion version = blueprintVersionWithPolyrepoManifest();
        EvaluateProtectedResourcesIntegrityCommand command = new EvaluateProtectedResourcesIntegrityCommand(
                "root-tag",
                new ProductRepoLocator(
                        "https://github.com/org/root.git",
                        "GITHUB",
                        "https://github.com",
                        "root",
                        "main",
                        "org",
                        "id"),
                List.of(),
                List.of(),
                "parent",
                "1.0.0",
                null);

        Path infraPath;
        Path appPath;
        try (TargetWorkingTrees trees = port.reinstantiateBlueprintLocally(version, command)) {
            assertThat(trees.keys()).containsExactly("infra-repo", "app-repo");
            assertThat(trees.get("infra-repo")).isNotNull();
            assertThat(trees.get("app-repo")).isNotNull();
            infraPath = trees.get("infra-repo").path();
            appPath = trees.get("app-repo").path();
            assertThat(infraPath).exists();
            assertThat(appPath).exists();
        }
        assertThat(infraPath).doesNotExist();
        assertThat(appPath).doesNotExist();
    }

    private static BlueprintValidatorProperties validatorProperties() {
        BlueprintValidatorProperties properties = new BlueprintValidatorProperties();
        BlueprintValidatorProperties.GitCredential credential = new BlueprintValidatorProperties.GitCredential();
        credential.setProviderType("GITHUB");
        credential.setToken("test-token");
        properties.getGit().setCredentials(List.of(credential));
        return properties;
    }

    private static BlueprintVersion blueprintVersionWithPolyrepoManifest() {
        ObjectNode content = OBJECT_MAPPER.createObjectNode();
        ArrayNode targetRepositories = content.putArray("targetRepositories");
        targetRepositories.addObject().put("key", "infra-repo");
        targetRepositories.addObject().put("key", "app-repo").put("isRoot", true);
        BlueprintVersion version = new BlueprintVersion();
        version.setContent(content);
        Blueprint blueprint = new Blueprint();
        BlueprintRepo repo = new BlueprintRepo();
        repo.setProviderType(BlueprintRepoProviderType.GITHUB);
        repo.setProviderBaseUrl("https://github.com");
        repo.setRemoteUrlHttp("https://github.com/org/source.git");
        blueprint.setBlueprintRepo(repo);
        version.setBlueprint(blueprint);
        return version;
    }
}
