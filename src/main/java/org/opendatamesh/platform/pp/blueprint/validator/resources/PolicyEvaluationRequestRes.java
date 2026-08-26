package org.opendatamesh.platform.pp.blueprint.validator.resources;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

public class PolicyEvaluationRequestRes {

    @Schema(description = "Policy Evaluation ID to reconcile the evaluation result with the triggering request")
    private Long policyEvaluationId;

    @Schema(description = "JSON representation of the policy to evaluate against")
    private JsonNode policy;

    @Schema(description = "JSON representation of the object to be evaluated")
    private JsonNode objectToEvaluate;

    public PolicyEvaluationRequestRes() {
    }

    public Long getPolicyEvaluationId() {
        return policyEvaluationId;
    }

    public void setPolicyEvaluationId(Long policyEvaluationId) {
        this.policyEvaluationId = policyEvaluationId;
    }

    public JsonNode getPolicy() {
        return policy;
    }

    public void setPolicy(JsonNode policy) {
        this.policy = policy;
    }

    public JsonNode getObjectToEvaluate() {
        return objectToEvaluate;
    }

    public void setObjectToEvaluate(JsonNode objectToEvaluate) {
        this.objectToEvaluate = objectToEvaluate;
    }
}
