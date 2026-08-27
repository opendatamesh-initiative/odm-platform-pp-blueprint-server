package org.opendatamesh.platform.pp.blueprint.old.v1.client;

import org.opendatamesh.platform.pp.blueprint.old.v1.resources.policy.PolicyResource;
import org.opendatamesh.platform.pp.blueprint.old.v1.resources.policy.PolicySearchOptions;
import org.opendatamesh.platform.pp.blueprint.utils.client.RestUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

class PolicyClientImpl implements PolicyClient {

    private static final String ROUTE = "/api/v1/pp/policy/policies";

    private final RestUtils restUtils;
    private final String policyServiceBaseUrl;

    PolicyClientImpl(RestUtils restUtils, String policyServiceBaseUrl) {
        this.restUtils = restUtils;
        this.policyServiceBaseUrl = policyServiceBaseUrl;
    }

    @Override
    public Page<PolicyResource> getPolicies(Pageable pageable, PolicySearchOptions searchOptions) {
        return restUtils.getPage(
                policyServiceBaseUrl + ROUTE,
                null,
                pageable,
                searchOptions,
                PolicyResource.class
        );
    }

    @Override
    public PolicyResource createPolicy(PolicyResource policy) {
        return restUtils.create(
                policyServiceBaseUrl + ROUTE,
                null,
                policy,
                PolicyResource.class
        );
    }
}
