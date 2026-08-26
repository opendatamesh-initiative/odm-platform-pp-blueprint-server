package org.opendatamesh.platform.pp.blueprint.validator.services;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources.EvaluateProtectedResourcesIntegrityCommand;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources.EvaluateProtectedResourcesIntegrityFactory;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources.EvaluateProtectedResourcesIntegrityPresenter;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources.IntegrityOutcome;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources.ProductRepoLocator;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources.ProtectedResourceMismatch;
import org.opendatamesh.platform.pp.blueprint.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.blueprint.validator.config.BlueprintValidatorProperties;
import org.opendatamesh.platform.pp.blueprint.validator.resources.PolicyEvaluationRequestRes;
import org.opendatamesh.platform.pp.blueprint.validator.resources.PolicyEvaluationResultRes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class ProtectedResourcesValidatorService {

    private static final Logger log = LoggerFactory.getLogger(ProtectedResourcesValidatorService.class);

    private final EvaluateProtectedResourcesIntegrityFactory integrityFactory;
    private final BlueprintValidatorProperties validatorProperties;
    private final ObjectMapper objectMapper;

    public ProtectedResourcesValidatorService(
            EvaluateProtectedResourcesIntegrityFactory integrityFactory,
            BlueprintValidatorProperties validatorProperties,
            ObjectMapper objectMapper
    ) {
        this.integrityFactory = integrityFactory;
        this.validatorProperties = validatorProperties;
        this.objectMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public PolicyEvaluationResultRes evaluate(PolicyEvaluationRequestRes document) {
        if (document == null || document.getObjectToEvaluate() == null || !document.getObjectToEvaluate().isObject()) {
            throw new BadRequestException("Empty/Malformed Policy Evaluation Object");
        }

        JsonNode versionResource = extractVersionResource(document.getObjectToEvaluate());
        JsonNode content = versionResource == null ? null : versionResource.get("content");
        JsonNode blueprint = content == null ? null : content.get("blueprint");
        String blueprintName = text(blueprint, "blueprintName");
        String blueprintVersionNumber = text(blueprint, "blueprintVersionNumber");

        OutcomeHolder holder = new OutcomeHolder();
        if (!StringUtils.hasText(blueprintName) || !StringUtils.hasText(blueprintVersionNumber)) {
            holder.presentNotApplicable("This data product version was not created from a blueprint");
            return toResult(document.getPolicyEvaluationId(), holder.outcome);
        }

        EvaluateProtectedResourcesIntegrityCommand command = new EvaluateProtectedResourcesIntegrityCommand(
                versionResource == null ? null : text(versionResource, "tag"),
                mapProductRepo(versionResource),
                blueprintName,
                blueprintVersionNumber,
                mapParameters(blueprint)
        );

        int timeoutSeconds = Math.max(1, validatorProperties.getEvaluationTimeoutSeconds());
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "blueprint-service-validator-eval");
            thread.setDaemon(true);
            return thread;
        });
        Future<?> future = executor.submit(() ->
                integrityFactory.buildEvaluateProtectedResourcesIntegrity(command, holder).execute());
        try {
            future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            holder.timeout("Protected-resource check timed out after " + timeoutSeconds + "s");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            holder.presentInfrastructureFailure("Protected-resource check was interrupted");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            log.warn("Protected-resources integrity evaluation failed: {}", cause.getMessage());
            holder.presentInfrastructureFailure(cause.getMessage() == null
                    ? cause.getClass().getSimpleName()
                    : cause.getMessage());
        } finally {
            executor.shutdownNow();
        }
        return toResult(document.getPolicyEvaluationId(), holder.outcome);
    }

    JsonNode extractVersionResource(JsonNode objectToEvaluate) {
        JsonNode eventContent = objectToEvaluate.get("eventContent");
        if (eventContent != null && eventContent.has("dataProductVersion") && eventContent.get("dataProductVersion").isObject()) {
            return eventContent.get("dataProductVersion");
        }
        if (objectToEvaluate.has("dataProductVersion") && objectToEvaluate.get("dataProductVersion").isObject()) {
            return objectToEvaluate.get("dataProductVersion");
        }
        if (objectToEvaluate.has("content")
                && (objectToEvaluate.has("tag") || objectToEvaluate.has("dataProduct"))) {
            return objectToEvaluate;
        }
        return objectToEvaluate;
    }

    private ProductRepoLocator mapProductRepo(JsonNode versionResource) {
        if (versionResource == null) {
            return null;
        }
        JsonNode dataProduct = versionResource.get("dataProduct");
        JsonNode repo = dataProduct == null ? null : dataProduct.get("dataProductRepo");
        if (repo == null || repo.isNull() || !repo.isObject()) {
            return null;
        }
        String providerType = text(repo, "providerType");
        if (!StringUtils.hasText(providerType)) {
            providerType = text(repo, "dataProductRepoProviderType");
        }
        return new ProductRepoLocator(
                text(repo, "remoteUrlHttp"),
                providerType,
                text(repo, "providerBaseUrl"),
                text(repo, "name"),
                text(repo, "defaultBranch"),
                text(repo, "ownerId"),
                text(repo, "externalIdentifier")
        );
    }

    private Map<String, JsonNode> mapParameters(JsonNode blueprint) {
        Map<String, JsonNode> parameters = new LinkedHashMap<>();
        if (blueprint == null || !blueprint.has("parameters") || !blueprint.get("parameters").isObject()) {
            return parameters;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = blueprint.get("parameters").fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            parameters.put(field.getKey(), field.getValue());
        }
        return parameters;
    }

    private PolicyEvaluationResultRes toResult(Long policyEvaluationId, IntegrityOutcome outcome) {
        PolicyEvaluationResultRes result = new PolicyEvaluationResultRes();
        result.setPolicyEvaluationId(policyEvaluationId);
        PolicyEvaluationResultRes.OutputObject output = new PolicyEvaluationResultRes.OutputObject();
        result.setOutputObject(output);
        if (outcome == null) {
            result.setEvaluationResult(false);
            output.setMessage("Protected-resource check did not produce a result");
            return result;
        }
        output.setMessage(outcome.message());
        switch (outcome.kind()) {
            case NOT_APPLICABLE, PASSED -> result.setEvaluationResult(true);
            case FAILED, INFRASTRUCTURE_FAILED -> {
                result.setEvaluationResult(false);
                if (outcome.mismatches() != null && !outcome.mismatches().isEmpty()) {
                    output.setRawError(objectMapper.valueToTree(outcome.mismatches()));
                } else {
                    output.setRawError(objectMapper.createObjectNode().put("cause", outcome.message()));
                }
            }
        }
        return result;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        return value.isTextual() ? value.asText() : value.asText(null);
    }

    static final class OutcomeHolder implements EvaluateProtectedResourcesIntegrityPresenter {
        private volatile IntegrityOutcome outcome;
        private volatile boolean sealed;

        @Override
        public void presentNotApplicable(String reason) {
            if (!sealed) {
                this.outcome = IntegrityOutcome.notApplicable(reason);
            }
        }

        @Override
        public void presentPassed(String message) {
            if (!sealed) {
                this.outcome = IntegrityOutcome.passed(message);
            }
        }

        @Override
        public void presentFailed(List<ProtectedResourceMismatch> mismatches, String message) {
            if (!sealed) {
                this.outcome = IntegrityOutcome.failed(mismatches, message);
            }
        }

        @Override
        public void presentInfrastructureFailure(String message) {
            if (!sealed) {
                this.outcome = IntegrityOutcome.infrastructureFailed(message);
            }
        }

        void timeout(String message) {
            this.sealed = true;
            this.outcome = IntegrityOutcome.infrastructureFailed(message);
        }
    }
}
