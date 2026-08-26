package org.opendatamesh.platform.pp.blueprint.old.v1;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.opendatamesh.platform.pp.blueprint.validator.resources.PolicyEvaluationRequestRes;
import org.opendatamesh.platform.pp.blueprint.validator.resources.PolicyEvaluationResultRes;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Temporary Policy V1 evaluate endpoint. Same path as the lasting validator contract.
 * Delete with {@code old/v1}; replace with a thin controller that calls
 * {@code ProtectedResourcesValidatorService} directly.
 */
@Hidden
@RestController
@RequestMapping(value = "/api/v1/up/validator/evaluate-policy")
public class ProtectedResourcesValidatorController {

    private final ReconstructPublicationRequestedService service;

    public ProtectedResourcesValidatorController(ReconstructPublicationRequestedService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Evaluate protected-resources integrity", hidden = true)
    public PolicyEvaluationResultRes evaluate(
            @Parameter(description = "JSON object containing the object to be evaluated and the policy to validate against")
            @RequestBody PolicyEvaluationRequestRes document
    ) {
        return service.evaluate(document);
    }
}
