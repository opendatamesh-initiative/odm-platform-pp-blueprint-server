package org.opendatamesh.platform.pp.blueprint.old.v1;

import com.fasterxml.jackson.databind.JsonNode;
import org.opendatamesh.platform.pp.blueprint.old.v1.resources.RegistryProductRes;
import org.opendatamesh.platform.pp.blueprint.old.v1.resources.RegistryProductVersionRes;
import org.springframework.data.domain.Page;

/**
 * Registry V2 HTTP client used only by the Policy V1 reconstruction adapter.
 * Delete with {@code old/v1} when Policy V2 forwards nested tag + product repo.
 */
public interface RegistryClient {

    Page<RegistryProductRes> searchProductsByFqn(String fqn);

    Page<RegistryProductVersionRes> searchVersions(String productUuid, String versionNumber);

    JsonNode getVersion(String uuid);

    JsonNode getProduct(String uuid);
}
