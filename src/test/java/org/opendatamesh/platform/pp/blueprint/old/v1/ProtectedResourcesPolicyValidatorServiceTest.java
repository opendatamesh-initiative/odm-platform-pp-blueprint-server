package org.opendatamesh.platform.pp.blueprint.old.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources.EvaluateProtectedResourcesIntegrityCommand;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources.EvaluateProtectedResourcesIntegrityFactory;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources.EvaluateProtectedResourcesIntegrityPresenter;
import org.opendatamesh.platform.pp.blueprint.old.v1.resources.PolicyEvaluationRequestRes;
import org.opendatamesh.platform.pp.blueprint.old.v1.resources.PolicyEvaluationResultRes;
import org.opendatamesh.platform.pp.blueprint.utils.usecases.UseCase;
import org.opendatamesh.platform.pp.blueprint.validator.config.BlueprintValidatorProperties;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Policy V1 mapping from Registry-shaped JSON onto the lasting integrity command.
 * Scenarios trace to {@code spdd/prompt/BDMD-5124-202608241546-[Feat]-service-v1-protected-resources-policy-adapter.md} (Gherkin).
 */
@ExtendWith(MockitoExtension.class)
class ProtectedResourcesPolicyValidatorServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private EvaluateProtectedResourcesIntegrityFactory integrityFactory;
    @Mock
    private ProtectedResourcesPolicyValidatorService self;

    /**
     * Feature: Policy V1 Registry reconstruction
     *
     * Scenario: V1 payload reconstructs full root and additional publication context
     *   Given a Policy V1 afterState identifies one data product version
     *   And Registry returns a root repository, keyed additional repositories, a root tag, and keyed additional tags
     *   When reconstruction evaluates the request
     *   Then the Policy validator receives all locator and ref arrays
     *   And integrity is invoked with root and keyed additional domain entries
     */
    @Test
    void mapsRootAndAdditionalLocatorRefLists() {
        AtomicReference<EvaluateProtectedResourcesIntegrityCommand> captured = new AtomicReference<>();
        ProtectedResourcesPolicyValidatorService service = serviceThatCapturesCommand(captured);

        service.evaluate(request(versionWithRootAndAdditional(false)));

        EvaluateProtectedResourcesIntegrityCommand command = captured.get();
        assertThat(command).isNotNull();
        assertThat(command.rootPublicationRef()).isEqualTo("v1.2.0");
        assertThat(command.rootProductRepo().remoteUrlHttp()).isEqualTo("https://github.com/org/customer360.git");
        assertThat(command.additionalProductRepos()).hasSize(1);
        assertThat(command.additionalProductRepos().getFirst().repositoryKey()).isEqualTo("infra-repo");
        assertThat(command.additionalProductRepos().getFirst().locator().remoteUrlHttp())
                .isEqualTo("https://github.com/org/infra.git");
        assertThat(command.additionalProductRepos().getFirst().locator().providerType()).isEqualTo("GITHUB");
        assertThat(command.additionalRefs()).hasSize(1);
        assertThat(command.additionalRefs().getFirst().repositoryKey()).isEqualTo("infra-repo");
        assertThat(command.additionalRefs().getFirst().ref()).isEqualTo("infra-v9");
        assertThat(command.blueprintName()).isEqualTo("example-blueprint");
        assertThat(command.blueprintVersionNumber()).isEqualTo("1.0.0");
    }

    /**
     * Feature: Policy V1 Registry reconstruction
     *
     * Scenario: Duplicate keyed entries are preserved
     *   Given Registry returns duplicate additional locator or ref entries for one repositoryKey
     *   When the Policy validator maps the integrity command
     *   Then every duplicate entry remains in the domain lists
     *   And the adapter does not silently choose one
     */
    @Test
    void duplicateAdditionalEntriesRemainVisibleToIntegrity() {
        AtomicReference<EvaluateProtectedResourcesIntegrityCommand> captured = new AtomicReference<>();
        ProtectedResourcesPolicyValidatorService service = serviceThatCapturesCommand(captured);

        service.evaluate(request(versionWithRootAndAdditional(true)));

        EvaluateProtectedResourcesIntegrityCommand command = captured.get();
        assertThat(command.additionalProductRepos()).hasSize(2);
        assertThat(command.additionalProductRepos())
                .extracting(entry -> entry.repositoryKey())
                .containsExactly("infra-repo", "infra-repo");
        assertThat(command.additionalProductRepos())
                .extracting(entry -> entry.locator().remoteUrlHttp())
                .containsExactly("https://github.com/org/infra.git", "https://github.com/org/infra-dup.git");
        assertThat(command.additionalRefs()).hasSize(2);
        assertThat(command.additionalRefs())
                .extracting(entry -> entry.repositoryKey())
                .containsExactly("infra-repo", "infra-repo");
        assertThat(command.additionalRefs())
                .extracting(entry -> entry.ref())
                .containsExactly("infra-v9", "infra-v10");
    }

    /**
     * Feature: Policy V1 Registry reconstruction
     *
     * Scenario: Integrity timeout rejects the Policy evaluation
     *   Given a valid reconstructed command
     *   And integrity exceeds blueprint.validator.evaluation-timeout-seconds
     *   When the Policy adapter waits for completion
     *   Then evaluationResult is false
     *   And a late outcome cannot overwrite the timeout
     */
    @Test
    void timeoutSealsOutcomeAndReturnsFalse() {
        BlueprintValidatorProperties properties = new BlueprintValidatorProperties();
        properties.setEvaluationTimeoutSeconds(1);
        AtomicReference<ProtectedResourcesPolicyValidatorService.OutcomeHolder> holderRef = new AtomicReference<>();
        when(self.executeIntegrity(any(), any())).thenAnswer(invocation -> {
            holderRef.set(invocation.getArgument(1));
            return new CompletableFuture<Void>();
        });
        ProtectedResourcesPolicyValidatorService service = new ProtectedResourcesPolicyValidatorService(
                integrityFactory, properties, OBJECT_MAPPER, self);

        PolicyEvaluationResultRes result = service.evaluate(request(versionWithRootAndAdditional(false)));

        assertThat(result.getEvaluationResult()).isFalse();
        assertThat(result.getOutputObject().getMessage()).contains("timed out");
        holderRef.get().presentPassed("late success must not overwrite timeout");
        assertThat(result.getEvaluationResult()).isFalse();
        assertThat(result.getOutputObject().getMessage()).contains("timed out");
    }

    private ProtectedResourcesPolicyValidatorService serviceThatCapturesCommand(
            AtomicReference<EvaluateProtectedResourcesIntegrityCommand> captured
    ) {
        when(integrityFactory.buildEvaluateProtectedResourcesIntegrity(any(), any())).thenAnswer(invocation -> {
            captured.set(invocation.getArgument(0));
            EvaluateProtectedResourcesIntegrityPresenter presenter = invocation.getArgument(1);
            return (UseCase) () -> presenter.presentPassed("ok");
        });
        when(self.executeIntegrity(any(), any())).thenAnswer(invocation -> {
            integrityFactory.buildEvaluateProtectedResourcesIntegrity(
                    invocation.getArgument(0), invocation.getArgument(1)).execute();
            return CompletableFuture.completedFuture(null);
        });
        return new ProtectedResourcesPolicyValidatorService(
                integrityFactory, new BlueprintValidatorProperties(), OBJECT_MAPPER, self);
    }

    private static PolicyEvaluationRequestRes request(JsonNode objectToEvaluate) {
        PolicyEvaluationRequestRes request = new PolicyEvaluationRequestRes();
        request.setPolicyEvaluationId(11L);
        request.setObjectToEvaluate(objectToEvaluate);
        return request;
    }

    private static ObjectNode versionWithRootAndAdditional(boolean duplicate) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        ObjectNode eventContent = root.putObject("eventContent");
        ObjectNode version = eventContent.putObject("dataProductVersion");
        version.put("tag", "v1.2.0");
        ObjectNode content = version.putObject("content");
        ObjectNode blueprint = content.putObject("blueprint");
        blueprint.put("blueprintName", "example-blueprint");
        blueprint.put("blueprintVersionNumber", "1.0.0");
        ObjectNode dataProduct = version.putObject("dataProduct");
        ObjectNode rootRepo = dataProduct.putObject("dataProductRepo");
        rootRepo.put("remoteUrlHttp", "https://github.com/org/customer360.git");
        rootRepo.put("providerType", "GITHUB");
        ObjectNode first = OBJECT_MAPPER.createObjectNode();
        first.put("repositoryKey", "infra-repo");
        first.put("remoteUrlHttp", "https://github.com/org/infra.git");
        first.put("providerType", "GITHUB");
        var additionalRepos = OBJECT_MAPPER.createArrayNode().add(first);
        var additionalTags = OBJECT_MAPPER.createArrayNode()
                .add(OBJECT_MAPPER.createObjectNode().put("repositoryKey", "infra-repo").put("tag", "infra-v9"));
        if (duplicate) {
            ObjectNode second = OBJECT_MAPPER.createObjectNode();
            second.put("repositoryKey", "infra-repo");
            second.put("remoteUrlHttp", "https://github.com/org/infra-dup.git");
            second.put("providerType", "GITHUB");
            additionalRepos.add(second);
            additionalTags.add(OBJECT_MAPPER.createObjectNode().put("repositoryKey", "infra-repo").put("tag", "infra-v10"));
        }
        dataProduct.set("additionalDataProductRepos", additionalRepos);
        version.set("additionalTags", additionalTags);
        return root;
    }
}
