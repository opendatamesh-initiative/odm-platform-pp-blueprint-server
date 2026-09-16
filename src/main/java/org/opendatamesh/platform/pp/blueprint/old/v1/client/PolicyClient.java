package org.opendatamesh.platform.pp.blueprint.old.v1.client;

import org.opendatamesh.platform.pp.blueprint.old.v1.resources.policy.PolicyResource;
import org.opendatamesh.platform.pp.blueprint.old.v1.resources.policy.PolicySearchOptions;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PolicyClient {

    Page<PolicyResource> getPolicies(Pageable pageable, PolicySearchOptions searchOptions);

    PolicyResource createPolicy(PolicyResource policy);
}
