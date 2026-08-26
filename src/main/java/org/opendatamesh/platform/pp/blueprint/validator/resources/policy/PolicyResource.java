package org.opendatamesh.platform.pp.blueprint.validator.resources.policy;

import java.util.List;

public class PolicyResource {

    private Long id;
    private String name;
    private String displayName;
    private Boolean blockingFlag;
    private List<PolicyEvaluationEventResource> evaluationEvents;
    private PolicyEngineResource policyEngine;

    public PolicyResource() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Boolean getBlockingFlag() {
        return blockingFlag;
    }

    public void setBlockingFlag(Boolean blockingFlag) {
        this.blockingFlag = blockingFlag;
    }

    public List<PolicyEvaluationEventResource> getEvaluationEvents() {
        return evaluationEvents;
    }

    public void setEvaluationEvents(List<PolicyEvaluationEventResource> evaluationEvents) {
        this.evaluationEvents = evaluationEvents;
    }

    public PolicyEngineResource getPolicyEngine() {
        return policyEngine;
    }

    public void setPolicyEngine(PolicyEngineResource policyEngine) {
        this.policyEngine = policyEngine;
    }
}
