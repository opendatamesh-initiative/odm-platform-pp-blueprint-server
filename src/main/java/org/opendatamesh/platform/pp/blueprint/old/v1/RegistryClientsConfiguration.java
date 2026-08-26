package org.opendatamesh.platform.pp.blueprint.old.v1;

import com.fasterxml.jackson.databind.JsonNode;
import org.opendatamesh.platform.pp.blueprint.old.v1.resources.RegistryProductRes;
import org.opendatamesh.platform.pp.blueprint.old.v1.resources.RegistryProductVersionRes;
import org.opendatamesh.platform.pp.blueprint.utils.client.RestUtilsFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.util.StringUtils;

/**
 * Adapter-only Registry client. Unused after {@code old/v1} is deleted.
 */
@Configuration
public class RegistryClientsConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RegistryClientsConfiguration.class);

    private final RestTemplateBuilder restTemplateBuilder;
    private final boolean registryServiceActive;
    private final String registryServiceAddress;

    public RegistryClientsConfiguration(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${odm.product-plane.registry-service.active:false}") boolean registryServiceActive,
            @Value("${odm.product-plane.registry-service.address:}") String registryServiceAddress
    ) {
        this.restTemplateBuilder = restTemplateBuilder;
        this.registryServiceActive = registryServiceActive;
        this.registryServiceAddress = registryServiceAddress;
    }

    @Bean
    public RegistryClient registryClient() {
        if (useRealClient()) {
            return new RegistryClientImpl(
                    RestUtilsFactory.getRestUtils(restTemplateBuilder.build()),
                    registryServiceAddress
            );
        }
        log.warn("ODM Registry Client is not enabled (registry-service inactive or address blank). "
                + "V1 reconstruction will fail closed; V2-shaped evaluate payloads still pass through.");
        return new RegistryClient() {
            @Override
            public Page<RegistryProductRes> searchProductsByFqn(String fqn) {
                throw notConfigured();
            }

            @Override
            public Page<RegistryProductVersionRes> searchVersions(String productUuid, String versionNumber) {
                throw notConfigured();
            }

            @Override
            public JsonNode getVersion(String uuid) {
                throw notConfigured();
            }

            @Override
            public JsonNode getProduct(String uuid) {
                throw notConfigured();
            }
        };
    }

    private boolean useRealClient() {
        return registryServiceActive && StringUtils.hasText(registryServiceAddress);
    }

    private static RegistryReconstructionException notConfigured() {
        return new RegistryReconstructionException("Registry not configured");
    }
}
