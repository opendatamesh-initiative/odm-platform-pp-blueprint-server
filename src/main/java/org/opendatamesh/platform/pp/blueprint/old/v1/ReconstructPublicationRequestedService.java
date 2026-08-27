package org.opendatamesh.platform.pp.blueprint.old.v1;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.opendatamesh.platform.pp.blueprint.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.blueprint.old.v1.resources.RegistryProductRes;
import org.opendatamesh.platform.pp.blueprint.old.v1.resources.RegistryProductVersionRes;
import org.opendatamesh.platform.pp.blueprint.old.v1.resources.PolicyEvaluationRequestRes;
import org.opendatamesh.platform.pp.blueprint.old.v1.resources.PolicyEvaluationResultRes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Policy V1 evaluate adapter: reconstruct a V2 nested version resource from Registry, then
 * delegate to {@link ProtectedResourcesPolicyValidatorService}. Registry is called only here so this
 * package can be deleted when Policy V2 forwards {@code DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED}
 * with tag + repo.
 */
@Service
public class ReconstructPublicationRequestedService {

    static final String MISSING_CLONE_METADATA =
            "Cannot check protected resources: the data product version is missing its Git repository or tag";
    static final String MISSING_IDENTITY =
            "Cannot check protected resources: the data product name or version could not be determined";

    private static final Logger log = LoggerFactory.getLogger(ReconstructPublicationRequestedService.class);

    private final RegistryClient registryClient;
    private final ProtectedResourcesPolicyValidatorService validatorService;
    private final ObjectMapper objectMapper;

