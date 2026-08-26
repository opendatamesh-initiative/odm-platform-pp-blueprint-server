package org.opendatamesh.platform.pp.blueprint.validator.client;

import org.opendatamesh.platform.pp.blueprint.validator.resources.policy.PolicyEngineResource;
import org.opendatamesh.platform.pp.blueprint.validator.resources.policy.PolicyEngineSearchOptions;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PolicyEngineClient {

    Page<PolicyEngineResource> getPolicyEngines(Pageable pageable, PolicyEngineSearchOptions searchOptions);

    PolicyEngineResource createPolicyEngine(PolicyEngineResource policyEngine);
}
