package org.opendatamesh.platform.pp.blueprint.old.v1;

import org.opendatamesh.platform.pp.blueprint.validator.client.PolicyClient;
import org.opendatamesh.platform.pp.blueprint.validator.client.PolicyEngineClient;
import org.opendatamesh.platform.pp.blueprint.validator.config.BlueprintValidatorProperties;
import org.opendatamesh.platform.pp.blueprint.validator.resources.policy.PolicyEngineResource;
import org.opendatamesh.platform.pp.blueprint.validator.resources.policy.PolicyEngineSearchOptions;
import org.opendatamesh.platform.pp.blueprint.validator.resources.policy.PolicyEvaluationEventResource;
import org.opendatamesh.platform.pp.blueprint.validator.resources.policy.PolicyResource;
import org.opendatamesh.platform.pp.blueprint.validator.resources.policy.PolicySearchOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Pageable;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;

import java.util.List;
import java.util.Optional;

/**
 * Registers the engine + policy on Policy V1 {@code DATA_PRODUCT_VERSION_CREATION}.
 * Replace the event name when {@code old/v1} is deleted.
 */
@Configuration
public class ProtectedResourcesValidatorPolicySubscriber {

    public static final String EVALUATION_EVENT = "DATA_PRODUCT_VERSION_CREATION";

    private static final Logger log = LoggerFactory.getLogger(ProtectedResourcesValidatorPolicySubscriber.class);

    private final boolean validatorActive;
    private final BlueprintValidatorProperties properties;
    private final PolicyEngineClient policyEngineClient;
    private final PolicyClient policyClient;
    private final String serverBaseUrl;
    private final boolean policyServiceActive;
    private final String policyServiceAddress;

    public ProtectedResourcesValidatorPolicySubscriber(
            BlueprintValidatorProperties properties,
            PolicyEngineClient policyEngineClient,
            PolicyClient policyClient,
            @Value("${server.baseUrl:}") String serverBaseUrl,
            @Value("${odm.product-plane.policy-service.active:false}") boolean policyServiceActive,
            @Value("${odm.product-plane.policy-service.address:}") String policyServiceAddress
    ) {
        this.properties = properties;
        this.validatorActive = properties.isActive();
        this.policyEngineClient = policyEngineClient;
        this.policyClient = policyClient;
        this.serverBaseUrl = serverBaseUrl;
        this.policyServiceActive = policyServiceActive;
        this.policyServiceAddress = policyServiceAddress;
    }

    @PostConstruct
    public void init() {
        if (!validatorActive) {
            return;
        }
        if (!policyServiceActive || !StringUtils.hasText(policyServiceAddress)) {
            log.error("Blueprint validator is active but Policy Service is not configured "
                    + "(odm.product-plane.policy-service.active/address). Skipping engine/policy registration.");
            return;
        }

        try {
            PolicyEngineResource policyEngine = findPolicyEngine().orElseGet(this::createPolicyEngine);
            if (policyEngine == null || !StringUtils.hasText(policyEngine.getName())) {
                log.error("Blueprint validator could not resolve a Policy engine; skipping policy registration.");
                return;
            }
            // Create-if-absent only: do not update an existing policy's blockingFlag on restart.
            // Operators must change blocking in Policy Service if they change blueprint.validator.policy.blocking
            // after the first successful create.
            if (!policyExists(policyEngine.getName(), properties.getPolicy().getName())) {
                createPolicy(policyEngine);
            }
        } catch (RuntimeException e) {
            log.error("Blueprint validator failed to register Policy engine/policy: {}", e.getMessage(), e);
        }
    }

    Optional<PolicyEngineResource> findPolicyEngine() {
        return policyEngineClient.getPolicyEngines(Pageable.ofSize(500), new PolicyEngineSearchOptions())
                .get()
                .filter(engine -> properties.getPolicyEngine().getName().equals(engine.getName()))
                .findFirst();
    }

    PolicyEngineResource createPolicyEngine() {
        PolicyEngineResource policyEngine = new PolicyEngineResource();
        policyEngine.setName(properties.getPolicyEngine().getName());
        policyEngine.setDisplayName(properties.getPolicyEngine().getDisplayName());
        policyEngine.setAdapterUrl(serverBaseUrl);
        return policyEngineClient.createPolicyEngine(policyEngine);
    }

    boolean policyExists(String policyEngineName, String policyName) {
        PolicySearchOptions filter = new PolicySearchOptions();
        filter.setPolicyEngineName(policyEngineName);
        filter.setName(policyName);
        return !policyClient.getPolicies(Pageable.ofSize(1), filter).isEmpty();
    }

    PolicyResource createPolicy(PolicyEngineResource policyEngine) {
        PolicyResource policy = new PolicyResource();
        policy.setPolicyEngine(policyEngine);
        policy.setName(properties.getPolicy().getName());
        policy.setDisplayName(properties.getPolicy().getName());
        policy.setBlockingFlag(properties.getPolicy().isBlocking());
        policy.setEvaluationEvents(List.of(new PolicyEvaluationEventResource(EVALUATION_EVENT)));
        return policyClient.createPolicy(policy);
    }
}
