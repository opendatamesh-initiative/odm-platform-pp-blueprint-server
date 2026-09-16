package org.opendatamesh.platform.pp.blueprint.rest.v2.controllers;

import org.junit.jupiter.api.Test;
import org.opendatamesh.platform.pp.blueprint.rest.v2.BlueprintApplicationIT;
import org.opendatamesh.platform.pp.blueprint.rest.v2.RoutesV2;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintRepoOwnerTypeRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintRepoProviderTypeRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintTypeRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.usecases.register.RegisterBlueprintCommandRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.usecases.register.RegisterBlueprintResponseRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.usecases.updatedocumentationfields.BlueprintUpdateDocumentationFieldsCommandRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.usecases.updatedocumentationfields.UpdateBlueprintDocumentationFieldsResponseRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.label.LabelRes;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link BlueprintUseCaseController}.
 * Scenarios trace to {@code spdd/analysis/GGQPA-XXX-202603261636-[Analysis]-blueprint-use-case-register.md} (Gherkin).
 */
public class BlueprintUseCaseControllerIT extends BlueprintApplicationIT {

    /**
     * REG-BP-001 — Scenario: Successful registration returns 201 and created blueprint
     * (Spec — Feature: Register blueprint via public use-case endpoint)
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

    /**
     * Updates documentation fields only — blueprintRepo must remain unchanged (Spec section).
     */
    @Test
    public void whenUpdateDocumentationFieldsWithoutRepoThenRepoUnchanged() {
        String namePrefix = "whenUpdateDocumentationFieldsWithoutRepoThenRepoUnchanged";

        RegisterBlueprintCommandRes register = new RegisterBlueprintCommandRes();
        register.setBlueprint(validBlueprintWithRepo(namePrefix));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<RegisterBlueprintResponseRes> registered = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINT_REGISTER),
                new HttpEntity<>(register, headers),
                RegisterBlueprintResponseRes.class
        );
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String blueprintUuid = registered.getBody().getBlueprint().getUuid();

        try {
            ResponseEntity<BlueprintRes> beforeEntity = rest.getForEntity(
                    apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid),
                    BlueprintRes.class
            );
            assertThat(beforeEntity.getBody()).isNotNull();
            BlueprintRes before = beforeEntity.getBody();
            assertThat(before.getBlueprintRepo()).isNotNull();

            BlueprintUpdateDocumentationFieldsCommandRes update = new BlueprintUpdateDocumentationFieldsCommandRes();
            update.setUuid(blueprintUuid);
            update.setDisplayName("new-display-" + namePrefix);
            update.setDescription("new-description-" + namePrefix);

            ResponseEntity<UpdateBlueprintDocumentationFieldsResponseRes> post = rest.postForEntity(
                    apiUrl(RoutesV2.BLUEPRINTS,  "/update-documentation-fields"),
                    new HttpEntity<>(update, headers),
                    UpdateBlueprintDocumentationFieldsResponseRes.class
            );

            assertThat(post.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(post.getBody()).isNotNull();
            assertThat(post.getBody().getBlueprint()).isNotNull();
            assertThat(post.getBody().getBlueprint().getDisplayName()).isEqualTo("new-display-" + namePrefix);
            assertThat(post.getBody().getBlueprint().getDescription()).isEqualTo("new-description-" + namePrefix);

            ResponseEntity<BlueprintRes> afterGet = rest.getForEntity(
                    apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid),
                    BlueprintRes.class
            );
            assertThat(afterGet.getBody()).isNotNull();
            assertThat(afterGet.getBody().getName()).isEqualTo(before.getName());
            assertThat(afterGet.getBody().getUuid()).isEqualTo(before.getUuid());
            assertThat(afterGet.getBody().getDisplayName()).isEqualTo("new-display-" + namePrefix);
            assertThat(afterGet.getBody().getDescription()).isEqualTo("new-description-" + namePrefix);
            assertThat(afterGet.getBody().getBlueprintRepo())
                    .usingRecursiveComparison()
                    .isEqualTo(before.getBlueprintRepo());
        } finally {
            rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
        }
    }

    /**
     * Updates documentation fields and full repository configuration (Spec section).
     */
    @Test
    public void whenUpdateDocumentationFieldsWithRepoThenRepoAndBlueprintUpdated() {
        String namePrefix = "whenUpdateDocumentationFieldsWithRepoThenRepoAndBlueprintUpdated";

        RegisterBlueprintCommandRes register = new RegisterBlueprintCommandRes();
        register.setBlueprint(validBlueprintWithRepo(namePrefix));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<RegisterBlueprintResponseRes> registered = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINT_REGISTER),
                new HttpEntity<>(register, headers),
                RegisterBlueprintResponseRes.class
        );
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String blueprintUuid = registered.getBody().getBlueprint().getUuid();

        try {
            ResponseEntity<BlueprintRes> beforeEntity = rest.getForEntity(
                    apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid),
                    BlueprintRes.class
            );
            BlueprintRes before = beforeEntity.getBody();
            assertThat(before).isNotNull();

            BlueprintUpdateDocumentationFieldsCommandRes update = new BlueprintUpdateDocumentationFieldsCommandRes();
            update.setUuid(blueprintUuid);
            update.setDisplayName("repo-upd-display-" + namePrefix);
            update.setDescription("repo-upd-desc-" + namePrefix);

            BlueprintUpdateDocumentationFieldsCommandRes.BlueprintRepo repo =
                    new BlueprintUpdateDocumentationFieldsCommandRes.BlueprintRepo();
            repo.setExternalIdentifier("ext-id-new");
            repo.setName("repo-name-new");
            repo.setDescription("repo-desc-new");
            repo.setManifestRootPath("/manifest-new");
            repo.setDescriptorTemplatePath("/template-new");
            repo.setReadmePath("/readme-new");
            repo.setRemoteUrlHttp("https://github.com/other-org/other-repo.git");
            repo.setRemoteUrlSsh("git@github.com:other-org/other-repo.git");
            repo.setDefaultBranch("develop");
            repo.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
            repo.setProviderBaseUrl("https://github.com");
            repo.setOwnerId("other-org");
            repo.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
            update.setBlueprintRepo(repo);

            ResponseEntity<UpdateBlueprintDocumentationFieldsResponseRes> post = rest.postForEntity(
                    apiUrl(RoutesV2.BLUEPRINTS,  "/update-documentation-fields"),
                    new HttpEntity<>(update, headers),
                    UpdateBlueprintDocumentationFieldsResponseRes.class
            );

            assertThat(post.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(post.getBody()).isNotNull();
            assertThat(post.getBody().getBlueprint()).isNotNull();
            assertThat(post.getBody().getBlueprint().getName()).isEqualTo(before.getName());
            assertThat(post.getBody().getBlueprint().getUuid()).isEqualTo(before.getUuid());
            assertThat(post.getBody().getBlueprint().getDisplayName()).isEqualTo("repo-upd-display-" + namePrefix);
            assertThat(post.getBody().getBlueprint().getDescription()).isEqualTo("repo-upd-desc-" + namePrefix);

            BlueprintRes.BlueprintRepoRes expected = new BlueprintRes.BlueprintRepoRes();
            expected.setExternalIdentifier("ext-id-new");
            expected.setName("repo-name-new");
            expected.setDescription("repo-desc-new");
            expected.setManifestRootPath("/manifest-new");
            expected.setDescriptorTemplatePath("/template-new");
            expected.setReadmePath("/readme-new");
            expected.setRemoteUrlHttp("https://github.com/other-org/other-repo.git");
            expected.setRemoteUrlSsh("git@github.com:other-org/other-repo.git");
            expected.setDefaultBranch("develop");
            expected.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
            expected.setProviderBaseUrl("https://github.com");
            expected.setOwnerId("other-org");
            expected.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
            expected.setBlueprintUuid(blueprintUuid);

            ResponseEntity<BlueprintRes> afterGet = rest.getForEntity(
                    apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid),
                    BlueprintRes.class
            );
            assertThat(afterGet.getBody()).isNotNull();
            assertThat(afterGet.getBody().getBlueprintRepo())
                    .usingRecursiveComparison()
                    .ignoringFields("uuid") // ignore uuid field because when updating the blueprintRepo, the uuid is generated as new one
                    .isEqualTo(expected);
        } finally {
            rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
        }
    }

    /**
     * Scenario: Register requires blueprintType
     * Given a register command whose nested blueprint omits blueprintType
     * When the client POSTs to the register use-case endpoint
     * Then the response status is 400
     */
    @Test
    public void whenRegisterBlueprintWithoutKindThenReturnBadRequest() {
        BlueprintRes blueprint = validBlueprintWithRepo("reg-no-kind");
        blueprint.setBlueprintType(null);

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
        assertThat(response.getBody()).contains("Blueprint type is required");
    }

    /**
     * Scenario: Public update-documentation-fields does not change blueprintType
     * Given a MODULE exists
     * When the client POSTs update-documentation-fields with a new displayName and a complete repo without descriptorTemplatePath
     * Then the response status is 200
     * And GET still returns blueprintType MODULE
     */
    @Test
    public void whenUpdateDocumentationFieldsThenKindUnchanged() {
        BlueprintRes blueprint = validBlueprintWithRepo("doc-kind-unchanged");
        blueprint.setBlueprintType(BlueprintTypeRes.MODULE);
        blueprint.getBlueprintRepo().setDescriptorTemplatePath(null);

        RegisterBlueprintCommandRes register = new RegisterBlueprintCommandRes();
        register.setBlueprint(blueprint);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<RegisterBlueprintResponseRes> registered = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINT_REGISTER),
                new HttpEntity<>(register, headers),
                RegisterBlueprintResponseRes.class
        );
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String blueprintUuid = registered.getBody().getBlueprint().getUuid();

        try {
            BlueprintUpdateDocumentationFieldsCommandRes update = new BlueprintUpdateDocumentationFieldsCommandRes();
            update.setUuid(blueprintUuid);
            update.setDisplayName("new-display");
            update.setDescription("new-description");
            BlueprintUpdateDocumentationFieldsCommandRes.BlueprintRepo repo =
                    new BlueprintUpdateDocumentationFieldsCommandRes.BlueprintRepo();
            repo.setExternalIdentifier("ext-id");
            repo.setName("repo-name");
            repo.setDescription("repo-desc");
            repo.setManifestRootPath("/manifest");
            repo.setDescriptorTemplatePath(null);
            repo.setReadmePath("/readme");
            repo.setRemoteUrlHttp("https://github.com/org/repo.git");
            repo.setRemoteUrlSsh("git@github.com:org/repo.git");
            repo.setDefaultBranch("main");
            repo.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
            repo.setProviderBaseUrl("https://github.com");
            repo.setOwnerId("org");
            repo.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
            update.setBlueprintRepo(repo);

            ResponseEntity<UpdateBlueprintDocumentationFieldsResponseRes> post = rest.postForEntity(
                    apiUrl(RoutesV2.BLUEPRINTS, "/update-documentation-fields"),
                    new HttpEntity<>(update, headers),
                    UpdateBlueprintDocumentationFieldsResponseRes.class
            );
            assertThat(post.getStatusCode()).isEqualTo(HttpStatus.OK);

            ResponseEntity<BlueprintRes> afterGet = rest.getForEntity(
                    apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid),
                    BlueprintRes.class
            );
            assertThat(afterGet.getBody().getBlueprintType()).isEqualTo(BlueprintTypeRes.MODULE);
            assertThat(afterGet.getBody().getDisplayName()).isEqualTo("new-display");
        } finally {
            rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
        }
    }

    private static BlueprintRes validBlueprintWithRepo(String namePrefix) {
        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName(namePrefix + "-bp");
        blueprint.setDisplayName(namePrefix + "-display");
        blueprint.setDescription(namePrefix + "-description");
        blueprint.setBlueprintType(BlueprintTypeRes.BLUEPRINT);

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

    /**
     * Feature: Register blueprint with labels
     * Given catalog labels exist
     * When the client registers a blueprint that references those labels
     * Then the response is 201 and GET shows the assignments
     */
    @Test
    public void whenRegisterBlueprintWithLabelsThenReturn201AndGetShowsThem() {
        String namePrefix = "whenRegisterBlueprintWithLabelsThenReturn201AndGetShowsThem";
        LabelRes label = createLabel(namePrefix + "-label", "#0E8A16", "Status");

        BlueprintRes blueprint = validBlueprintWithRepo(namePrefix);
        blueprint.setLabels(List.of(labelStub(label.getUuid())));

        RegisterBlueprintCommandRes command = new RegisterBlueprintCommandRes();
        command.setBlueprint(blueprint);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<RegisterBlueprintResponseRes> response = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINT_REGISTER),
                new HttpEntity<>(command, headers),
                RegisterBlueprintResponseRes.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        String blueprintUuid = response.getBody().getBlueprint().getUuid();

        try {
            ResponseEntity<BlueprintRes> getResponse = rest.getForEntity(
                    apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid),
                    BlueprintRes.class
            );
            assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(getResponse.getBody().getLabels()).extracting(LabelRes::getUuid).containsExactly(label.getUuid());
            assertThat(getResponse.getBody().getLabels()).extracting(LabelRes::getName).containsExactly(label.getName());
            assertThat(getResponse.getBody().getLabels()).extracting(LabelRes::getGroup).containsExactly("Status");
        } finally {
            rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
            rest.delete(apiUrl(RoutesV2.LABELS, "/" + label.getUuid()));
        }
    }

    /**
     * Feature: Update documentation fields — omit labels preserves assignments
     */
    @Test
    public void whenUpdateDocumentationFieldsOmitLabelsThenAssignmentsUnchanged() {
        String namePrefix = "whenUpdateDocumentationFieldsOmitLabelsThenAssignmentsUnchanged";
        LabelRes label = createLabel(namePrefix + "-label", "#111111");
        String blueprintUuid = registerBlueprintWithLabel(namePrefix, label.getUuid());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            BlueprintUpdateDocumentationFieldsCommandRes update = new BlueprintUpdateDocumentationFieldsCommandRes();
            update.setUuid(blueprintUuid);
            update.setDisplayName("new-display-" + namePrefix);
            update.setDescription("new-description-" + namePrefix);

            ResponseEntity<UpdateBlueprintDocumentationFieldsResponseRes> post = rest.postForEntity(
                    apiUrl(RoutesV2.BLUEPRINTS, "/update-documentation-fields"),
                    new HttpEntity<>(update, headers),
                    UpdateBlueprintDocumentationFieldsResponseRes.class
            );

            assertThat(post.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(post.getBody().getBlueprint().getLabels())
                    .extracting(LabelRes::getUuid)
                    .containsExactly(label.getUuid());

            ResponseEntity<BlueprintRes> afterGet = rest.getForEntity(
                    apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid),
                    BlueprintRes.class
            );
            assertThat(afterGet.getBody().getLabels()).extracting(LabelRes::getUuid).containsExactly(label.getUuid());
        } finally {
            rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
            rest.delete(apiUrl(RoutesV2.LABELS, "/" + label.getUuid()));
        }
    }

    /**
     * Feature: Update documentation fields — present list replaces assignments
     */
    @Test
    public void whenUpdateDocumentationFieldsWithLabelsThenAssignmentsReplaced() {
        String namePrefix = "whenUpdateDocumentationFieldsWithLabelsThenAssignmentsReplaced";
        LabelRes firstLabel = createLabel(namePrefix + "-first", "#111111");
        LabelRes secondLabel = createLabel(namePrefix + "-second", "#222222");
        String blueprintUuid = registerBlueprintWithLabel(namePrefix, firstLabel.getUuid());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            BlueprintUpdateDocumentationFieldsCommandRes update = new BlueprintUpdateDocumentationFieldsCommandRes();
            update.setUuid(blueprintUuid);
            update.setDisplayName(namePrefix + "-display");
            update.setDescription(namePrefix + "-description");
            update.setLabels(List.of(labelStub(secondLabel.getUuid())));

            ResponseEntity<UpdateBlueprintDocumentationFieldsResponseRes> post = rest.postForEntity(
                    apiUrl(RoutesV2.BLUEPRINTS, "/update-documentation-fields"),
                    new HttpEntity<>(update, headers),
                    UpdateBlueprintDocumentationFieldsResponseRes.class
            );

            assertThat(post.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(post.getBody().getBlueprint().getLabels())
                    .extracting(LabelRes::getUuid)
                    .containsExactly(secondLabel.getUuid());
        } finally {
            rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
            rest.delete(apiUrl(RoutesV2.LABELS, "/" + firstLabel.getUuid()));
            rest.delete(apiUrl(RoutesV2.LABELS, "/" + secondLabel.getUuid()));
        }
    }

    /**
     * Feature: Update documentation fields — empty list clears assignments
     */
    @Test
    public void whenUpdateDocumentationFieldsWithEmptyLabelsThenAssignmentsCleared() {
        String namePrefix = "whenUpdateDocumentationFieldsWithEmptyLabelsThenAssignmentsCleared";
        LabelRes label = createLabel(namePrefix + "-label", "#333333");
        String blueprintUuid = registerBlueprintWithLabel(namePrefix, label.getUuid());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            BlueprintUpdateDocumentationFieldsCommandRes update = new BlueprintUpdateDocumentationFieldsCommandRes();
            update.setUuid(blueprintUuid);
            update.setDisplayName(namePrefix + "-display");
            update.setDescription(namePrefix + "-description");
            update.setLabels(List.of());

            ResponseEntity<UpdateBlueprintDocumentationFieldsResponseRes> post = rest.postForEntity(
                    apiUrl(RoutesV2.BLUEPRINTS, "/update-documentation-fields"),
                    new HttpEntity<>(update, headers),
                    UpdateBlueprintDocumentationFieldsResponseRes.class
            );

            assertThat(post.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(post.getBody().getBlueprint().getLabels()).isEmpty();

            ResponseEntity<BlueprintRes> afterGet = rest.getForEntity(
                    apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid),
                    BlueprintRes.class
            );
            assertThat(afterGet.getBody().getLabels()).isEmpty();
        } finally {
            rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
            rest.delete(apiUrl(RoutesV2.LABELS, "/" + label.getUuid()));
        }
    }

    private String registerBlueprintWithLabel(String namePrefix, String labelUuid) {
        BlueprintRes blueprint = validBlueprintWithRepo(namePrefix);
        blueprint.setLabels(List.of(labelStub(labelUuid)));

        RegisterBlueprintCommandRes command = new RegisterBlueprintCommandRes();
        command.setBlueprint(blueprint);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<RegisterBlueprintResponseRes> registered = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINT_REGISTER),
                new HttpEntity<>(command, headers),
                RegisterBlueprintResponseRes.class
        );
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return registered.getBody().getBlueprint().getUuid();
    }

    private LabelRes createLabel(String name, String color) {
        return createLabel(name, color, null);
    }

    private LabelRes createLabel(String name, String color, String group) {
        LabelRes label = new LabelRes();
        label.setName(name);
        label.setDescription(name + "-desc");
        label.setColor(color);
        label.setGroup(group);
        ResponseEntity<LabelRes> response = rest.postForEntity(
                apiUrl(RoutesV2.LABELS),
                new HttpEntity<>(label),
                LabelRes.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private static LabelRes labelStub(String uuid) {
        LabelRes stub = new LabelRes();
        stub.setUuid(uuid);
        return stub;
    }
}
