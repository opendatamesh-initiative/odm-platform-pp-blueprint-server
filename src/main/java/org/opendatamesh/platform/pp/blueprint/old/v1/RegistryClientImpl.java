package org.opendatamesh.platform.pp.blueprint.old.v1;

import com.fasterxml.jackson.databind.JsonNode;
import org.opendatamesh.platform.pp.blueprint.exceptions.client.ClientException;
import org.opendatamesh.platform.pp.blueprint.exceptions.client.ClientResourceMappingException;
import org.opendatamesh.platform.pp.blueprint.old.v1.resources.RegistryProductRes;
import org.opendatamesh.platform.pp.blueprint.old.v1.resources.RegistryProductSearchOptions;
import org.opendatamesh.platform.pp.blueprint.old.v1.resources.RegistryProductVersionRes;
import org.opendatamesh.platform.pp.blueprint.old.v1.resources.RegistryProductVersionSearchOptions;
import org.opendatamesh.platform.pp.blueprint.utils.client.RestUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

class RegistryClientImpl implements RegistryClient {

    private static final String PRODUCTS_ROUTE = "/api/v2/pp/registry/products";
    private static final String VERSIONS_ROUTE = "/api/v2/pp/registry/products-versions";
    private static final Pageable LOOKUP_PAGE = Pageable.ofSize(2);

    private final RestUtils restUtils;
    private final String registryServiceBaseUrl;

    RegistryClientImpl(RestUtils restUtils, String registryServiceBaseUrl) {
        this.restUtils = restUtils;
        this.registryServiceBaseUrl = registryServiceBaseUrl;
    }

    @Override
    public Page<RegistryProductRes> searchProductsByFqn(String fqn) {
        RegistryProductSearchOptions filters = new RegistryProductSearchOptions();
        filters.setFqn(fqn);
        try {
            return restUtils.getPage(
                    registryServiceBaseUrl + PRODUCTS_ROUTE,
                    null,
                    LOOKUP_PAGE,
                    filters,
                    RegistryProductRes.class
            );
        } catch (ClientException | ClientResourceMappingException e) {
            throw wrap(e);
        }
    }

    @Override
    public Page<RegistryProductVersionRes> searchVersions(String productUuid, String versionNumber) {
        RegistryProductVersionSearchOptions filters = new RegistryProductVersionSearchOptions();
        filters.setDataProductUuid(productUuid);
        filters.setVersionNumber(versionNumber);
        try {
            return restUtils.getPage(
                    registryServiceBaseUrl + VERSIONS_ROUTE,
                    null,
                    LOOKUP_PAGE,
                    filters,
                    RegistryProductVersionRes.class
            );
        } catch (ClientException | ClientResourceMappingException e) {
            throw wrap(e);
        }
    }

    @Override
    public JsonNode getVersion(String uuid) {
        try {
            return restUtils.get(
                    registryServiceBaseUrl + VERSIONS_ROUTE + "/{uuid}",
                    null,
                    uuid,
                    JsonNode.class
            );
        } catch (ClientException | ClientResourceMappingException e) {
            throw wrap(e);
        }
    }

    @Override
    public JsonNode getProduct(String uuid) {
        try {
            return restUtils.get(
                    registryServiceBaseUrl + PRODUCTS_ROUTE + "/{uuid}",
                    null,
                    uuid,
                    JsonNode.class
            );
        } catch (ClientException | ClientResourceMappingException e) {
            throw wrap(e);
        }
    }

    private static RegistryReconstructionException wrap(RuntimeException e) {
        if (e instanceof ClientException clientException) {
            return new RegistryReconstructionException(
                    "Registry request failed (HTTP " + clientException.getCode() + ")",
                    e
            );
        }
        return new RegistryReconstructionException("Registry response could not be read", e);
    }
}
