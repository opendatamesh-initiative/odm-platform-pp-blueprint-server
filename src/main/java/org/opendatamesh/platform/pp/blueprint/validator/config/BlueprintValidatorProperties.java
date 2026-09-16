package org.opendatamesh.platform.pp.blueprint.validator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "blueprint.validator")
public class BlueprintValidatorProperties {

    private boolean active = false;
    private int evaluationTimeoutSeconds = 120;
    private PolicyEngine policyEngine = new PolicyEngine();
    private Policy policy = new Policy();
    private Git git = new Git();

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getEvaluationTimeoutSeconds() {
        return evaluationTimeoutSeconds;
    }

    public void setEvaluationTimeoutSeconds(int evaluationTimeoutSeconds) {
        this.evaluationTimeoutSeconds = evaluationTimeoutSeconds;
    }

    public PolicyEngine getPolicyEngine() {
        return policyEngine;
    }

    public void setPolicyEngine(PolicyEngine policyEngine) {
        this.policyEngine = policyEngine;
    }

    public Policy getPolicy() {
        return policy;
    }

    public void setPolicy(Policy policy) {
        this.policy = policy;
    }

    public Git getGit() {
        return git;
    }

    public void setGit(Git git) {
        this.git = git;
    }

    public static class PolicyEngine {
        private String name = "blueprint-service-validator";
        private String displayName = "Blueprint Service Validator";

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }
    }

    public static class Policy {
        private String name = "Protected Resources Integrity";
        private boolean blocking = true;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isBlocking() {
            return blocking;
        }

        public void setBlocking(boolean blocking) {
            this.blocking = blocking;
        }
    }

    public static class Git {
        private List<GitCredential> credentials = new ArrayList<>();

        public List<GitCredential> getCredentials() {
            return credentials;
        }

        public void setCredentials(List<GitCredential> credentials) {
            this.credentials = credentials != null ? credentials : new ArrayList<>();
        }
    }

    public static class GitCredential {
        private String providerType;
        private String providerBaseUrl;
        private String authType;
        private String token;
        private String username;

        public String getProviderType() {
            return providerType;
        }

        public void setProviderType(String providerType) {
            this.providerType = providerType;
        }

        public String getProviderBaseUrl() {
            return providerBaseUrl;
        }

        public void setProviderBaseUrl(String providerBaseUrl) {
            this.providerBaseUrl = providerBaseUrl;
        }

        public String getAuthType() {
            return authType;
        }

        public void setAuthType(String authType) {
            this.authType = authType;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }
    }
}
