package org.opendatamesh.platform.pp.blueprint.old.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.opendatamesh.platform.pp.blueprint.old.v1.resources.PolicyEvaluationRequestRes;
import org.opendatamesh.platform.pp.blueprint.old.v1.resources.PolicyEvaluationResultRes;
import org.opendatamesh.platform.pp.blueprint.old.v1.resources.RegistryProductRes;
import org.opendatamesh.platform.pp.blueprint.old.v1.resources.RegistryProductVersionRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.BlueprintApplicationIT;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.springframework.data.domain.PageImpl;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Integration test that Policy V1 evaluate payloads reconstruct via Registry then hit the policy validator.
 * Scenarios trace to {@code spdd/prompt/BDMD-5124-202608241546-[Feat]-service-v1-protected-resources-policy-adapter.md} (Gherkin).
 */
public class OldV1ProtectedResourcesValidatorControllerIT extends BlueprintApplicationIT {

    private static final String EVALUATE_PATH = "/api/v1/up/validator/evaluate-policy";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String FQN = "urn:org:dp:reconstructed";
    private static final String VERSION_NUMBER = "2.0.0";
    private static final String PRODUCT_UUID = "reconstructed-product-uuid";
    private static final String VERSION_UUID = "reconstructed-version-uuid";

    @MockitoBean
    private RegistryClient registryClient;

    /**
     * Feature: Reconstruct V2 publication object from Policy V1
     *
     * Scenario: Reconstructed content without lineage is not applicable
     *   Given a Policy V1 evaluate request with afterState identity
     *   And Registry GET version returns content without blueprint lineage
     *   When the evaluate endpoint is called
     *   Then the response status is 200
     *   And evaluationResult is true
     *   And the message states the version was not created from a blueprint
     */
    @Test
    void v1AfterStateWithReconstructedContentWithoutLineageIsNotApplicable() {
        RegistryProductRes product = new RegistryProductRes();
        product.setUuid(PRODUCT_UUID);
        product.setFqn(FQN);
        RegistryProductVersionRes versionSummary = new RegistryProductVersionRes();
        versionSummary.setUuid(VERSION_UUID);
        versionSummary.setVersionNumber(VERSION_NUMBER);
        versionSummary.setTag("v2.0.0");
        when(registryClient.searchProductsByFqn(FQN)).thenReturn(new PageImpl<>(List.of(product)));
        when(registryClient.searchVersions(PRODUCT_UUID, VERSION_NUMBER))
                .thenReturn(new PageImpl<>(List.of(versionSummary)));
        when(registryClient.getVersion(VERSION_UUID)).thenReturn(reconstructedVersionWithoutLineage());
        when(registryClient.getProduct(anyString())).thenReturn(productWithRepo());

        PolicyEvaluationRequestRes request = new PolicyEvaluationRequestRes();
        request.setPolicyEvaluationId(99L);
        request.setObjectToEvaluate(v1Payload());

        ResponseEntity<PolicyEvaluationResultRes> response = rest.exchange(
                apiUrlFromString(EVALUATE_PATH),
                HttpMethod.POST,
                new HttpEntity<>(request, jsonHeaders()),
                PolicyEvaluationResultRes.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getEvaluationResult()).isTrue();
        assertThat(response.getBody().getOutputObject().getMessage()).contains("not created from a blueprint");
    }

    private ObjectNode v1Payload() {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.putNull("currentState");
        ObjectNode afterState = root.putObject("afterState");
        ObjectNode descriptor = afterState.putObject("dataProductVersion");
        ObjectNode info = descriptor.putObject("info");
        info.put("fullyQualifiedName", FQN);
        info.put("version", VERSION_NUMBER);
        ObjectNode blueprint = descriptor.putObject("blueprint");
        blueprint.put("blueprintName", "from-v1-descriptor");
        blueprint.put("blueprintVersionNumber", "9.9.9");
        return root;
    }

    private ObjectNode reconstructedVersionWithoutLineage() {
        ObjectNode version = OBJECT_MAPPER.createObjectNode();
        version.put("uuid", VERSION_UUID);
        version.put("tag", "v2.0.0");
        ObjectNode content = version.putObject("content");
        content.putObject("info").put("fullyQualifiedName", FQN);
        ObjectNode dataProduct = version.putObject("dataProduct");
        dataProduct.put("uuid", PRODUCT_UUID);
        dataProduct.set("dataProductRepo", repoNode());
        return version;
    }

    private ObjectNode productWithRepo() {
        ObjectNode product = OBJECT_MAPPER.createObjectNode();
        product.put("uuid", PRODUCT_UUID);
        product.put("fqn", FQN);
        product.set("dataProductRepo", repoNode());
        return product;
    }

    private ObjectNode repoNode() {
        ObjectNode repo = OBJECT_MAPPER.createObjectNode();
        repo.put("remoteUrlHttp", "https://github.com/org/customer360.git");
        repo.put("providerType", "GITHUB");
        repo.put("providerBaseUrl", "https://github.com");
        return repo;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
