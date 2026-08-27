package org.opendatamesh.platform.pp.blueprint.validator.config;

import org.opendatamesh.platform.git.provider.GitProviderIdentifier;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Maps configured service Git credentials to the {@code x-odm-gpauth-*} headers
 * {@code GitProviderFactory} already understands. Tokens are never logged.
 */
public final class ValidatorGitCredentialHeaders {

    public static final String HEADER_AUTH_TYPE = "x-odm-gpauth-type";
    public static final String HEADER_TOKEN = "x-odm-gpauth-param-token";
    public static final String HEADER_USERNAME = "x-odm-gpauth-param-username";

    private ValidatorGitCredentialHeaders() {
    }

    public static Optional<HttpHeaders> resolve(BlueprintValidatorProperties properties, GitProviderIdentifier identifier) {
        if (identifier == null || !StringUtils.hasText(identifier.type())) {
            return Optional.empty();
        }
        return resolve(properties, identifier.type(), identifier.baseUrl());
    }

    public static Optional<HttpHeaders> resolve(
            BlueprintValidatorProperties properties,
            String providerType,
            String providerBaseUrl
    ) {
        if (properties == null || properties.getGit() == null || !StringUtils.hasText(providerType)) {
            return Optional.empty();
        }
        BlueprintValidatorProperties.GitCredential match = selectCredential(
                properties.getGit().getCredentials(),
                providerType,
                providerBaseUrl
        );
        if (match == null || !StringUtils.hasText(match.getToken())) {
            return Optional.empty();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_AUTH_TYPE, StringUtils.hasText(match.getAuthType()) ? match.getAuthType() : "PAT");
        headers.set(HEADER_TOKEN, match.getToken());
        if (StringUtils.hasText(match.getUsername())) {
            headers.set(HEADER_USERNAME, match.getUsername());
        }
        return Optional.of(headers);
    }

    private static BlueprintValidatorProperties.GitCredential selectCredential(
            List<BlueprintValidatorProperties.GitCredential> credentials,
            String providerType,
            String providerBaseUrl
    ) {
        if (credentials == null || credentials.isEmpty()) {
            return null;
        }
        String wantedType = providerType.trim().toUpperCase(Locale.ROOT);
        BlueprintValidatorProperties.GitCredential typeAndBaseUrl = null;
        BlueprintValidatorProperties.GitCredential typeAndEmptyBaseUrl = null;
        for (BlueprintValidatorProperties.GitCredential credential : credentials) {
            if (credential == null || !StringUtils.hasText(credential.getProviderType())) {
                continue;
            }
            if (!wantedType.equals(credential.getProviderType().trim().toUpperCase(Locale.ROOT))) {
                continue;
            }
            if (StringUtils.hasText(credential.getProviderBaseUrl())) {
                if (StringUtils.hasText(providerBaseUrl)
                        && credential.getProviderBaseUrl().equals(providerBaseUrl)) {
                    typeAndBaseUrl = credential;
                }
            } else if (typeAndEmptyBaseUrl == null) {
                typeAndEmptyBaseUrl = credential;
            }
        }
        return typeAndBaseUrl != null ? typeAndBaseUrl : typeAndEmptyBaseUrl;
    }
}
