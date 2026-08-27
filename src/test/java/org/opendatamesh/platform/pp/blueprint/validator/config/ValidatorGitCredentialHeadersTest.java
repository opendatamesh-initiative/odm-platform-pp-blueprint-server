package org.opendatamesh.platform.pp.blueprint.validator.config;

import org.junit.jupiter.api.Test;
import org.opendatamesh.platform.git.provider.GitProviderIdentifier;
import org.springframework.http.HttpHeaders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ValidatorGitCredentialHeadersTest {

    @Test
    void prefersMatchingBaseUrlThenEmptyBaseUrl() {
        BlueprintValidatorProperties properties = new BlueprintValidatorProperties();
        BlueprintValidatorProperties.GitCredential githubCloud = credential("GITHUB", "", "cloud-token");
        BlueprintValidatorProperties.GitCredential githubEnterprise = credential("GITHUB", "https://git.example", "ent-token");
        properties.getGit().setCredentials(List.of(githubCloud, githubEnterprise));

        HttpHeaders enterprise = ValidatorGitCredentialHeaders.resolve(
                properties, new GitProviderIdentifier("GITHUB", "https://git.example")).orElseThrow();
        assertThat(enterprise.getFirst(ValidatorGitCredentialHeaders.HEADER_TOKEN)).isEqualTo("ent-token");

        HttpHeaders cloud = ValidatorGitCredentialHeaders.resolve(
                properties, new GitProviderIdentifier("GITHUB", "https://github.com")).orElseThrow();
        assertThat(cloud.getFirst(ValidatorGitCredentialHeaders.HEADER_TOKEN)).isEqualTo("cloud-token");
    }

    @Test
    void blankTokenIsNoMatch() {
        BlueprintValidatorProperties properties = new BlueprintValidatorProperties();
        properties.getGit().setCredentials(List.of(credential("GITHUB", "", "")));
        assertThat(ValidatorGitCredentialHeaders.resolve(properties, "GITHUB", null)).isEmpty();
    }

    private static BlueprintValidatorProperties.GitCredential credential(String type, String baseUrl, String token) {
        BlueprintValidatorProperties.GitCredential credential = new BlueprintValidatorProperties.GitCredential();
        credential.setProviderType(type);
        credential.setProviderBaseUrl(baseUrl);
        credential.setAuthType("PAT");
        credential.setToken(token);
        return credential;
    }
}
