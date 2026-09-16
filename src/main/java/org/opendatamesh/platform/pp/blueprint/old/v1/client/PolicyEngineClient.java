package org.opendatamesh.platform.pp.blueprint.old.v1.client;

import org.opendatamesh.platform.pp.blueprint.old.v1.resources.policy.PolicyEngineResource;
import org.opendatamesh.platform.pp.blueprint.old.v1.resources.policy.PolicyEngineSearchOptions;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PolicyEngineClient {

    Page<PolicyEngineResource> getPolicyEngines(Pageable pageable, PolicyEngineSearchOptions searchOptions);

    PolicyEngineResource createPolicyEngine(PolicyEngineResource policyEngine);
}
