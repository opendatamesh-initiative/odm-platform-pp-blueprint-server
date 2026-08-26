package org.opendatamesh.platform.pp.blueprint.validator.resources.policy;

public class PolicySearchOptions {

    private String evaluationEvent;
    private String policyEngineName;
    private String name;
    private Boolean lastVersion = true;

    public PolicySearchOptions() {
    }

    public String getEvaluationEvent() {
        return evaluationEvent;
    }

    public void setEvaluationEvent(String evaluationEvent) {
        this.evaluationEvent = evaluationEvent;
    }

    public String getPolicyEngineName() {
        return policyEngineName;
    }

    public void setPolicyEngineName(String policyEngineName) {
        this.policyEngineName = policyEngineName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getLastVersion() {
        return lastVersion;
    }

    public void setLastVersion(Boolean lastVersion) {
        this.lastVersion = lastVersion;
    }
}
