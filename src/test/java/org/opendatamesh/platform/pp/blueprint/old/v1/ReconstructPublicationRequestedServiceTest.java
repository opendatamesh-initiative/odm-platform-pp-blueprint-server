package org.opendatamesh.platform.pp.blueprint.old.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendatamesh.platform.pp.blueprint.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.blueprint.old.v1.resources.RegistryProductRes;
import org.opendatamesh.platform.pp.blueprint.old.v1.resources.RegistryProductVersionRes;
import org.opendatamesh.platform.pp.blueprint.validator.resources.PolicyEvaluationRequestRes;
import org.opendatamesh.platform.pp.blueprint.validator.resources.PolicyEvaluationResultRes;
import org.opendatamesh.platform.pp.blueprint.validator.services.ProtectedResourcesValidatorService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconstructPublicationRequestedServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String FQN = "urn:org:dp:customer360";
    private static final String VERSION_NUMBER = "1.2.0";
    private static final String PRODUCT_UUID = "product-uuid";
    private static final String VERSION_UUID = "version-uuid";

    @Mock
    private RegistryClient registryClient;
    @Mock
    private ProtectedResourcesValidatorService validatorService;

    private ReconstructPublicationRequestedService service;

    @BeforeEach
    void setUp() {
        service = new ReconstructPublicationRequestedService(registryClient, validatorService, OBJECT_MAPPER);
    }

    @Test
    void v2ShapedPayloadSkipsRegistryAndDelegates() {
        PolicyEvaluationRequestRes request = request(v2VersionResource(true, true));
        PolicyEvaluationResultRes expected = passed(request.getPolicyEvaluationId());
        when(validatorService.evaluate(request)).thenReturn(expected);

        PolicyEvaluationResultRes result = service.evaluate(request);

        assertThat(result).isSameAs(expected);
        verify(registryClient, never()).searchProductsByFqn(any());
        verify(registryClient, never()).getVersion(any());
        verify(validatorService).evaluate(request);
    }

    @Test
    void v1AfterStateReconstructsAndDelegatesGetVersionBody() {
        stubUniqueLookup();
        ObjectNode versionFromRegistry = v2VersionResource(true, true);
        versionFromRegistry.put("uuid", VERSION_UUID);
        when(registryClient.getVersion(VERSION_UUID)).thenReturn(versionFromRegistry);

        PolicyEvaluationResultRes expected = passed(7L);
        when(validatorService.evaluate(any())).thenReturn(expected);

        PolicyEvaluationRequestRes request = request(v1Payload(FQN, VERSION_NUMBER));
        request.setPolicyEvaluationId(7L);
        PolicyEvaluationResultRes result = service.evaluate(request);

        assertThat(result).isSameAs(expected);
        ArgumentCaptor<PolicyEvaluationRequestRes> captor = ArgumentCaptor.forClass(PolicyEvaluationRequestRes.class);
        verify(validatorService).evaluate(captor.capture());
        PolicyEvaluationRequestRes delegated = captor.getValue();
        assertThat(delegated.getPolicyEvaluationId()).isEqualTo(7L);
        assertThat(delegated.getObjectToEvaluate().path("uuid").asText()).isEqualTo(VERSION_UUID);
        assertThat(delegated.getObjectToEvaluate().path("tag").asText()).isEqualTo("v1.2.0");
        assertThat(delegated.getObjectToEvaluate().path("dataProduct").path("dataProductRepo").path("remoteUrlHttp").asText())
                .isEqualTo("https://github.com/org/customer360.git");
    }

    @Test
    void missingFqnAndVersionReturns200FalseWithoutIntegrity() {
        PolicyEvaluationRequestRes request = request(v1Payload(null, null));
        request.setPolicyEvaluationId(3L);

        PolicyEvaluationResultRes result = service.evaluate(request);

        assertThat(result.getEvaluationResult()).isFalse();
        assertThat(result.getPolicyEvaluationId()).isEqualTo(3L);
        assertThat(result.getOutputObject().getMessage())
                .isEqualTo(ReconstructPublicationRequestedService.MISSING_IDENTITY);
        verify(registryClient, never()).searchProductsByFqn(any());
        verify(validatorService, never()).evaluate(any());
    }

    @Test
    void zeroProductsReturns200False() {
        when(registryClient.searchProductsByFqn(FQN)).thenReturn(Page.empty());

        PolicyEvaluationResultRes result = service.evaluate(request(v1Payload(FQN, VERSION_NUMBER)));

        assertThat(result.getEvaluationResult()).isFalse();
        assertThat(result.getOutputObject().getMessage()).contains("no data product found");
        verify(registryClient, never()).searchVersions(any(), any());
        verify(validatorService, never()).evaluate(any());
    }

    @Test
    void multipleProductsReturns200False() {
        when(registryClient.searchProductsByFqn(FQN)).thenReturn(new PageImpl<>(List.of(
                product("one"), product("two"))));

        PolicyEvaluationResultRes result = service.evaluate(request(v1Payload(FQN, VERSION_NUMBER)));

        assertThat(result.getEvaluationResult()).isFalse();
        assertThat(result.getOutputObject().getMessage()).contains("multiple data products found");
        verify(validatorService, never()).evaluate(any());
    }

    @Test
    void getVersionWithoutRepoNestsProductBeforeDelegate() {
        stubUniqueLookup();
        ObjectNode versionWithoutRepo = v2VersionResource(true, false);
        versionWithoutRepo.put("uuid", VERSION_UUID);
        when(registryClient.getVersion(VERSION_UUID)).thenReturn(versionWithoutRepo);
        when(registryClient.getProduct(PRODUCT_UUID)).thenReturn(productWithRepo());
        when(validatorService.evaluate(any())).thenReturn(passed(1L));

        service.evaluate(request(v1Payload(FQN, VERSION_NUMBER)));

        ArgumentCaptor<PolicyEvaluationRequestRes> captor = ArgumentCaptor.forClass(PolicyEvaluationRequestRes.class);
        verify(validatorService).evaluate(captor.capture());
        JsonNode nestedRepo = captor.getValue().getObjectToEvaluate().path("dataProduct").path("dataProductRepo");
        assertThat(nestedRepo.path("remoteUrlHttp").asText()).isEqualTo("https://github.com/org/customer360.git");
        verify(registryClient).getProduct(PRODUCT_UUID);
    }

    @Test
    void registryNotConfiguredAndV1PayloadReturns200False() {
        when(registryClient.searchProductsByFqn(FQN))
                .thenThrow(new RegistryReconstructionException("Registry not configured"));

        PolicyEvaluationResultRes result = service.evaluate(request(v1Payload(FQN, VERSION_NUMBER)));

        assertThat(result.getEvaluationResult()).isFalse();
        assertThat(result.getOutputObject().getMessage()).isEqualTo("Registry not configured");
        verify(validatorService, never()).evaluate(any());
    }

    @Test
    void unreadablePayloadThrowsBadRequest() {
        PolicyEvaluationRequestRes request = new PolicyEvaluationRequestRes();
        request.setPolicyEvaluationId(1L);
        assertThatThrownBy(() -> service.evaluate(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Empty/Malformed Policy Evaluation Object");
        verify(registryClient, never()).searchProductsByFqn(any());
    }

    @Test
    void neitherCurrentNorAfterStateIsObjectThrowsBadRequest() {
        ObjectNode payload = OBJECT_MAPPER.createObjectNode();
        payload.put("afterState", "not-an-object");
        PolicyEvaluationRequestRes request = request(payload);

        assertThatThrownBy(() -> service.evaluate(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Empty/Malformed Policy Evaluation Object");
        verify(validatorService, never()).evaluate(any());
    }

    private void stubUniqueLookup() {
        when(registryClient.searchProductsByFqn(FQN)).thenReturn(new PageImpl<>(List.of(product(PRODUCT_UUID))));
        when(registryClient.searchVersions(PRODUCT_UUID, VERSION_NUMBER))
                .thenReturn(new PageImpl<>(List.of(versionSummary(VERSION_UUID))));
    }

    private static PolicyEvaluationRequestRes request(JsonNode objectToEvaluate) {
        PolicyEvaluationRequestRes request = new PolicyEvaluationRequestRes();
        request.setPolicyEvaluationId(1L);
        request.setObjectToEvaluate(objectToEvaluate);
        return request;
    }

    private static ObjectNode v1Payload(String fqn, String version) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.putNull("currentState");
        ObjectNode afterState = root.putObject("afterState");
        ObjectNode descriptor = afterState.putObject("dataProductVersion");
        ObjectNode info = descriptor.putObject("info");
        if (fqn != null) {
            info.put("fullyQualifiedName", fqn);
        }
        if (version != null) {
            info.put("version", version);
        }
        ObjectNode blueprint = descriptor.putObject("blueprint");
        blueprint.put("blueprintName", "example-blueprint");
        blueprint.put("blueprintVersionNumber", "1.0.0");
        return root;
    }

    private static ObjectNode v2VersionResource(boolean includeTag, boolean includeRepo) {
        ObjectNode version = OBJECT_MAPPER.createObjectNode();
        if (includeTag) {
            version.put("tag", "v1.2.0");
        }
        ObjectNode content = version.putObject("content");
        content.putObject("info").put("fullyQualifiedName", FQN);
        if (includeRepo) {
            ObjectNode dataProduct = version.putObject("dataProduct");
            dataProduct.put("uuid", PRODUCT_UUID);
            dataProduct.set("dataProductRepo", repoNode());
        } else {
            ObjectNode dataProduct = version.putObject("dataProduct");
            dataProduct.put("uuid", PRODUCT_UUID);
        }
        return version;
    }

    private static ObjectNode productWithRepo() {
        ObjectNode product = OBJECT_MAPPER.createObjectNode();
        product.put("uuid", PRODUCT_UUID);
        product.put("fqn", FQN);
        product.set("dataProductRepo", repoNode());
        return product;
    }

    private static ObjectNode repoNode() {
        ObjectNode repo = OBJECT_MAPPER.createObjectNode();
        repo.put("remoteUrlHttp", "https://github.com/org/customer360.git");
        repo.put("providerType", "GITHUB");
        repo.put("providerBaseUrl", "https://github.com");
        return repo;
    }

    private static RegistryProductRes product(String uuid) {
        RegistryProductRes product = new RegistryProductRes();
        product.setUuid(uuid);
        product.setFqn(FQN);
        return product;
    }

    private static RegistryProductVersionRes versionSummary(String uuid) {
        RegistryProductVersionRes version = new RegistryProductVersionRes();
        version.setUuid(uuid);
        version.setVersionNumber(VERSION_NUMBER);
        version.setTag("v1.2.0");
        return version;
    }

    private static PolicyEvaluationResultRes passed(Long id) {
        PolicyEvaluationResultRes result = new PolicyEvaluationResultRes();
        result.setPolicyEvaluationId(id);
        result.setEvaluationResult(true);
        PolicyEvaluationResultRes.OutputObject output = new PolicyEvaluationResultRes.OutputObject();
        output.setMessage("ok");
        result.setOutputObject(output);
        return result;
    }
}
