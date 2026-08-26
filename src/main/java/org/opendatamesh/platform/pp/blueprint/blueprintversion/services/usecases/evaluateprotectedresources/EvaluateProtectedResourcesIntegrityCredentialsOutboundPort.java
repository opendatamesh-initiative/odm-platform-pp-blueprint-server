package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources;

import org.springframework.http.HttpHeaders;

import java.util.Optional;

interface EvaluateProtectedResourcesIntegrityCredentialsOutboundPort {

    Optional<HttpHeaders> resolveHeaders(String providerType, String providerBaseUrl);
}
