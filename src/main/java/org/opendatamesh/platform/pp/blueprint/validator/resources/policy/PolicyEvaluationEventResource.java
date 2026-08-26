package org.opendatamesh.platform.pp.blueprint.validator.resources.policy;

public class PolicyEvaluationEventResource {

    private String event;

    public PolicyEvaluationEventResource() {
    }

    public PolicyEvaluationEventResource(String event) {
        this.event = event;
    }

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }
}
