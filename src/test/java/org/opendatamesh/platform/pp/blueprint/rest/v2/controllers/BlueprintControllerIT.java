package org.opendatamesh.platform.pp.blueprint.rest.v2.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.opendatamesh.platform.pp.blueprint.rest.v2.BlueprintApplicationIT;
import org.opendatamesh.platform.pp.blueprint.rest.v2.RoutesV2;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintRepoOwnerTypeRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintRepoProviderTypeRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintTypeRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintRes;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link org.opendatamesh.platform.pp.blueprint.rest.v2.controllers.BlueprintController}.
 * Scenarios trace to {@code spdd/analysis/GGQPA-XXX-202603261546-[Analysis]-blueprint-anemic-crud.md} (Gherkin).
 */
public class BlueprintControllerIT extends BlueprintApplicationIT {

    /**
     * Feature: Create blueprint
     * Given the API is available
     * And a valid blueprint payload is prepared
     * When the client sends POST to "/api/v2/pp/blueprint/blueprints" with that JSON body
     * Then the response status is 201
     * And the response body is a BlueprintRes reflecting the created resource
     * And a subsequent GET by the returned uuid returns the same logical data
     */
    @Test
    public void whenCreateBlueprintThenReturnCreatedBlueprint() {
        String namePrefix = "whenCreateBlueprintThenReturnCreatedBlueprint";

        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName(namePrefix + "-bp");
        blueprint.setDisplayName(namePrefix + "-display");
        blueprint.setDescription(namePrefix + "-description");

        
        blueprint.setBlueprintType(BlueprintTypeRes.BLUEPRINT);
        BlueprintRes.BlueprintRepoRes repoFixture1 = new BlueprintRes.BlueprintRepoRes();
        repoFixture1.setExternalIdentifier("ext-id");
        repoFixture1.setName("repo-name");
        repoFixture1.setDescription("repo-desc");
        repoFixture1.setManifestRootPath("/manifest");
        repoFixture1.setDescriptorTemplatePath("/template");
        repoFixture1.setReadmePath("/readme");
        repoFixture1.setRemoteUrlHttp("https://github.com/org/repo.git");
        repoFixture1.setRemoteUrlSsh("git@github.com:org/repo.git");
        repoFixture1.setDefaultBranch("main");
        repoFixture1.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
        repoFixture1.setProviderBaseUrl("https://github.com");
        repoFixture1.setOwnerId("org");
        repoFixture1.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
        blueprint.setBlueprintRepo(repoFixture1);
// When
        ResponseEntity<BlueprintRes> response = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                BlueprintRes.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getUuid()).isNotNull();
        assertThat(response.getBody().getName()).isEqualTo(blueprint.getName());
        assertThat(response.getBody().getDisplayName()).isEqualTo(blueprint.getDisplayName());
        assertThat(response.getBody().getDescription()).isEqualTo(blueprint.getDescription());

        String blueprintUuid = response.getBody().getUuid();

