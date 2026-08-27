package org.opendatamesh.platform.pp.blueprint.old.v1;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendatamesh.platform.pp.blueprint.old.v1.client.PolicyClient;
import org.opendatamesh.platform.pp.blueprint.old.v1.client.PolicyEngineClient;
import org.opendatamesh.platform.pp.blueprint.old.v1.resources.policy.PolicyEngineResource;
import org.opendatamesh.platform.pp.blueprint.old.v1.resources.policy.PolicyResource;
import org.opendatamesh.platform.pp.blueprint.validator.config.BlueprintValidatorProperties;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Policy engine and policy create-if-absent on {@code DATA_PRODUCT_VERSION_CREATION}.
 * Scenarios trace to {@code spdd/prompt/BDMD-5124-202608241546-[Feat]-service-v1-protected-resources-policy-adapter.md} (Gherkin).
 */
@ExtendWith(MockitoExtension.class)
class ProtectedResourcesValidatorPolicySubscriberTest {

    @Mock
    private PolicyEngineClient policyEngineClient;
    @Mock
    private PolicyClient policyClient;

    /**
     * Feature: Policy engine registration (V1)
     *
     * Scenario: Validator inactive does not register a Policy engine
     *   Given blueprint.validator.active is false
     *   When the Policy subscriber initializes
     *   Then Policy engine and policy clients are never called
     */
    @Test
    void inactiveDoesNotCallClients() {
        BlueprintValidatorProperties properties = activeProperties(false);
        ProtectedResourcesValidatorPolicySubscriber subscriber = new ProtectedResourcesValidatorPolicySubscriber(
                properties, policyEngineClient, policyClient, "http://localhost:8080", true, "http://policy");
        subscriber.init();
        verify(policyEngineClient, never()).getPolicyEngines(any(), any());
        verify(policyClient, never()).createPolicy(any());
    }

    /**
     * Feature: Policy engine registration (V1)
     *
     * Scenario: Validator active creates engine and policy if absent
     *   Given blueprint.validator.active is true
     *   And Policy Service is configured
     *   And no Policy engine or policy named for this validator exists
     *   When the Policy subscriber initializes
     *   Then a Policy engine is created with adapterUrl equal to server.baseUrl
     *   And a policy named "Protected Resources Integrity" is created
     *   And the policy blockingFlag is taken from Blueprint configuration
     *   And the policy evaluation events contain exactly "DATA_PRODUCT_VERSION_CREATION"
     *   And the policy evaluation events do not contain "DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED"
     *   And the policy evaluation events do not contain "DATA_PRODUCT_CREATION"
     */
    @Test
    void activeCreatesEngineAndPolicyIfAbsent() {
        BlueprintValidatorProperties properties = activeProperties(true);
        when(policyEngineClient.getPolicyEngines(any(), any())).thenReturn(Page.empty());
        PolicyEngineResource createdEngine = new PolicyEngineResource();
        createdEngine.setName("blueprint-service-validator");
        createdEngine.setAdapterUrl("http://localhost:8080");
        when(policyEngineClient.createPolicyEngine(any())).thenReturn(createdEngine);
        when(policyClient.getPolicies(any(), any())).thenReturn(Page.empty());
        when(policyClient.createPolicy(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProtectedResourcesValidatorPolicySubscriber subscriber = new ProtectedResourcesValidatorPolicySubscriber(
                properties, policyEngineClient, policyClient, "http://localhost:8080", true, "http://policy");
        subscriber.init();

        ArgumentCaptor<PolicyEngineResource> engineCaptor = ArgumentCaptor.forClass(PolicyEngineResource.class);
        verify(policyEngineClient).createPolicyEngine(engineCaptor.capture());
        assertThat(engineCaptor.getValue().getName()).isEqualTo("blueprint-service-validator");
        assertThat(engineCaptor.getValue().getDisplayName()).isEqualTo("Blueprint Service Validator");
        assertThat(engineCaptor.getValue().getAdapterUrl()).isEqualTo("http://localhost:8080");

        ArgumentCaptor<PolicyResource> policyCaptor = ArgumentCaptor.forClass(PolicyResource.class);
        verify(policyClient).createPolicy(policyCaptor.capture());
        PolicyResource created = policyCaptor.getValue();
        assertThat(created.getName()).isEqualTo("Protected Resources Integrity");
        assertThat(created.getDisplayName()).isEqualTo("Protected Resources Integrity");
        assertThat(created.getBlockingFlag()).isTrue();
        assertThat(created.getEvaluationEvents()).hasSize(1);
        assertThat(created.getEvaluationEvents().getFirst().getEvent())
                .isEqualTo(ProtectedResourcesValidatorPolicySubscriber.EVALUATION_EVENT);
        assertThat(created.getEvaluationEvents().getFirst().getEvent())
                .isEqualTo("DATA_PRODUCT_VERSION_CREATION");
        assertThat(created.getEvaluationEvents())
                .extracting(event -> event.getEvent())
                .containsExactly("DATA_PRODUCT_VERSION_CREATION")
                .doesNotContain("DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED", "DATA_PRODUCT_CREATION");
    }

    /**
     * Feature: Policy engine registration (V1)
     *
     * Scenario: Validator active does not recreate an existing policy
     *   Given blueprint.validator.active is true
     *   And Policy Service is configured
     *   And the Policy engine and policy already exist
     *   When the Policy subscriber initializes
     *   Then createPolicyEngine is never called
     *   And createPolicy is never called
     */
    @Test
    void activeDoesNotRecreateExistingPolicy() {
        BlueprintValidatorProperties properties = activeProperties(true);
        PolicyEngineResource existing = new PolicyEngineResource();
        existing.setName("blueprint-service-validator");
        when(policyEngineClient.getPolicyEngines(any(), any())).thenReturn(new PageImpl<>(List.of(existing)));
        PolicyResource existingPolicy = new PolicyResource();
        existingPolicy.setName("Protected Resources Integrity");
        when(policyClient.getPolicies(any(), any())).thenReturn(new PageImpl<>(List.of(existingPolicy)));

        ProtectedResourcesValidatorPolicySubscriber subscriber = new ProtectedResourcesValidatorPolicySubscriber(
                properties, policyEngineClient, policyClient, "http://localhost:8080", true, "http://policy");
        subscriber.init();

        verify(policyEngineClient, never()).createPolicyEngine(any());
        verify(policyClient, never()).createPolicy(any());
    }

    /**
     * Feature: Policy engine registration (V1)
     *
     * Scenario: Validator active but Policy Service is not configured skips registration
     *   Given blueprint.validator.active is true
     *   And Policy Service is inactive or has a blank address
     *   When the Policy subscriber initializes
     *   Then Policy engine and policy clients are never called
     */
    @Test
    void activeWithoutPolicyServiceDoesNotCallClients() {
        BlueprintValidatorProperties properties = activeProperties(true);
        ProtectedResourcesValidatorPolicySubscriber subscriber = new ProtectedResourcesValidatorPolicySubscriber(
                properties, policyEngineClient, policyClient, "http://localhost:8080", false, "");
        subscriber.init();
        verify(policyEngineClient, never()).getPolicyEngines(any(), any());
        verify(policyClient, never()).createPolicy(any());
    }

    private static BlueprintValidatorProperties activeProperties(boolean active) {
        BlueprintValidatorProperties properties = new BlueprintValidatorProperties();
        properties.setActive(active);
        return properties;
    }
}
