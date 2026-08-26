package org.opendatamesh.platform.pp.blueprint.validator.client;

import org.opendatamesh.platform.pp.blueprint.validator.resources.policy.PolicyResource;
import org.opendatamesh.platform.pp.blueprint.validator.resources.policy.PolicySearchOptions;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PolicyClient {

    Page<PolicyResource> getPolicies(Pageable pageable, PolicySearchOptions searchOptions);

    PolicyResource createPolicy(PolicyResource policy);
}