        ResponseEntity<BlueprintRes> getResponse = rest.getForEntity(
                apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid),
                BlueprintRes.class
        );
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().getName()).isEqualTo(blueprint.getName());

        // Cleanup
        rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
    }

    /**
     * Feature: Get blueprint by id
     * Given a blueprint exists with a known uuid
     * When the client sends GET to "/api/v2/pp/blueprint/blueprints/{uuid}"
     * Then the response status is 200
     * And the response body matches the stored blueprint
     */
    @Test
    public void whenGetBlueprintByIdThenReturnBlueprint() {
        String namePrefix = "whenGetBlueprintByIdThenReturnBlueprint";

        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName(namePrefix + "-bp");
        blueprint.setDisplayName(namePrefix + "-display");
        blueprint.setDescription(namePrefix + "-description");

        
        blueprint.setBlueprintType(BlueprintTypeRes.BLUEPRINT);
        BlueprintRes.BlueprintRepoRes repoFixture2 = new BlueprintRes.BlueprintRepoRes();
        repoFixture2.setExternalIdentifier("ext-id");
        repoFixture2.setName("repo-name");
        repoFixture2.setDescription("repo-desc");
        repoFixture2.setManifestRootPath("/manifest");
        repoFixture2.setDescriptorTemplatePath("/template");
        repoFixture2.setReadmePath("/readme");
        repoFixture2.setRemoteUrlHttp("https://github.com/org/repo.git");
        repoFixture2.setRemoteUrlSsh("git@github.com:org/repo.git");
        repoFixture2.setDefaultBranch("main");
        repoFixture2.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
        repoFixture2.setProviderBaseUrl("https://github.com");
        repoFixture2.setOwnerId("org");
        repoFixture2.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
        blueprint.setBlueprintRepo(repoFixture2);
ResponseEntity<BlueprintRes> blueprintResponse = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                BlueprintRes.class
        );
        assertThat(blueprintResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String blueprintUuid = blueprintResponse.getBody().getUuid();

        // When
        ResponseEntity<BlueprintRes> response = rest.getForEntity(
                apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid),
                BlueprintRes.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getUuid()).isEqualTo(blueprintUuid);
        assertThat(response.getBody().getName()).isEqualTo(blueprint.getName());

        // Cleanup
        rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
    }

    /**
     * Feature: Get blueprint by id — not found
     * Given no blueprint exists for uuid "non-existent-uuid"
     * When the client sends GET to "/api/v2/pp/blueprint/blueprints/non-existent-uuid"
     * Then the response status is 404
     */
    @Test
    public void whenGetBlueprintWithNonExistentIdThenReturnNotFound() {
        // When
        ResponseEntity<String> response = rest.getForEntity(
                apiUrl(RoutesV2.BLUEPRINTS, "/non-existent-uuid"),
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * Feature: Search blueprints — paginated list
     * Given one or more blueprints exist in the database
     * When the client sends GET to "/api/v2/pp/blueprint/blueprints" with default pagination
     * Then the response status is 200
     * And the body has a "content" array of BlueprintRes
     * And "totalElements" matches the number of matching blueprints
     * And results are ordered by createdAt descending by default
     */
    @Test
    public void whenSearchBlueprintsThenReturnBlueprintsList() {
        String namePrefix = "whenSearchBlueprintsThenReturnBlueprintsList";

        BlueprintRes firstBlueprint = new BlueprintRes();
        firstBlueprint.setName(namePrefix + "-first-bp");
        firstBlueprint.setDisplayName(namePrefix + "-first-display");
        firstBlueprint.setDescription(namePrefix + "-first-description");

        
        firstBlueprint.setBlueprintType(BlueprintTypeRes.BLUEPRINT);
        BlueprintRes.BlueprintRepoRes repoFixture3 = new BlueprintRes.BlueprintRepoRes();
        repoFixture3.setExternalIdentifier("ext-id");
        repoFixture3.setName("repo-name");
        repoFixture3.setDescription("repo-desc");
        repoFixture3.setManifestRootPath("/manifest");
        repoFixture3.setDescriptorTemplatePath("/template");
        repoFixture3.setReadmePath("/readme");
        repoFixture3.setRemoteUrlHttp("https://github.com/org/repo.git");
        repoFixture3.setRemoteUrlSsh("git@github.com:org/repo.git");
        repoFixture3.setDefaultBranch("main");
        repoFixture3.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
        repoFixture3.setProviderBaseUrl("https://github.com");
        repoFixture3.setOwnerId("org");
        repoFixture3.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
        firstBlueprint.setBlueprintRepo(repoFixture3);
ResponseEntity<BlueprintRes> firstBlueprintResponse = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(firstBlueprint),
                BlueprintRes.class
        );
        assertThat(firstBlueprintResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String firstBlueprintUuid = firstBlueprintResponse.getBody().getUuid();

        BlueprintRes secondBlueprint = new BlueprintRes();
        secondBlueprint.setName(namePrefix + "-second-bp");
        secondBlueprint.setDisplayName(namePrefix + "-second-display");
        secondBlueprint.setDescription(namePrefix + "-second-description");

        
        secondBlueprint.setBlueprintType(BlueprintTypeRes.BLUEPRINT);
        BlueprintRes.BlueprintRepoRes repoFixture4 = new BlueprintRes.BlueprintRepoRes();
        repoFixture4.setExternalIdentifier("ext-id");
        repoFixture4.setName("repo-name");
        repoFixture4.setDescription("repo-desc");
        repoFixture4.setManifestRootPath("/manifest");
        repoFixture4.setDescriptorTemplatePath("/template");
        repoFixture4.setReadmePath("/readme");
        repoFixture4.setRemoteUrlHttp("https://github.com/org/repo.git");
        repoFixture4.setRemoteUrlSsh("git@github.com:org/repo.git");
        repoFixture4.setDefaultBranch("main");
        repoFixture4.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
        repoFixture4.setProviderBaseUrl("https://github.com");
        repoFixture4.setOwnerId("org");
        repoFixture4.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
        secondBlueprint.setBlueprintRepo(repoFixture4);
ResponseEntity<BlueprintRes> secondBlueprintResponse = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(secondBlueprint),
                BlueprintRes.class
        );
        assertThat(secondBlueprintResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String secondBlueprintUuid = secondBlueprintResponse.getBody().getUuid();

        // When
        ResponseEntity<JsonNode> response = rest.getForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                JsonNode.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        JsonNode body = response.getBody();
        assertThat(body.has("content")).isTrue();
        assertThat(body.has("totalElements")).isTrue();
        assertThat(body.get("totalElements").asInt()).isGreaterThanOrEqualTo(2);

        // Cleanup
        rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + firstBlueprintUuid));
        rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + secondBlueprintUuid));
    }

    /**
     * Feature: Search blueprints — filters
     * Given blueprints exist with distinguishable field values (e.g. name or other supported filter fields)
     * When the client sends GET to "/api/v2/pp/blueprint/blueprints" including supported filter query parameters
     * Then the response status is 200
     * And every item in "content" satisfies the filter
     * And "totalElements" equals the count of matching rows
     */
    @Test
    public void whenSearchBlueprintsWithFiltersThenReturnFilteredResults() {
        String namePrefix = "whenSearchBlueprintsWithFiltersThenReturnFilteredResults";

        BlueprintRes filteredBlueprint = new BlueprintRes();
        filteredBlueprint.setName(namePrefix + "-filtered-bp");
        filteredBlueprint.setDisplayName(namePrefix + "-filtered-display");
        filteredBlueprint.setDescription(namePrefix + "-filtered-description");

        
        filteredBlueprint.setBlueprintType(BlueprintTypeRes.BLUEPRINT);
        BlueprintRes.BlueprintRepoRes repoFixture5 = new BlueprintRes.BlueprintRepoRes();
        repoFixture5.setExternalIdentifier("ext-id");
        repoFixture5.setName("repo-name");
        repoFixture5.setDescription("repo-desc");
        repoFixture5.setManifestRootPath("/manifest");
        repoFixture5.setDescriptorTemplatePath("/template");
        repoFixture5.setReadmePath("/readme");
        repoFixture5.setRemoteUrlHttp("https://github.com/org/repo.git");
        repoFixture5.setRemoteUrlSsh("git@github.com:org/repo.git");
        repoFixture5.setDefaultBranch("main");
        repoFixture5.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
        repoFixture5.setProviderBaseUrl("https://github.com");
        repoFixture5.setOwnerId("org");
        repoFixture5.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
        filteredBlueprint.setBlueprintRepo(repoFixture5);
ResponseEntity<BlueprintRes> filteredBlueprintResponse = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(filteredBlueprint),
                BlueprintRes.class
        );
        assertThat(filteredBlueprintResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String filteredBlueprintUuid = filteredBlueprintResponse.getBody().getUuid();

        BlueprintRes otherBlueprint = new BlueprintRes();
        otherBlueprint.setName(namePrefix + "-other-bp");
        otherBlueprint.setDisplayName(namePrefix + "-other-display");
        otherBlueprint.setDescription(namePrefix + "-other-description");

        
        otherBlueprint.setBlueprintType(BlueprintTypeRes.BLUEPRINT);
        BlueprintRes.BlueprintRepoRes repoFixture6 = new BlueprintRes.BlueprintRepoRes();
        repoFixture6.setExternalIdentifier("ext-id");
        repoFixture6.setName("repo-name");
        repoFixture6.setDescription("repo-desc");
        repoFixture6.setManifestRootPath("/manifest");
        repoFixture6.setDescriptorTemplatePath("/template");
        repoFixture6.setReadmePath("/readme");
        repoFixture6.setRemoteUrlHttp("https://github.com/org/repo.git");
        repoFixture6.setRemoteUrlSsh("git@github.com:org/repo.git");
        repoFixture6.setDefaultBranch("main");
        repoFixture6.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
        repoFixture6.setProviderBaseUrl("https://github.com");
        repoFixture6.setOwnerId("org");
        repoFixture6.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
        otherBlueprint.setBlueprintRepo(repoFixture6);
ResponseEntity<BlueprintRes> otherBlueprintResponse = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(otherBlueprint),
                BlueprintRes.class
        );
        assertThat(otherBlueprintResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String otherBlueprintUuid = otherBlueprintResponse.getBody().getUuid();

        // When — filter by name (bound via BlueprintSearchOptions when implemented)
        ResponseEntity<JsonNode> response = rest.getForEntity(
                apiUrl(RoutesV2.BLUEPRINTS, "?name=" + filteredBlueprint.getName()),
                JsonNode.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        JsonNode content = response.getBody().get("content");
        assertThat(content.isArray()).isTrue();
        for (JsonNode item : content) {
            assertThat(item.get("name").asText()).isEqualTo(filteredBlueprint.getName());
        }
        assertThat(response.getBody().get("totalElements").asInt()).isEqualTo(content.size());

        // Cleanup
        rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + filteredBlueprintUuid));
        rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + otherBlueprintUuid));
    }

    /**
     * Feature: Update blueprint
     * Given an existing blueprint with a known uuid
     * When the client sends PUT to "/api/v2/pp/blueprint/blueprints/{uuid}" with valid updated fields
     * Then the response status is 200
     * And the response body reflects the updated values
     * And GET by the same uuid returns the updated blueprint
     */
    @Test
    public void whenUpdateBlueprintThenReturnUpdatedBlueprint() {
        String namePrefix = "whenUpdateBlueprintThenReturnUpdatedBlueprint";

        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName(namePrefix + "-bp");
        blueprint.setDisplayName(namePrefix + "-display");
        blueprint.setDescription(namePrefix + "-description");

        
        blueprint.setBlueprintType(BlueprintTypeRes.BLUEPRINT);
        BlueprintRes.BlueprintRepoRes repoFixture7 = new BlueprintRes.BlueprintRepoRes();
        repoFixture7.setExternalIdentifier("ext-id");
        repoFixture7.setName("repo-name");
        repoFixture7.setDescription("repo-desc");
        repoFixture7.setManifestRootPath("/manifest");
        repoFixture7.setDescriptorTemplatePath("/template");
        repoFixture7.setReadmePath("/readme");
        repoFixture7.setRemoteUrlHttp("https://github.com/org/repo.git");
        repoFixture7.setRemoteUrlSsh("git@github.com:org/repo.git");
        repoFixture7.setDefaultBranch("main");
        repoFixture7.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
        repoFixture7.setProviderBaseUrl("https://github.com");
        repoFixture7.setOwnerId("org");
        repoFixture7.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
        blueprint.setBlueprintRepo(repoFixture7);
ResponseEntity<BlueprintRes> blueprintResponse = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                BlueprintRes.class
        );
        assertThat(blueprintResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String blueprintUuid = blueprintResponse.getBody().getUuid();

        BlueprintRes updateBlueprint = new BlueprintRes();
        updateBlueprint.setUuid(blueprintUuid);
        updateBlueprint.setName(namePrefix + "-bp");
        updateBlueprint.setDisplayName("Updated display");
        updateBlueprint.setDescription("Updated description");
        updateBlueprint.setBlueprintType(BlueprintTypeRes.BLUEPRINT);
        updateBlueprint.setBlueprintRepo(repoFixture7);

        // When
        ResponseEntity<BlueprintRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid),
                HttpMethod.PUT,
                new HttpEntity<>(updateBlueprint),
                BlueprintRes.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getUuid()).isEqualTo(blueprintUuid);
        assertThat(response.getBody().getDisplayName()).isEqualTo("Updated display");
        assertThat(response.getBody().getDescription()).isEqualTo("Updated description");

        ResponseEntity<BlueprintRes> getResponse = rest.getForEntity(
                apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid),
                BlueprintRes.class
        );
        assertThat(getResponse.getBody().getDisplayName()).isEqualTo("Updated display");

        // Cleanup
        rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
    }

    /**
     * Feature: Delete blueprint
     * Given an existing blueprint with a known uuid
     * When the client sends DELETE to "/api/v2/pp/blueprint/blueprints/{uuid}"
     * Then the response status is 204
     * And GET to the same path returns 404
     */
    @Test
    public void whenDeleteBlueprintThenReturnNoContentAndBlueprintIsDeleted() {
        String namePrefix = "whenDeleteBlueprintThenReturnNoContentAndBlueprintIsDeleted";

        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName(namePrefix + "-bp");
        blueprint.setDisplayName(namePrefix + "-display");
        blueprint.setDescription(namePrefix + "-description");

        
        blueprint.setBlueprintType(BlueprintTypeRes.BLUEPRINT);
        BlueprintRes.BlueprintRepoRes repoFixture8 = new BlueprintRes.BlueprintRepoRes();
        repoFixture8.setExternalIdentifier("ext-id");
        repoFixture8.setName("repo-name");
        repoFixture8.setDescription("repo-desc");
        repoFixture8.setManifestRootPath("/manifest");
        repoFixture8.setDescriptorTemplatePath("/template");
        repoFixture8.setReadmePath("/readme");
        repoFixture8.setRemoteUrlHttp("https://github.com/org/repo.git");
        repoFixture8.setRemoteUrlSsh("git@github.com:org/repo.git");
        repoFixture8.setDefaultBranch("main");
        repoFixture8.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
        repoFixture8.setProviderBaseUrl("https://github.com");
        repoFixture8.setOwnerId("org");
        repoFixture8.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
        blueprint.setBlueprintRepo(repoFixture8);
ResponseEntity<BlueprintRes> blueprintResponse = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                BlueprintRes.class
        );
        assertThat(blueprintResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String blueprintUuid = blueprintResponse.getBody().getUuid();

        // When
        ResponseEntity<Void> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid),
                HttpMethod.DELETE,
                null,
                Void.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        ResponseEntity<String> getResponse = rest.getForEntity(
                apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid),
                String.class
        );
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * Feature: Delete blueprint — not found
     * Given no blueprint exists for uuid "missing-uuid"
     * When the client sends DELETE to "/api/v2/pp/blueprint/blueprints/missing-uuid"
     * Then the response status is 404
     */
    @Test
    public void whenDeleteBlueprintWithMissingUuidThenReturnNotFound() {
        // When
        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINTS, "/missing-uuid"),
                HttpMethod.DELETE,
                null,
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * Feature: Create / update with invalid data — bad request
     * Given an invalid blueprint payload (violates validation rules agreed in service layer)
     * When the client sends POST to "/api/v2/pp/blueprint/blueprints" with that body
     * Then the response status is 400
     */
    @Test
    public void whenCreateBlueprintWithInvalidDataThenReturnBadRequest() {
        // Given — missing required fields
        BlueprintRes invalidBlueprint = new BlueprintRes();

        // When
        ResponseEntity<String> response = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(invalidBlueprint),
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * Feature: Update blueprint — duplicate name conflict
     * Given blueprint A and blueprint B exist with different names
     * When the client sends PUT on B's uuid with name equal to A's name (and duplicates are disallowed)
     * Then the response status is 409
     */
    @Test
    public void whenUpdateBlueprintWithDuplicateNameThenReturnConflict() {
        String namePrefix = "whenUpdateBlueprintWithDuplicateNameThenReturnConflict";

        BlueprintRes blueprintA = new BlueprintRes();
        blueprintA.setName(namePrefix + "-a-bp");
        blueprintA.setDisplayName(namePrefix + "-a-display");
        blueprintA.setDescription(namePrefix + "-a-description");

        
        blueprintA.setBlueprintType(BlueprintTypeRes.BLUEPRINT);
        BlueprintRes.BlueprintRepoRes repoFixture9 = new BlueprintRes.BlueprintRepoRes();
        repoFixture9.setExternalIdentifier("ext-id");
        repoFixture9.setName("repo-name");
        repoFixture9.setDescription("repo-desc");
        repoFixture9.setManifestRootPath("/manifest");
        repoFixture9.setDescriptorTemplatePath("/template");
        repoFixture9.setReadmePath("/readme");
        repoFixture9.setRemoteUrlHttp("https://github.com/org/repo.git");
        repoFixture9.setRemoteUrlSsh("git@github.com:org/repo.git");
        repoFixture9.setDefaultBranch("main");
        repoFixture9.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
        repoFixture9.setProviderBaseUrl("https://github.com");
        repoFixture9.setOwnerId("org");
        repoFixture9.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
        blueprintA.setBlueprintRepo(repoFixture9);
ResponseEntity<BlueprintRes> blueprintAResponse = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprintA),
                BlueprintRes.class
        );
        assertThat(blueprintAResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String blueprintUuidA = blueprintAResponse.getBody().getUuid();

        BlueprintRes blueprintB = new BlueprintRes();
        blueprintB.setName(namePrefix + "-b-bp");
        blueprintB.setDisplayName(namePrefix + "-b-display");
        blueprintB.setDescription(namePrefix + "-b-description");

        
        blueprintB.setBlueprintType(BlueprintTypeRes.BLUEPRINT);
        BlueprintRes.BlueprintRepoRes repoFixture10 = new BlueprintRes.BlueprintRepoRes();
        repoFixture10.setExternalIdentifier("ext-id");
        repoFixture10.setName("repo-name");
        repoFixture10.setDescription("repo-desc");
        repoFixture10.setManifestRootPath("/manifest");
        repoFixture10.setDescriptorTemplatePath("/template");
        repoFixture10.setReadmePath("/readme");
        repoFixture10.setRemoteUrlHttp("https://github.com/org/repo.git");
        repoFixture10.setRemoteUrlSsh("git@github.com:org/repo.git");
        repoFixture10.setDefaultBranch("main");
        repoFixture10.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
        repoFixture10.setProviderBaseUrl("https://github.com");
        repoFixture10.setOwnerId("org");
        repoFixture10.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
        blueprintB.setBlueprintRepo(repoFixture10);
ResponseEntity<BlueprintRes> blueprintBResponse = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprintB),
                BlueprintRes.class
        );
        assertThat(blueprintBResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String blueprintUuidB = blueprintBResponse.getBody().getUuid();

        BlueprintRes updateBlueprintB = new BlueprintRes();
        updateBlueprintB.setUuid(blueprintUuidB);
        updateBlueprintB.setName(blueprintA.getName());
        updateBlueprintB.setDisplayName(namePrefix + "-b-display");
        updateBlueprintB.setDescription(namePrefix + "-b-description");
        updateBlueprintB.setBlueprintType(BlueprintTypeRes.BLUEPRINT);
        updateBlueprintB.setBlueprintRepo(repoFixture10);

        // When
        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuidB),
                HttpMethod.PUT,
                new HttpEntity<>(updateBlueprintB),
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // Cleanup
        rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuidA));
        rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuidB));
    }

    /**
     * Feature: Create blueprint — duplicate name conflict
     * Given a blueprint already exists with name "existing-name"
     * When the client sends POST with the same logical name (per uniqueness rules)
     * Then the response status is 409
     */
    @Test
    public void whenCreateBlueprintWithDuplicateNameThenReturnConflict() {
        String namePrefix = "whenCreateBlueprintWithDuplicateNameThenReturnConflict";

        BlueprintRes firstBlueprint = new BlueprintRes();
        firstBlueprint.setName(namePrefix + "-first-bp");
        firstBlueprint.setDisplayName(namePrefix + "-first-display");
        firstBlueprint.setDescription(namePrefix + "-first-description");

        
        firstBlueprint.setBlueprintType(BlueprintTypeRes.BLUEPRINT);
        BlueprintRes.BlueprintRepoRes repoFixture11 = new BlueprintRes.BlueprintRepoRes();
        repoFixture11.setExternalIdentifier("ext-id");
        repoFixture11.setName("repo-name");
        repoFixture11.setDescription("repo-desc");
        repoFixture11.setManifestRootPath("/manifest");
        repoFixture11.setDescriptorTemplatePath("/template");
        repoFixture11.setReadmePath("/readme");
        repoFixture11.setRemoteUrlHttp("https://github.com/org/repo.git");
        repoFixture11.setRemoteUrlSsh("git@github.com:org/repo.git");
        repoFixture11.setDefaultBranch("main");
        repoFixture11.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
        repoFixture11.setProviderBaseUrl("https://github.com");
        repoFixture11.setOwnerId("org");
        repoFixture11.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
        firstBlueprint.setBlueprintRepo(repoFixture11);
ResponseEntity<BlueprintRes> firstBlueprintResponse = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(firstBlueprint),
                BlueprintRes.class
        );
        assertThat(firstBlueprintResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String firstBlueprintUuid = firstBlueprintResponse.getBody().getUuid();

        BlueprintRes secondBlueprint = new BlueprintRes();
        secondBlueprint.setName(firstBlueprint.getName());
        secondBlueprint.setDisplayName(namePrefix + "-second-display");
        secondBlueprint.setDescription(namePrefix + "-second-description");

        
        secondBlueprint.setBlueprintType(BlueprintTypeRes.BLUEPRINT);
        BlueprintRes.BlueprintRepoRes repoFixture12 = new BlueprintRes.BlueprintRepoRes();
        repoFixture12.setExternalIdentifier("ext-id");
        repoFixture12.setName("repo-name");
        repoFixture12.setDescription("repo-desc");
        repoFixture12.setManifestRootPath("/manifest");
        repoFixture12.setDescriptorTemplatePath("/template");
        repoFixture12.setReadmePath("/readme");
        repoFixture12.setRemoteUrlHttp("https://github.com/org/repo.git");
        repoFixture12.setRemoteUrlSsh("git@github.com:org/repo.git");
        repoFixture12.setDefaultBranch("main");
        repoFixture12.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
        repoFixture12.setProviderBaseUrl("https://github.com");
        repoFixture12.setOwnerId("org");
        repoFixture12.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
        secondBlueprint.setBlueprintRepo(repoFixture12);
// When
        ResponseEntity<String> response = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(secondBlueprint),
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // Cleanup
        rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + firstBlueprintUuid));
    }

    /**
     * Feature: Blueprint with nested repository — create
     * Given a valid blueprint payload including a complete nested blueprintRepo
     * When the client sends POST to "/api/v2/pp/blueprint/blueprints"
     * Then the response status is 201
     * And the response includes blueprintRepo with expected fields populated
     */
    @Test
    public void whenCreateBlueprintWithRepositoryThenReturnCreatedBlueprintWithRepository() {
        String namePrefix = "whenCreateBlueprintWithRepositoryThenReturnCreatedBlueprintWithRepository";

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

        // When
        ResponseEntity<BlueprintRes> response = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                BlueprintRes.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getBlueprintRepo()).isNotNull();
        assertThat(response.getBody().getBlueprintRepo().getRemoteUrlHttp()).contains("github.com");
        assertThat(response.getBody().getBlueprintRepo().getProviderType()).isEqualTo(BlueprintRepoProviderTypeRes.GITHUB);

        String blueprintUuid = response.getBody().getUuid();
        // Cleanup
        rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
    }

    /**
     * Feature: Blueprint with nested repository — read
     * Given a blueprint exists with an associated blueprintRepo
     * When the client sends GET to "/api/v2/pp/blueprint/blueprints/{uuid}"
     * Then the response status is 200
     * And blueprintRepo is present and matches stored data
     */
    @Test
    public void whenGetBlueprintWithRepositoryThenReturnBlueprintWithRepositoryDetails() {
        String namePrefix = "whenGetBlueprintWithRepositoryThenReturnBlueprintWithRepositoryDetails";

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

        ResponseEntity<BlueprintRes> blueprintResponse = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                BlueprintRes.class
        );
        assertThat(blueprintResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String blueprintUuid = blueprintResponse.getBody().getUuid();

        // When
        ResponseEntity<BlueprintRes> response = rest.getForEntity(
                apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid),
                BlueprintRes.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getBlueprintRepo()).isNotNull();
        assertThat(response.getBody().getBlueprintRepo().getName()).isEqualTo("repo-name");

        // Cleanup
        rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
    }

    /**
     * Feature: Blueprint with nested repository — update
     * Given a blueprint with blueprintRepo exists
     * When the client sends PUT with modified blueprintRepo fields
     * Then the response status is 200
     * And GET returns the updated repository data
     */
    @Test
    public void whenUpdateBlueprintRepositoryThenReturnUpdatedBlueprintWithModifiedRepository() {
        String namePrefix = "whenUpdateBlueprintRepositoryThenReturnUpdatedBlueprintWithModifiedRepository";

        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName(namePrefix + "-bp");
        blueprint.setDisplayName(namePrefix + "-display");
        blueprint.setDescription(namePrefix + "-description");
        blueprint.setBlueprintType(BlueprintTypeRes.BLUEPRINT);
BlueprintRes.BlueprintRepoRes createBlueprintRepo = new BlueprintRes.BlueprintRepoRes();
        createBlueprintRepo.setExternalIdentifier("ext-id");
        createBlueprintRepo.setName("repo-name");
        createBlueprintRepo.setDescription("repo-desc");
        createBlueprintRepo.setManifestRootPath("/manifest");
        createBlueprintRepo.setDescriptorTemplatePath("/template");
        createBlueprintRepo.setReadmePath("/readme");
        createBlueprintRepo.setRemoteUrlHttp("https://github.com/org/repo.git");
        createBlueprintRepo.setRemoteUrlSsh("git@github.com:org/repo.git");
        createBlueprintRepo.setDefaultBranch("main");
        createBlueprintRepo.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
        createBlueprintRepo.setProviderBaseUrl("https://github.com");
        createBlueprintRepo.setOwnerId("org");
        createBlueprintRepo.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
        blueprint.setBlueprintRepo(createBlueprintRepo);

        ResponseEntity<BlueprintRes> blueprintResponse = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                BlueprintRes.class
        );
        assertThat(blueprintResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String blueprintUuid = blueprintResponse.getBody().getUuid();

        BlueprintRes updateBlueprint = new BlueprintRes();
        updateBlueprint.setName(namePrefix + "-bp");
        updateBlueprint.setDisplayName(namePrefix + "-display");
        updateBlueprint.setDescription(namePrefix + "-description");
        
        updateBlueprint.setBlueprintType(BlueprintTypeRes.BLUEPRINT);
        BlueprintRes.BlueprintRepoRes repoFixture13 = new BlueprintRes.BlueprintRepoRes();
        repoFixture13.setExternalIdentifier("ext-id");
        repoFixture13.setName("repo-name");
        repoFixture13.setDescription("repo-desc");
        repoFixture13.setManifestRootPath("/manifest");
        repoFixture13.setDescriptorTemplatePath("/template");
        repoFixture13.setReadmePath("/readme");
        repoFixture13.setRemoteUrlHttp("https://github.com/org/repo.git");
        repoFixture13.setRemoteUrlSsh("git@github.com:org/repo.git");
        repoFixture13.setDefaultBranch("main");
        repoFixture13.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
        repoFixture13.setProviderBaseUrl("https://github.com");
        repoFixture13.setOwnerId("org");
        repoFixture13.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
        updateBlueprint.setBlueprintRepo(repoFixture13);
updateBlueprint.setUuid(blueprintUuid);

        BlueprintRes.BlueprintRepoRes updateBlueprintRepo = new BlueprintRes.BlueprintRepoRes();
        updateBlueprintRepo.setExternalIdentifier("ext-id");
        updateBlueprintRepo.setName("repo-name-updated");
        updateBlueprintRepo.setDescription("repo-desc");
        updateBlueprintRepo.setManifestRootPath("/manifest");
        updateBlueprintRepo.setDescriptorTemplatePath("/template");
        updateBlueprintRepo.setReadmePath("/readme");
        updateBlueprintRepo.setRemoteUrlHttp("https://github.com/org/repo.git");
        updateBlueprintRepo.setRemoteUrlSsh("git@github.com:org/repo.git");
        updateBlueprintRepo.setDefaultBranch("develop");
        updateBlueprintRepo.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
        updateBlueprintRepo.setProviderBaseUrl("https://github.com");
        updateBlueprintRepo.setOwnerId("org");
        updateBlueprintRepo.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
        updateBlueprint.setBlueprintRepo(updateBlueprintRepo);

        // When
        ResponseEntity<BlueprintRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid),
                HttpMethod.PUT,
                new HttpEntity<>(updateBlueprint),
                BlueprintRes.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getBlueprintRepo().getName()).isEqualTo("repo-name-updated");
        assertThat(response.getBody().getBlueprintRepo().getDefaultBranch()).isEqualTo("develop");

        ResponseEntity<BlueprintRes> getResponse = rest.getForEntity(
                apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid),
                BlueprintRes.class
        );
        assertThat(getResponse.getBody().getBlueprintRepo().getName()).isEqualTo("repo-name-updated");

        // Cleanup
        rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
    }

    /**
     * Given a blueprint with nested blueprintRepo exists
     * When the client sends DELETE to "/api/v2/pp/blueprint/blueprints/{uuid}"
     * Then the response status is 204
     * And GET for that blueprint returns 404
     * And the linked repository record no longer exists
     */
    @Test
    public void whenDeleteBlueprintWithRepositoryThenReturnNoContentAndBothAreDeleted() {
        String namePrefix = "whenDeleteBlueprintWithRepositoryThenReturnNoContentAndBothAreDeleted";

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

        ResponseEntity<BlueprintRes> blueprintResponse = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                BlueprintRes.class
        );
        assertThat(blueprintResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String blueprintUuid = blueprintResponse.getBody().getUuid();

        // When
        ResponseEntity<Void> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid),
                HttpMethod.DELETE,
                null,
                Void.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        ResponseEntity<String> getResponse = rest.getForEntity(
                apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid),
                String.class
        );
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * Given a blueprint payload with blueprintRepo missing required fields or invalid values per validation
     * When the client sends POST to "/api/v2/pp/blueprint/blueprints"
     * Then the response status is 400
     */
    @Test
    public void whenCreateBlueprintWithRepositoryWithInvalidDataThenReturnBadRequest() {
        String namePrefix = "whenCreateBlueprintWithRepositoryWithInvalidDataThenReturnBadRequest";

        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName(namePrefix + "-bp");
        blueprint.setDisplayName(namePrefix + "-display");
        blueprint.setDescription(namePrefix + "-description");
        blueprint.setBlueprintType(BlueprintTypeRes.BLUEPRINT);
BlueprintRes.BlueprintRepoRes incompleteBlueprintRepo = new BlueprintRes.BlueprintRepoRes();
        incompleteBlueprintRepo.setName("only-name");
        blueprint.setBlueprintRepo(incompleteBlueprintRepo);

        // When
        ResponseEntity<String> response = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * Feature: Nested repository — invalid provider type
     * Given a blueprint payload with blueprintRepo and an invalid provider type value
     * When the client sends POST to "/api/v2/pp/blueprint/blueprints"
     * Then the response status is 400
     */
    @Test
    public void whenCreateBlueprintWithRepositoryWithInvalidProviderTypeThenReturnBadRequest() {
        // Given — invalid enum value in JSON
        String json = """
                {
                  "name": "whenCreateBlueprintWithRepositoryWithInvalidProviderTypeThenReturnBadRequest",
                  "displayName": "d",
                  "description": "d",
                  "blueprintType": "BLUEPRINT",
                  "blueprintRepo": {
                    "externalIdentifier": "ext",
                    "name": "r",
                    "manifestRootPath": "/m",
                    "descriptorTemplatePath": "/t",
                    "remoteUrlHttp": "https://github.com/o/r.git",
                    "remoteUrlSsh": "git@github.com:o/r.git",
                    "defaultBranch": "main",
                    "providerType": "NOT_A_PROVIDER",
                    "providerBaseUrl": "https://github.com",
                    "ownerId": "o",
                    "ownerType": "ORGANIZATION"
                  }
                }
                """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // When
        ResponseEntity<String> response = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(json, headers),
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * Feature: Nested repository — invalid URLs
     * Given a blueprint payload with blueprintRepo and malformed or disallowed remote URLs
     * When the client sends POST to "/api/v2/pp/blueprint/blueprints"
     * Then the response status is 400
     */
    @Test
    public void whenCreateBlueprintWithRepositoryWithInvalidUrlsThenReturnBadRequest() {
        String namePrefix = "whenCreateBlueprintWithRepositoryWithInvalidUrlsThenReturnBadRequest";

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
        blueprintRepo.setRemoteUrlHttp(null);
        blueprintRepo.setRemoteUrlSsh(null);
        blueprintRepo.setDefaultBranch("main");
        blueprintRepo.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
        blueprintRepo.setProviderBaseUrl("https://github.com");
        blueprintRepo.setOwnerId("org");
        blueprintRepo.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
        blueprint.setBlueprintRepo(blueprintRepo);

        // When
        ResponseEntity<String> response = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private static BlueprintRes.BlueprintRepoRes validRepo(String descriptorTemplatePath) {
        BlueprintRes.BlueprintRepoRes blueprintRepo = new BlueprintRes.BlueprintRepoRes();
        blueprintRepo.setExternalIdentifier("ext-id");
        blueprintRepo.setName("repo-name");
        blueprintRepo.setDescription("repo-desc");
        blueprintRepo.setManifestRootPath("/manifest");
        blueprintRepo.setDescriptorTemplatePath(descriptorTemplatePath);
        blueprintRepo.setReadmePath("/readme");
        blueprintRepo.setRemoteUrlHttp("https://github.com/org/repo.git");
        blueprintRepo.setRemoteUrlSsh("git@github.com:org/repo.git");
        blueprintRepo.setDefaultBranch("main");
        blueprintRepo.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
        blueprintRepo.setProviderBaseUrl("https://github.com");
        blueprintRepo.setOwnerId("org");
        blueprintRepo.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
        return blueprintRepo;
    }

    /**
     * Scenario: Hidden CRUD create of a Blueprint requires blueprintType and descriptorTemplatePath
     * Given a valid repository payload with a non-blank descriptorTemplatePath
     * And blueprintType is BLUEPRINT
     * When the client POSTs to "/api/v2/pp/blueprint/blueprints"
     * Then the response status is 201
     * And GET by uuid returns blueprintType BLUEPRINT and the same descriptorTemplatePath
     */
    @Test
    public void whenCreateBlueprintWithKindBlueprintThenReturnCreatedBlueprint() {
        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName("kind-blueprint-create-bp");
        blueprint.setDisplayName("kind-blueprint-create-display");
        blueprint.setDescription("d");
        blueprint.setBlueprintType(BlueprintTypeRes.BLUEPRINT);
        blueprint.setBlueprintRepo(validRepo("/template"));

        ResponseEntity<BlueprintRes> response = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS), new HttpEntity<>(blueprint), BlueprintRes.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getBlueprintType()).isEqualTo(BlueprintTypeRes.BLUEPRINT);
        assertThat(response.getBody().getBlueprintRepo().getDescriptorTemplatePath()).isEqualTo("/template");

        ResponseEntity<BlueprintRes> get = rest.getForEntity(
                apiUrl(RoutesV2.BLUEPRINTS, "/" + response.getBody().getUuid()), BlueprintRes.class);
        assertThat(get.getBody().getBlueprintType()).isEqualTo(BlueprintTypeRes.BLUEPRINT);
        rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + response.getBody().getUuid()));
    }

    /**
     * Scenario: Hidden CRUD create without blueprintType returns 400
     * Given a valid blueprint payload with repository and descriptorTemplatePath
     * And blueprintType is omitted
     * When the client POSTs to "/api/v2/pp/blueprint/blueprints"
     * Then the response status is 400
     * And the message states that blueprint type is required
     */
    @Test
    public void whenCreateBlueprintWithoutKindThenReturnBadRequest() {
        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName("kind-missing-bp");
        blueprint.setDisplayName("d");
        blueprint.setDescription("d");
        blueprint.setBlueprintRepo(validRepo("/template"));

        ResponseEntity<String> response = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS), new HttpEntity<>(blueprint), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Blueprint type is required");
    }

    /**
     * Scenario: Hidden CRUD create of a Blueprint without descriptorTemplatePath returns 400
     * Given blueprintType is BLUEPRINT
     * And the repository has a blank descriptorTemplatePath
     * When the client POSTs to "/api/v2/pp/blueprint/blueprints"
     * Then the response status is 400
     * And the message states that descriptor template path is required for a Blueprint
     */
    @Test
    public void whenCreateBlueprintWithoutDescriptorTemplatePathThenReturnBadRequest() {
        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName("kind-bp-no-descriptor-bp");
        blueprint.setDisplayName("d");
        blueprint.setDescription("d");
        blueprint.setBlueprintType(BlueprintTypeRes.BLUEPRINT);
        blueprint.setBlueprintRepo(validRepo(null));

        ResponseEntity<String> response = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS), new HttpEntity<>(blueprint), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Descriptor template path is required for a Blueprint");
    }

    /**
     * Scenario: Hidden CRUD create of a Blueprint module forbids descriptorTemplatePath
     * Given blueprintType is MODULE
     * And the repository has a non-blank descriptorTemplatePath
     * When the client POSTs to "/api/v2/pp/blueprint/blueprints"
     * Then the response status is 400
     * And the message states that a Blueprint module must not have descriptorTemplatePath
     */
    @Test
    public void whenCreateBlueprintModuleWithDescriptorTemplatePathThenReturnBadRequest() {
        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName("kind-module-with-path-bp");
        blueprint.setDisplayName("d");
        blueprint.setDescription("d");
        blueprint.setBlueprintType(BlueprintTypeRes.MODULE);
        blueprint.setBlueprintRepo(validRepo("/template"));

        ResponseEntity<String> response = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS), new HttpEntity<>(blueprint), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("A Blueprint module must not have descriptorTemplatePath");
    }

    /**
     * Scenario: Hidden CRUD create of a Blueprint module with blank descriptorTemplatePath succeeds
     * Given blueprintType is MODULE
     * And a valid repository payload with blank descriptorTemplatePath
     * When the client POSTs to "/api/v2/pp/blueprint/blueprints"
     * Then the response status is 201
     * And GET by uuid returns blueprintType MODULE
     */
    @Test
    public void whenCreateBlueprintModuleThenReturnCreatedBlueprint() {
        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName("kind-module-create-bp");
        blueprint.setDisplayName("d");
        blueprint.setDescription("d");
        blueprint.setBlueprintType(BlueprintTypeRes.MODULE);
        blueprint.setBlueprintRepo(validRepo(null));

        ResponseEntity<BlueprintRes> response = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS), new HttpEntity<>(blueprint), BlueprintRes.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getBlueprintType()).isEqualTo(BlueprintTypeRes.MODULE);

        ResponseEntity<BlueprintRes> get = rest.getForEntity(
                apiUrl(RoutesV2.BLUEPRINTS, "/" + response.getBody().getUuid()), BlueprintRes.class);
        assertThat(get.getBody().getBlueprintType()).isEqualTo(BlueprintTypeRes.MODULE);
        rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + response.getBody().getUuid()));
    }

    /**
     * Scenario: Search filters by blueprintType
     * Given one BLUEPRINT and one MODULE exist
     * When the client GETs "/api/v2/pp/blueprint/blueprints" with blueprintType=MODULE
     * Then the response contains only the MODULE row
     */
    @Test
    public void whenSearchBlueprintsByKindThenReturnFilteredResults() {
        BlueprintRes root = new BlueprintRes();
        root.setName("kind-search-root-bp");
        root.setDisplayName("d");
        root.setDescription("d");
        root.setBlueprintType(BlueprintTypeRes.BLUEPRINT);
        root.setBlueprintRepo(validRepo("/template"));
        ResponseEntity<BlueprintRes> rootCreated = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS), new HttpEntity<>(root), BlueprintRes.class);

        BlueprintRes module = new BlueprintRes();
        module.setName("kind-search-module-bp");
        module.setDisplayName("d");
        module.setDescription("d");
        module.setBlueprintType(BlueprintTypeRes.MODULE);
        module.setBlueprintRepo(validRepo(null));
        ResponseEntity<BlueprintRes> moduleCreated = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS), new HttpEntity<>(module), BlueprintRes.class);

        ResponseEntity<JsonNode> response = rest.getForEntity(
                apiUrl(RoutesV2.BLUEPRINTS) + "?blueprintType=MODULE&size=100", JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode content = response.getBody().get("content");
        assertThat(content).isNotNull();
        for (JsonNode item : content) {
            assertThat(item.get("blueprintType").asText()).isEqualTo("MODULE");
        }
        boolean found = false;
        for (JsonNode item : content) {
            if ("kind-search-module-bp".equals(item.get("name").asText())) {
                found = true;
            }
            assertThat(item.get("name").asText()).isNotEqualTo("kind-search-root-bp");
        }
        assertThat(found).isTrue();

        rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + rootCreated.getBody().getUuid()));
        rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + moduleCreated.getBody().getUuid()));
    }

    /**
     * Scenario: Hidden CRUD overwrite can change blueprintType when path matches the new blueprintType
     * Given a MODULE exists with blank descriptorTemplatePath
     * When the client PUTs the blueprint with blueprintType BLUEPRINT and a non-blank descriptorTemplatePath
     * Then the response status is 200
     * And GET returns blueprintType BLUEPRINT and that path
     */
    @Test
    public void whenOverwriteBlueprintTypeWithMatchingPathThenReturnUpdatedBlueprintType() {
        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName("kind-overwrite-ok-bp");
        blueprint.setDisplayName("d");
        blueprint.setDescription("d");
        blueprint.setBlueprintType(BlueprintTypeRes.MODULE);
        blueprint.setBlueprintRepo(validRepo(null));
        ResponseEntity<BlueprintRes> created = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS), new HttpEntity<>(blueprint), BlueprintRes.class);
        String uuid = created.getBody().getUuid();

        BlueprintRes update = new BlueprintRes();
        update.setName("kind-overwrite-ok-bp");
        update.setDisplayName("d");
        update.setDescription("d");
        update.setBlueprintType(BlueprintTypeRes.BLUEPRINT);
        update.setBlueprintRepo(validRepo("/new-template"));

        ResponseEntity<BlueprintRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINTS, "/" + uuid),
                HttpMethod.PUT,
                new HttpEntity<>(update),
                BlueprintRes.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getBlueprintType()).isEqualTo(BlueprintTypeRes.BLUEPRINT);
        assertThat(response.getBody().getBlueprintRepo().getDescriptorTemplatePath()).isEqualTo("/new-template");
        rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + uuid));
    }

    /**
     * Scenario: Hidden CRUD overwrite that changes blueprintType without a matching path returns 400
     * Given a MODULE exists
     * When the client PUTs blueprintType BLUEPRINT but leaves descriptorTemplatePath blank
     * Then the response status is 400
     * And blueprintType on GET is still MODULE
     */
    @Test
    public void whenOverwriteBlueprintTypeWithMismatchedPathThenReturnBadRequest() {
        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName("kind-overwrite-bad-bp");
        blueprint.setDisplayName("d");
        blueprint.setDescription("d");
        blueprint.setBlueprintType(BlueprintTypeRes.MODULE);
        blueprint.setBlueprintRepo(validRepo(null));
        ResponseEntity<BlueprintRes> created = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS), new HttpEntity<>(blueprint), BlueprintRes.class);
        String uuid = created.getBody().getUuid();

        BlueprintRes update = new BlueprintRes();
        update.setName("kind-overwrite-bad-bp");
        update.setDisplayName("d");
        update.setDescription("d");
        update.setBlueprintType(BlueprintTypeRes.BLUEPRINT);
        update.setBlueprintRepo(validRepo(null));

        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINTS, "/" + uuid),
                HttpMethod.PUT,
                new HttpEntity<>(update),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<BlueprintRes> get = rest.getForEntity(
                apiUrl(RoutesV2.BLUEPRINTS, "/" + uuid), BlueprintRes.class);
        assertThat(get.getBody().getBlueprintType()).isEqualTo(BlueprintTypeRes.MODULE);
        rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + uuid));
    }

    /**
     * Scenario: Hidden CRUD overwrite that omits blueprintType keeps the stored blueprintType
     * Given a MODULE exists
     * When the client PUTs an otherwise valid body without blueprintType
     * Then the response status is 200
     * And GET still returns blueprintType MODULE
     */
    @Test
    public void whenOverwriteBlueprintOmittingBlueprintTypeThenKeepStoredBlueprintType() {
        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName("kind-overwrite-omit-bp");
        blueprint.setDisplayName("d");
        blueprint.setDescription("d");
        blueprint.setBlueprintType(BlueprintTypeRes.MODULE);
        blueprint.setBlueprintRepo(validRepo(null));
        ResponseEntity<BlueprintRes> created = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS), new HttpEntity<>(blueprint), BlueprintRes.class);
        String uuid = created.getBody().getUuid();

        BlueprintRes update = new BlueprintRes();
        update.setName("kind-overwrite-omit-bp");
        update.setDisplayName("updated");
        update.setDescription("d");
        update.setBlueprintRepo(validRepo(null));

        ResponseEntity<BlueprintRes> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINTS, "/" + uuid),
                HttpMethod.PUT,
                new HttpEntity<>(update),
                BlueprintRes.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getBlueprintType()).isEqualTo(BlueprintTypeRes.MODULE);
        assertThat(response.getBody().getDisplayName()).isEqualTo("updated");
        rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + uuid));
    }
}
