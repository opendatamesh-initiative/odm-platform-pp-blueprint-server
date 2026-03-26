package org.opendatamesh.platform.pp.blueprint.rest.v2.controllers;

import org.junit.jupiter.api.Test;
import org.opendatamesh.platform.pp.blueprint.rest.v2.BlueprintApplicationIT;
import org.opendatamesh.platform.pp.blueprint.rest.v2.RoutesV2;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintRepoOwnerTypeRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintRepoProviderTypeRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.usecases.register.RegisterBlueprintCommandRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.usecases.register.RegisterBlueprintResponseRes;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link BlueprintUseCaseController}.
 * Scenarios trace to {@code agentspecs/changes/blueprint/register_blueprint_use_case/specs.md} (Gherkin).
 */
public class BlueprintUseCaseControllerIT extends BlueprintApplicationIT {

    /**
     * REG-BP-001 — Scenario: Successful registration returns 201 and created blueprint
     * (specs.md — Feature: Register blueprint via public use-case endpoint)
     */
    @Test
    public void whenRegisterBlueprintThenReturn201AndCreatedBlueprint() {
        String namePrefix = "whenRegisterBlueprintThenReturn201AndCreatedBlueprint";

        RegisterBlueprintCommandRes command = new RegisterBlueprintCommandRes();
        command.setBlueprint(validBlueprintWithRepo(namePrefix));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<RegisterBlueprintResponseRes> response = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINT_REGISTER),
                new HttpEntity<>(command, headers),
                RegisterBlueprintResponseRes.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getBlueprint()).isNotNull();
        assertThat(response.getBody().getBlueprint().getUuid()).isNotNull();
        assertThat(response.getBody().getBlueprint().getName()).isEqualTo(namePrefix + "-bp");

        String blueprintUuid = response.getBody().getBlueprint().getUuid();

        ResponseEntity<BlueprintRes> getResponse = rest.getForEntity(
                apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid),
                BlueprintRes.class
        );
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().getName()).isEqualTo(namePrefix + "-bp");

        rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
    }

    /**
     * REG-BP-002 — Scenario: Invalid HTTP remote URL returns 400
     */
    @Test
    public void whenRegisterBlueprintWithInvalidHttpUrlThenReturn400() {
        String namePrefix = "whenRegisterBlueprintWithInvalidHttpUrlThenReturn400";

        BlueprintRes blueprint = validBlueprintWithRepo(namePrefix);
        blueprint.getBlueprintRepo().setRemoteUrlHttp("not-a-url");

        RegisterBlueprintCommandRes command = new RegisterBlueprintCommandRes();
        command.setBlueprint(blueprint);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINT_REGISTER),
                new HttpEntity<>(command, headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        RegisterBlueprintCommandRes retry = new RegisterBlueprintCommandRes();
        retry.setBlueprint(validBlueprintWithRepo(namePrefix));
        ResponseEntity<RegisterBlueprintResponseRes> ok = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINT_REGISTER),
                new HttpEntity<>(retry, headers),
                RegisterBlueprintResponseRes.class
        );
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + ok.getBody().getBlueprint().getUuid()));
    }

    /**
     * REG-BP-003 — Scenario: Invalid SSH remote URL returns 400
     */
    @Test
    public void whenRegisterBlueprintWithInvalidSshUrlThenReturn400() {
        String namePrefix = "whenRegisterBlueprintWithInvalidSshUrlThenReturn400";

        BlueprintRes blueprint = validBlueprintWithRepo(namePrefix);
        blueprint.getBlueprintRepo().setRemoteUrlSsh("https://wrong-scheme.example/repo.git");

        RegisterBlueprintCommandRes command = new RegisterBlueprintCommandRes();
        command.setBlueprint(blueprint);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINT_REGISTER),
                new HttpEntity<>(command, headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * REG-BP-004 — Scenario: Invalid provider base URL returns 400
     */
    @Test
    public void whenRegisterBlueprintWithInvalidProviderBaseUrlThenReturn400() {
        String namePrefix = "whenRegisterBlueprintWithInvalidProviderBaseUrlThenReturn400";

        BlueprintRes blueprint = validBlueprintWithRepo(namePrefix);
        blueprint.getBlueprintRepo().setProviderBaseUrl("ftp://not-allowed.example");

        RegisterBlueprintCommandRes command = new RegisterBlueprintCommandRes();
        command.setBlueprint(blueprint);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINT_REGISTER),
                new HttpEntity<>(command, headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * REG-BP-005 — Scenario: Path traversal in repository paths returns 400
     */
    @Test
    public void whenRegisterBlueprintWithPathTraversalThenReturn400() {
        String namePrefix = "whenRegisterBlueprintWithPathTraversalThenReturn400";

        BlueprintRes blueprint = validBlueprintWithRepo(namePrefix);
        blueprint.getBlueprintRepo().setManifestRootPath("/safe/../evil");

        RegisterBlueprintCommandRes command = new RegisterBlueprintCommandRes();
        command.setBlueprint(blueprint);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINT_REGISTER),
                new HttpEntity<>(command, headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private static BlueprintRes validBlueprintWithRepo(String namePrefix) {
        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName(namePrefix + "-bp");
        blueprint.setDisplayName(namePrefix + "-display");
        blueprint.setDescription(namePrefix + "-description");

        BlueprintRes.BlueprintRepoRes blueprintRepo = new BlueprintRes.BlueprintRepoRes();
        blueprintRepo.setExternalIdentifier("ext-id");
        blueprintRepo.setName("repo-name");
        blueprintRepo.setDescription("repo-desc");
        blueprintRepo.setManifestRootPath("/manifest");
        blueprintRepo.setDescriptorTemplatePath("/template");
        blueprintRepo.setReadmePath("/readme");
        blueprintRepo.setRemoteUrlHttp("https://github.com/org/repo.git");
        blueprintRepo.setRemoteUrlSsh("git@github.com:org/repo.git");
        blueprintRepo.setDefaultBranch("main");
        blueprintRepo.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
        blueprintRepo.setProviderBaseUrl("https://github.com");
        blueprintRepo.setOwnerId("org");
        blueprintRepo.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
        blueprint.setBlueprintRepo(blueprintRepo);
        return blueprint;
    }
}
