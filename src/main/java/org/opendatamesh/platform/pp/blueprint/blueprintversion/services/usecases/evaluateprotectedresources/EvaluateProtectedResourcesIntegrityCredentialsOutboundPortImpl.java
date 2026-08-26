package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources;

import org.opendatamesh.platform.pp.blueprint.validator.config.BlueprintValidatorProperties;
import org.opendatamesh.platform.pp.blueprint.validator.config.ValidatorGitCredentialHeaders;
import org.springframework.http.HttpHeaders;

import java.util.Optional;

class EvaluateProtectedResourcesIntegrityCredentialsOutboundPortImpl
        implements EvaluateProtectedResourcesIntegrityCredentialsOutboundPort {

    private final BlueprintValidatorProperties properties;

    EvaluateProtectedResourcesIntegrityCredentialsOutboundPortImpl(BlueprintValidatorProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<HttpHeaders> resolveHeaders(String providerType, String providerBaseUrl) {
        return ValidatorGitCredentialHeaders.resolve(properties, providerType, providerBaseUrl);
    }
}
