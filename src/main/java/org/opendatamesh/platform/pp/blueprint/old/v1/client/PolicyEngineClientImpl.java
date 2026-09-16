package org.opendatamesh.platform.pp.blueprint.old.v1.client;

import org.opendatamesh.platform.pp.blueprint.old.v1.resources.policy.PolicyEngineResource;
import org.opendatamesh.platform.pp.blueprint.old.v1.resources.policy.PolicyEngineSearchOptions;
import org.opendatamesh.platform.pp.blueprint.utils.client.RestUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

class PolicyEngineClientImpl implements PolicyEngineClient {

    private final RestUtils restUtils;
    private final String policyServiceBaseUrl;

    PolicyEngineClientImpl(RestUtils restUtils, String policyServiceBaseUrl) {
        this.restUtils = restUtils;
        this.policyServiceBaseUrl = policyServiceBaseUrl;
    }

    @Override
    public Page<PolicyEngineResource> getPolicyEngines(Pageable pageable, PolicyEngineSearchOptions searchOptions) {
        return restUtils.getPage(
                policyServiceBaseUrl + "/api/v1/pp/policy/policy-engines",
                null,
                pageable,
                searchOptions,
                PolicyEngineResource.class
        );
    }

    @Override
    public PolicyEngineResource createPolicyEngine(PolicyEngineResource policyEngine) {
        return restUtils.create(
                policyServiceBaseUrl + "/api/v1/pp/policy/policy-engines",
                null,
                policyEngine,
                PolicyEngineResource.class
        );
    }
}