    public ReconstructPublicationRequestedService(
            RegistryClient registryClient,
            ProtectedResourcesPolicyValidatorService validatorService,
            ObjectMapper objectMapper
    ) {
        this.registryClient = registryClient;
        this.validatorService = validatorService;
        this.objectMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public PolicyEvaluationResultRes evaluate(PolicyEvaluationRequestRes document) {
        if (document == null || document.getObjectToEvaluate() == null || !document.getObjectToEvaluate().isObject()) {
            throw new BadRequestException("Empty/Malformed Policy Evaluation Object");
        }

        JsonNode objectToEvaluate = document.getObjectToEvaluate();
        if (isAlreadyV2Shaped(objectToEvaluate)) {
            return validatorService.evaluate(document);
        }

        try {
            JsonNode reconstructed = reconstructVersionResource(objectToEvaluate);
            PolicyEvaluationRequestRes rebuilt = new PolicyEvaluationRequestRes();
            rebuilt.setPolicyEvaluationId(document.getPolicyEvaluationId());
            rebuilt.setPolicy(document.getPolicy());
            rebuilt.setObjectToEvaluate(reconstructed);
            return validatorService.evaluate(rebuilt);
        } catch (RegistryReconstructionException e) {
            log.warn("Protected-resources V1 reconstruction failed: {}", e.getMessage());
            return failClosed(document.getPolicyEvaluationId(), e.getMessage());
        }
    }

    JsonNode reconstructVersionResource(JsonNode objectToEvaluate) {
        JsonNode afterState = objectToEvaluate.get("afterState");
        JsonNode currentState = objectToEvaluate.get("currentState");
        boolean afterIsObject = afterState != null && afterState.isObject();
        boolean currentIsObject = currentState != null && currentState.isObject();
        if (!afterIsObject && !currentIsObject) {
            throw new BadRequestException("Empty/Malformed Policy Evaluation Object");
        }

        JsonNode descriptor = extractDescriptor(afterState);
        String fqn = readFqn(descriptor);
        String versionNumber = readVersion(descriptor);
        if (!StringUtils.hasText(fqn) || !StringUtils.hasText(versionNumber)) {
            throw new RegistryReconstructionException(MISSING_IDENTITY);
        }

        log.debug("Reconstructing V2 version resource from Registry for FQN={} version={}", fqn, versionNumber);

        RegistryProductRes product = uniqueProduct(fqn);
        RegistryProductVersionRes versionSummary = uniqueVersion(product.getUuid(), versionNumber);
        JsonNode version = registryClient.getVersion(versionSummary.getUuid());
        if (version == null || !version.isObject()) {
            throw new RegistryReconstructionException(
                    "Registry returned an empty data product version for uuid " + versionSummary.getUuid());
        }

        ObjectNode reconstructed = (ObjectNode) version.deepCopy();
        if (!hasNestedRepo(reconstructed)) {
            nestProduct(reconstructed, product.getUuid());
        }
        if (!hasText(reconstructed, "tag") || !hasCloneUrl(reconstructed)) {
            throw new RegistryReconstructionException(MISSING_CLONE_METADATA);
        }
        return reconstructed;
    }

    private RegistryProductRes uniqueProduct(String fqn) {
        Page<RegistryProductRes> page = registryClient.searchProductsByFqn(fqn);
        List<RegistryProductRes> products = page == null ? List.of() : page.getContent();
        if (products.isEmpty()) {
            throw new RegistryReconstructionException("no data product found in Registry for FQN '" + fqn + "'");
        }
        if (products.size() > 1) {
            throw new RegistryReconstructionException("multiple data products found in Registry for FQN '" + fqn + "'");
        }
        RegistryProductRes product = products.getFirst();
        if (product == null || !StringUtils.hasText(product.getUuid())) {
            throw new RegistryReconstructionException("Registry product for FQN '" + fqn + "' has no uuid");
        }
        return product;
    }

    private RegistryProductVersionRes uniqueVersion(String productUuid, String versionNumber) {
        Page<RegistryProductVersionRes> page = registryClient.searchVersions(productUuid, versionNumber);
        List<RegistryProductVersionRes> versions = page == null ? List.of() : page.getContent();
        if (versions.isEmpty()) {
            throw new RegistryReconstructionException(
                    "no data product version found in Registry for product '" + productUuid
                            + "' version '" + versionNumber + "'");
        }
        if (versions.size() > 1) {
            throw new RegistryReconstructionException(
                    "multiple data product versions found in Registry for product '" + productUuid
                            + "' version '" + versionNumber + "'");
        }
        RegistryProductVersionRes version = versions.getFirst();
        if (version == null || !StringUtils.hasText(version.getUuid())) {
            throw new RegistryReconstructionException(
                    "Registry version for product '" + productUuid + "' version '" + versionNumber + "' has no uuid");
        }
        return version;
    }

    private void nestProduct(ObjectNode version, String productUuid) {
        JsonNode nestedProduct = version.get("dataProduct");
        String uuid = firstNonBlank(text(nestedProduct, "uuid"), productUuid);
        if (!StringUtils.hasText(uuid)) {
            throw new RegistryReconstructionException(MISSING_CLONE_METADATA);
        }
        JsonNode product = registryClient.getProduct(uuid);
        if (product == null || !product.isObject()) {
            throw new RegistryReconstructionException(MISSING_CLONE_METADATA);
        }
        version.set("dataProduct", product);
    }

    /**
     * V2 publication-event and raw Registry version shapes skip Registry. Existing integrity ITs
     * POST {@code eventContent.dataProductVersion}; Policy V2 will POST the nested version resource.
     */
    static boolean isAlreadyV2Shaped(JsonNode objectToEvaluate) {
        if (objectToEvaluate.path("eventContent").path("dataProductVersion").isObject()) {
            return true;
        }
        JsonNode version = objectToEvaluate;
        if (objectToEvaluate.has("dataProductVersion") && objectToEvaluate.get("dataProductVersion").isObject()) {
            version = objectToEvaluate.get("dataProductVersion");
        }
        if (!version.has("content")) {
            return false;
        }
        return hasText(version, "tag") || version.has("dataProduct") || hasNestedRepo(version);
    }

    private static JsonNode extractDescriptor(JsonNode afterState) {
        if (afterState == null || !afterState.isObject()) {
            return null;
        }
        JsonNode wrapped = afterState.get("dataProductVersion");
        if (wrapped != null && wrapped.isObject()) {
            return wrapped;
        }
        if (afterState.has("info")) {
            return afterState;
        }
        return afterState;
    }

    private static String readFqn(JsonNode descriptor) {
        if (descriptor == null) {
            return null;
        }
        JsonNode info = descriptor.get("info");
        String fqn = firstNonBlank(text(info, "fullyQualifiedName"), text(info, "fqn"));
        if (StringUtils.hasText(fqn)) {
            return fqn;
        }
        return firstNonBlank(text(descriptor, "fullyQualifiedName"), text(descriptor, "fqn"));
    }

    private static String readVersion(JsonNode descriptor) {
        if (descriptor == null) {
            return null;
        }
        JsonNode info = descriptor.get("info");
        String version = firstNonBlank(text(info, "version"), text(info, "versionNumber"));
        if (StringUtils.hasText(version)) {
            return version;
        }
        return firstNonBlank(text(descriptor, "version"), text(descriptor, "versionNumber"));
    }

    private static boolean hasNestedRepo(JsonNode version) {
        JsonNode repo = version.path("dataProduct").path("dataProductRepo");
        return repo.isObject();
    }

    private static boolean hasCloneUrl(JsonNode version) {
        return StringUtils.hasText(text(version.path("dataProduct").path("dataProductRepo"), "remoteUrlHttp"));
    }

    private static boolean hasText(JsonNode node, String field) {
        return StringUtils.hasText(text(node, field));
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        return value.isTextual() ? value.asText() : value.asText(null);
    }

    private static String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private PolicyEvaluationResultRes failClosed(Long policyEvaluationId, String message) {
        PolicyEvaluationResultRes result = new PolicyEvaluationResultRes();
        result.setPolicyEvaluationId(policyEvaluationId);
        result.setEvaluationResult(false);
        PolicyEvaluationResultRes.OutputObject output = new PolicyEvaluationResultRes.OutputObject();
        output.setMessage(message);
        output.setRawError(objectMapper.createObjectNode().put("cause", message));
        result.setOutputObject(output);
        return result;
    }
}
