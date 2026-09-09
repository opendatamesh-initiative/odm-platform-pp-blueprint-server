package org.opendatamesh.platform.pp.blueprint.rest.v2.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.opendatamesh.platform.pp.blueprint.rest.v2.BlueprintApplicationIT;
import org.opendatamesh.platform.pp.blueprint.rest.v2.RoutesV2;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintRepoOwnerTypeRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintRepoProviderTypeRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.label.LabelRes;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

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

    /**
     * Feature: Create blueprint with existing labels
     * Given catalog labels exist
     * When the client sends POST with those label uuids
     * Then GET returns the assigned labels including name and color
     */
    @Test
    public void whenCreateBlueprintWithExistingLabelsThenGetReturnsLabelNameAndColor() {
        String namePrefix = "whenCreateBlueprintWithExistingLabelsThenGetReturnsLabelNameAndColor";
        LabelRes label = createLabel(namePrefix + "-label", "#0E8A16", "Status");

        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName(namePrefix + "-bp");
        blueprint.setDisplayName(namePrefix + "-display");
        blueprint.setDescription(namePrefix + "-description");
        blueprint.setLabels(List.of(labelStub(label.getUuid())));

        ResponseEntity<BlueprintRes> response = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                BlueprintRes.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        String blueprintUuid = response.getBody().getUuid();

        try {
            ResponseEntity<BlueprintRes> getResponse = rest.getForEntity(
                    apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid),
                    BlueprintRes.class
            );
            assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(getResponse.getBody().getLabels()).isNotNull();
            assertThat(getResponse.getBody().getLabels()).extracting(LabelRes::getName).containsExactly(label.getName());
            assertThat(getResponse.getBody().getLabels()).extracting(LabelRes::getColor).containsExactly("#0E8A16");
            assertThat(getResponse.getBody().getLabels()).extracting(LabelRes::getGroup).containsExactly("Status");
        } finally {
            rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
            rest.delete(apiUrl(RoutesV2.LABELS, "/" + label.getUuid()));
        }
    }

    /**
     * Feature: Update blueprint — replace assignment set
     * Given a blueprint assigned to one label
     * When the client sends PUT with a different label
     * Then GET returns only the new assignment
     */
    @Test
    public void whenUpdateBlueprintThenReplaceLabelAssignmentSet() {
        String namePrefix = "whenUpdateBlueprintThenReplaceLabelAssignmentSet";
        LabelRes firstLabel = createLabel(namePrefix + "-first", "#111111");
        LabelRes secondLabel = createLabel(namePrefix + "-second", "#222222");

        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName(namePrefix + "-bp");
        blueprint.setDisplayName(namePrefix + "-display");
        blueprint.setDescription(namePrefix + "-description");
        blueprint.setLabels(List.of(labelStub(firstLabel.getUuid())));

        ResponseEntity<BlueprintRes> created = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                BlueprintRes.class
        );
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String blueprintUuid = created.getBody().getUuid();

        try {
            BlueprintRes update = new BlueprintRes();
            update.setUuid(blueprintUuid);
            update.setName(namePrefix + "-bp");
            update.setDisplayName(namePrefix + "-display");
            update.setDescription(namePrefix + "-description");
            update.setLabels(List.of(labelStub(secondLabel.getUuid())));

            ResponseEntity<BlueprintRes> response = rest.exchange(
                    apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid),
                    HttpMethod.PUT,
                    new HttpEntity<>(update),
                    BlueprintRes.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getLabels()).extracting(LabelRes::getUuid).containsExactly(secondLabel.getUuid());

            ResponseEntity<BlueprintRes> getResponse = rest.getForEntity(
                    apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid),
                    BlueprintRes.class
            );
            assertThat(getResponse.getBody().getLabels()).extracting(LabelRes::getUuid).containsExactly(secondLabel.getUuid());
        } finally {
            rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
            rest.delete(apiUrl(RoutesV2.LABELS, "/" + firstLabel.getUuid()));
            rest.delete(apiUrl(RoutesV2.LABELS, "/" + secondLabel.getUuid()));
        }
    }

    /**
     * Feature: Create blueprint — unknown label uuid
     * When the client sends POST with a label uuid that does not exist
     * Then the response status is 400
     */
    @Test
    public void whenCreateBlueprintWithUnknownLabelUuidThenReturnBadRequest() {
        String namePrefix = "whenCreateBlueprintWithUnknownLabelUuidThenReturnBadRequest";

        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName(namePrefix + "-bp");
        blueprint.setDisplayName(namePrefix + "-display");
        blueprint.setDescription(namePrefix + "-description");
        blueprint.setLabels(List.of(labelStub(UUID.randomUUID().toString())));

        ResponseEntity<String> response = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * Feature: Create blueprint — duplicate label uuid
     * When the client sends POST with the same label uuid twice
     * Then the response status is 400
     */
    @Test
    public void whenCreateBlueprintWithDuplicateLabelUuidThenReturnBadRequest() {
        String namePrefix = "whenCreateBlueprintWithDuplicateLabelUuidThenReturnBadRequest";
        LabelRes label = createLabel(namePrefix + "-label", "#ABCDEF");

        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName(namePrefix + "-bp");
        blueprint.setDisplayName(namePrefix + "-display");
        blueprint.setDescription(namePrefix + "-description");
        blueprint.setLabels(List.of(labelStub(label.getUuid()), labelStub(label.getUuid())));

        ResponseEntity<String> response = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        rest.delete(apiUrl(RoutesV2.LABELS, "/" + label.getUuid()));
    }

    /**
     * Feature: Search blueprints — labelUuids match any
     * Given two blueprints and two labels
     * When filtering by one label uuid, only the assigned blueprint is returned
     * When filtering by both uuids, the union is returned
     * When composing with name, both filters apply
     */
    @Test
    public void whenSearchBlueprintsByLabelUuidsThenMatchAnyAndComposeWithName() {
        String namePrefix = "whenSearchBlueprintsByLabelUuidsThenMatchAnyAndComposeWithName";
        LabelRes labelA = createLabel(namePrefix + "-labelA", "#AAAAAA");
        LabelRes labelB = createLabel(namePrefix + "-labelB", "#BBBBBB");

        BlueprintRes blueprintA = new BlueprintRes();
        blueprintA.setName(namePrefix + "-bpA");
        blueprintA.setDisplayName(namePrefix + "-displayA");
        blueprintA.setDescription(namePrefix + "-descriptionA");
        blueprintA.setLabels(List.of(labelStub(labelA.getUuid())));

        BlueprintRes blueprintB = new BlueprintRes();
        blueprintB.setName(namePrefix + "-bpB");
        blueprintB.setDisplayName(namePrefix + "-displayB");
        blueprintB.setDescription(namePrefix + "-descriptionB");
        blueprintB.setLabels(List.of(labelStub(labelB.getUuid())));

        ResponseEntity<BlueprintRes> createdA = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprintA),
                BlueprintRes.class
        );
        ResponseEntity<BlueprintRes> createdB = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprintB),
                BlueprintRes.class
        );
        assertThat(createdA.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createdB.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String uuidA = createdA.getBody().getUuid();
        String uuidB = createdB.getBody().getUuid();

        try {
            ResponseEntity<JsonNode> filterOne = rest.getForEntity(
                    apiUrl(RoutesV2.BLUEPRINTS, "?labelUuids=" + labelA.getUuid()),
                    JsonNode.class
            );
            assertThat(filterOne.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode contentOne = filterOne.getBody().get("content");
            assertThat(contentOne).isNotNull();
            assertThat(contentOne.size()).isGreaterThanOrEqualTo(1);
            for (JsonNode item : contentOne) {
                assertThat(item.get("uuid").asText()).isNotEqualTo(uuidB);
            }
            assertThat(uuidsIn(contentOne)).contains(uuidA);

            ResponseEntity<JsonNode> filterBoth = rest.getForEntity(
                    apiUrl(RoutesV2.BLUEPRINTS, "?labelUuids=" + labelA.getUuid() + "&labelUuids=" + labelB.getUuid()),
                    JsonNode.class
            );
            assertThat(filterBoth.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(uuidsIn(filterBoth.getBody().get("content"))).contains(uuidA, uuidB);

            ResponseEntity<JsonNode> composeName = rest.getForEntity(
                    apiUrl(RoutesV2.BLUEPRINTS, "?name=" + blueprintA.getName() + "&labelUuids=" + labelA.getUuid()),
                    JsonNode.class
            );
            assertThat(composeName.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode composedContent = composeName.getBody().get("content");
            assertThat(composedContent.size()).isEqualTo(1);
            assertThat(composedContent.get(0).get("uuid").asText()).isEqualTo(uuidA);
        } finally {
            rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + uuidA));
            rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + uuidB));
            rest.delete(apiUrl(RoutesV2.LABELS, "/" + labelA.getUuid()));
            rest.delete(apiUrl(RoutesV2.LABELS, "/" + labelB.getUuid()));
        }
    }

    /**
     * Feature: Search blueprints — unlabeled still listed without label filter
     * Given an unlabeled blueprint
     * When the client searches by name with no labelUuids
     * Then the unlabeled blueprint is returned
     */
    @Test
    public void whenSearchBlueprintsWithoutLabelFilterThenUnlabeledBlueprintIsReturned() {
        String namePrefix = "whenSearchBlueprintsWithoutLabelFilterThenUnlabeledBlueprintIsReturned";

        BlueprintRes unlabeled = new BlueprintRes();
        unlabeled.setName(namePrefix + "-bp");
        unlabeled.setDisplayName(namePrefix + "-display");
        unlabeled.setDescription(namePrefix + "-description");

        ResponseEntity<BlueprintRes> created = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(unlabeled),
                BlueprintRes.class
        );
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String blueprintUuid = created.getBody().getUuid();

        try {
            ResponseEntity<JsonNode> response = rest.getForEntity(
                    apiUrl(RoutesV2.BLUEPRINTS, "?name=" + unlabeled.getName()),
                    JsonNode.class
            );
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(uuidsIn(response.getBody().get("content"))).contains(blueprintUuid);
        } finally {
            rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
        }
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

    private static List<String> uuidsIn(JsonNode content) {
        assertThat(content).isNotNull();
        assertThat(content.isArray()).isTrue();
        List<String> uuids = new java.util.ArrayList<>();
        for (JsonNode item : content) {
            uuids.add(item.get("uuid").asText());
        }
        return uuids;
    }
}
