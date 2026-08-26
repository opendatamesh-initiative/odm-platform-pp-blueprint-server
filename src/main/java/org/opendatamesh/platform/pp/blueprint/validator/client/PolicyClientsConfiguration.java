package org.opendatamesh.platform.pp.blueprint.validator.client;

import org.opendatamesh.platform.pp.blueprint.utils.client.RestUtilsFactory;
import org.opendatamesh.platform.pp.blueprint.validator.resources.policy.PolicyEngineResource;
import org.opendatamesh.platform.pp.blueprint.validator.resources.policy.PolicyEngineSearchOptions;
import org.opendatamesh.platform.pp.blueprint.validator.resources.policy.PolicyResource;
import org.opendatamesh.platform.pp.blueprint.validator.resources.policy.PolicySearchOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.util.StringUtils;

@Configuration
public class PolicyClientsConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PolicyClientsConfiguration.class);

    private final RestTemplateBuilder restTemplateBuilder;
    private final boolean policyServiceActive;
    private final String policyServiceAddress;

    public PolicyClientsConfiguration(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${odm.product-plane.policy-service.active:false}") boolean policyServiceActive,
            @Value("${odm.product-plane.policy-service.address:}") String policyServiceAddress
    ) {
        this.restTemplateBuilder = restTemplateBuilder;
        this.policyServiceActive = policyServiceActive;
        this.policyServiceAddress = policyServiceAddress;
    }

    @Bean
    public PolicyEngineClient policyEngineClient() {
        if (useRealClients()) {
            return new PolicyEngineClientImpl(
                    RestUtilsFactory.getRestUtils(restTemplateBuilder.build()),
                    policyServiceAddress
            );
        }
        log.warn("ODM Policy Engine Client is not enabled (policy-service inactive or address blank).");
        return new PolicyEngineClient() {
            @Override
            public Page<PolicyEngineResource> getPolicyEngines(Pageable pageable, PolicyEngineSearchOptions searchOptions) {
                log.warn("getPolicyEngines called but policy engine client is disabled. Returning empty page.");
                return Page.empty();
            }

            @Override
            public PolicyEngineResource createPolicyEngine(PolicyEngineResource policyEngine) {
                log.warn("createPolicyEngine called but policy engine client is disabled. Policy engine not created.");
                return null;
            }
        };
    }

    @Bean
    public PolicyClient policyClient() {
        if (useRealClients()) {
            return new PolicyClientImpl(
                    RestUtilsFactory.getRestUtils(restTemplateBuilder.build()),
                    policyServiceAddress
            );
        }
        log.warn("ODM Policy Client is not enabled (policy-service inactive or address blank).");
        return new PolicyClient() {
            @Override
            public Page<PolicyResource> getPolicies(Pageable pageable, PolicySearchOptions searchOptions) {
                log.warn("getPolicies called but policy client is disabled. Returning empty page.");
                return Page.empty();
            }

            @Override
            public PolicyResource createPolicy(PolicyResource policy) {
                log.warn("createPolicy called but policy client is disabled. Policy not created.");
                return null;
            }
        };
    }

    private boolean useRealClients() {
        return policyServiceActive && StringUtils.hasText(policyServiceAddress);
    }
}
