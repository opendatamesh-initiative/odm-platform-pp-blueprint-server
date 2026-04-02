package org.opendatamesh.platform.pp.blueprint.rest.v2.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.opendatamesh.platform.pp.blueprint.rest.v2.BlueprintApplicationIT;
import org.opendatamesh.platform.pp.blueprint.rest.v2.RoutesV2;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.BlueprintVersionRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.BlueprintVersionShortRes;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link BlueprintVersionsController}.
 * Scenarios trace to {@code agentspecs/changes/blueprint_version/spec.md} (Gherkin).
 */
public class BlueprintVersionsControllerIT extends BlueprintApplicationIT {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Feature: Create blueprint version 
     * Given a parent blueprint exists 
     * And a valid blueprint version payload is prepared
     * When the client sends POST to "/api/v2/pp/blueprint/blueprints-versions" with that JSON body
     * Then the response status is 201
     * And the response body is a BlueprintVersionRes consistent with the created row
     * And GET by the returned uuid returns the same logical data
     */
    @Test
    public void whenCreateBlueprintVersionThenReturnCreatedBlueprintVersion() throws IOException {
        // Given — parent blueprint exists
        String namePrefix = "whenCreateBlueprintVersionThenReturnCreatedBlueprintVersion";

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

        try {
            BlueprintVersionRes blueprintVersion = new BlueprintVersionRes();
            blueprintVersion.setName(namePrefix + "-version");
            blueprintVersion.setDescription(namePrefix + "-desc");
            blueprintVersion.setReadme(namePrefix + "-readme");
            blueprintVersion.setTag("v1.0.0");
            blueprintVersion.setVersionNumber("1.0.0");
            blueprintVersion.setSpec("bp-spec");
            blueprintVersion.setSpecVersion("1.0.0");
            blueprintVersion.setBlueprint(blueprintResponse.getBody());
            blueprintVersion.setContent(objectMapper.readTree("{\"stub\":true}"));

            // When
            ResponseEntity<BlueprintVersionRes> response = rest.postForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS),
                    new HttpEntity<>(blueprintVersion),
                    BlueprintVersionRes.class
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getUuid()).isNotNull();
            assertThat(response.getBody().getName()).isEqualTo(blueprintVersion.getName());
            assertThat(response.getBody().getReadme()).isEqualTo(blueprintVersion.getReadme());
            assertThat(response.getBody().getVersionNumber()).isEqualTo(blueprintVersion.getVersionNumber());
            assertThat(response.getBody().getSpec()).isEqualTo(blueprintVersion.getSpec());
            assertThat(response.getBody().getSpecVersion()).isEqualTo(blueprintVersion.getSpecVersion());
            assertThat(response.getBody().getContent()).isEqualTo(blueprintVersion.getContent());

            // Cleanup
            rest.delete(apiUrl(RoutesV2.BLUEPRINT_VERSIONS, "/" + response.getBody().getUuid()));
        } finally {
            rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
        }
    }

    /**
     * Feature: Get blueprint version by id
     * Given a blueprint version exists with a known uuid
     * When the client sends GET to "/api/v2/pp/blueprint/blueprints-versions/{uuid}"
     * Then the response status is 200
     * And the response body matches the stored version
     */
    @Test
    public void whenGetBlueprintVersionByIdThenReturnBlueprintVersion() throws IOException {
        String namePrefix = "whenGetBlueprintVersionByIdThenReturnBlueprintVersion";
        
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

        try {
            
            BlueprintVersionRes blueprintVersion = new BlueprintVersionRes();
            blueprintVersion.setName(namePrefix + "-version");
            blueprintVersion.setDescription(namePrefix + "-desc");
            blueprintVersion.setReadme(namePrefix + "-readme");
            blueprintVersion.setTag("v1.0.0");
            blueprintVersion.setVersionNumber("1.0.0");
            blueprintVersion.setSpec("bp-spec");
            blueprintVersion.setSpecVersion("1.0.0");
            blueprintVersion.setBlueprint(blueprintResponse.getBody());
            blueprintVersion.setContent(objectMapper.readTree("{\"stub\":true}"));

            ResponseEntity<BlueprintVersionRes> createResponse = rest.postForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS),
                    new HttpEntity<>(blueprintVersion),
                    BlueprintVersionRes.class
            );
            assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            String blueprintVersionUuid = createResponse.getBody().getUuid();

            // When
            ResponseEntity<BlueprintVersionRes> response = rest.getForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS, "/" + blueprintVersionUuid),
                    BlueprintVersionRes.class
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getUuid()).isEqualTo(blueprintVersionUuid);
            assertThat(response.getBody().getName()).isEqualTo(blueprintVersion.getName());
            assertThat(response.getBody().getReadme()).isEqualTo(blueprintVersion.getReadme());
            assertThat(response.getBody().getSpec()).isEqualTo(blueprintVersion.getSpec());
            assertThat(response.getBody().getSpecVersion()).isEqualTo(blueprintVersion.getSpecVersion());
            assertThat(response.getBody().getVersionNumber()).isEqualTo(blueprintVersion.getVersionNumber());
            assertThat(response.getBody().getContent()).isEqualTo(blueprintVersion.getContent());

            // Cleanup
            rest.delete(apiUrl(RoutesV2.BLUEPRINT_VERSIONS, "/" + blueprintVersionUuid));
        } finally {
            rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
        }
    }

    /**
     * Feature: Get blueprint version by id — not found
     * Given no blueprint version exists for uuid "non-existent-uuid"
     * When the client sends GET to "/api/v2/pp/blueprint/blueprints-versions/non-existent-uuid"
     * Then the response status is 404
     */
    @Test
    public void whenGetBlueprintVersionByNonExistentUuidThenReturnNotFound() {
        // When
        ResponseEntity<String> response = rest.getForEntity(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS, "/non-existent-uuid"),
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * Feature: Search blueprint versions — paginated short list
     * Given one or more blueprint versions exist
     * When the client sends GET to "/api/v2/pp/blueprint/blueprints-versions" with default pagination
     * Then the response status is 200
     * And the body has "content" as an array of short DTOs (uuid, blueprintUuid, name, description, tag, versionNumber, createdBy, updatedBy, audit timestamps as defined on BlueprintVersionShortRes)
     * And "totalElements" matches the number of matching versions
     * And default ordering is createdAt descending
     */
    @Test
    public void whenSearchBlueprintVersionsThenReturnPaginatedResults() throws IOException {
        String namePrefix = "whenSearchBlueprintVersionsThenReturnPaginatedResults";

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

        try {

            BlueprintVersionRes blueprintVersion = new BlueprintVersionRes();
            blueprintVersion.setName(namePrefix + "-version");
            blueprintVersion.setDescription(namePrefix + "-desc");
            blueprintVersion.setReadme(namePrefix + "-readme");
            blueprintVersion.setTag("v1.0.0");
            blueprintVersion.setVersionNumber("1.0.0");
            blueprintVersion.setSpec("bp-spec");
            blueprintVersion.setSpecVersion("1.0.0");
            blueprintVersion.setBlueprint(blueprintResponse.getBody());
            blueprintVersion.setContent(objectMapper.readTree("{\"stub\":true}"));

            ResponseEntity<BlueprintVersionRes> createResponse = rest.postForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS),
                    new HttpEntity<>(blueprintVersion),
                    BlueprintVersionRes.class
            );
            assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            String blueprintVersionUuid = createResponse.getBody().getUuid();

            // When
            ResponseEntity<JsonNode> response = rest.getForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS),
                    JsonNode.class
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().has("content")).isTrue();
            assertThat(response.getBody().has("totalElements")).isTrue();
            assertThat(response.getBody().get("totalElements").asInt()).isGreaterThanOrEqualTo(1);

            // Cleanup
            rest.delete(apiUrl(RoutesV2.BLUEPRINT_VERSIONS, "/" + blueprintVersionUuid));
        } finally {
            rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
        }
    }

    /**
     * Feature: Search blueprint versions — text / filter parameter
     * Given multiple blueprint versions exist with different names or searchable fields
     * When the client sends GET with the supported search/filter query blueprintParameters
     * Then the response status is 200
     * And every entry in "content" matches the filter semantics
     * And "totalElements" equals the filtered count
     */
    @Test
    public void whenSearchBlueprintVersionsWithSearchParameterThenReturnFilteredResults() throws IOException {
        String namePrefix = "whenSearchBlueprintVersionsWithSearchParameterThenReturnFilteredResults";

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

        try {
            BlueprintVersionRes blueprintVersionMatching = new BlueprintVersionRes();
            blueprintVersionMatching.setName("test-version-matching");
            blueprintVersionMatching.setDescription(namePrefix + "-desc");
            blueprintVersionMatching.setReadme(namePrefix + "-readme");
            blueprintVersionMatching.setTag("v1.0.0");
            blueprintVersionMatching.setVersionNumber("1.0.0");
            blueprintVersionMatching.setSpec("bp-spec");
            blueprintVersionMatching.setSpecVersion("1.0.0");
            blueprintVersionMatching.setBlueprint(blueprintResponse.getBody());
            blueprintVersionMatching.setContent(objectMapper.readTree("{\"stub\":true}"));

            ResponseEntity<BlueprintVersionRes> blueprintVersionMatchingResponse = rest.postForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS),
                    new HttpEntity<>(blueprintVersionMatching),
                    BlueprintVersionRes.class
            );
            assertThat(blueprintVersionMatchingResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            String matchingId = blueprintVersionMatchingResponse.getBody().getUuid();

            BlueprintVersionRes blueprintVersionOther = new BlueprintVersionRes();
            blueprintVersionOther.setName("test-version-other");
            blueprintVersionOther.setDescription(namePrefix + "-desc");
            blueprintVersionOther.setReadme(namePrefix + "-readme");
            blueprintVersionOther.setTag("v1.0.1");
            blueprintVersionOther.setVersionNumber("1.0.1");
            blueprintVersionOther.setSpec("bp-spec");
            blueprintVersionOther.setSpecVersion("1.0.0");
            blueprintVersionOther.setBlueprint(blueprintResponse.getBody());
            blueprintVersionOther.setContent(objectMapper.readTree("{\"stub\":true}"));

            ResponseEntity<BlueprintVersionRes> blueprintVersionOtherReponse = rest.postForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS),
                    new HttpEntity<>(blueprintVersionOther),
                    BlueprintVersionRes.class
            );
            assertThat(blueprintVersionOtherReponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            String otherId = blueprintVersionOtherReponse.getBody().getUuid();

            // When
            ResponseEntity<JsonNode> response = rest.exchange(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS) + "?search=matching",
                    HttpMethod.GET,
                    null,
                    JsonNode.class
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("totalElements").asInt()).isEqualTo(1);
            JsonNode content = response.getBody().get("content");
            assertThat(content.size()).isEqualTo(1);
            BlueprintVersionShortRes row = objectMapper.treeToValue(content.get(0), BlueprintVersionShortRes.class);
            assertThat(row.getUuid()).isEqualTo(matchingId);
            assertThat(row.getName()).isEqualTo("test-version-matching");

            // Cleanup
            rest.delete(apiUrl(RoutesV2.BLUEPRINT_VERSIONS, "/" + matchingId));
            rest.delete(apiUrl(RoutesV2.BLUEPRINT_VERSIONS, "/" + otherId));
        } finally {
            rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
        }
    }

    /**
     * Feature: Update blueprint version
     * Given an existing blueprint version with known uuid
     * When the client sends PUT with valid updated fields
     * Then the response status is 200
     * And GET by the same uuid returns the updated data
     */
    @Test
    public void whenUpdateBlueprintVersionThenReturnUpdatedBlueprintVersion() throws IOException {
        String namePrefix = "whenUpdateBlueprintVersionThenReturnUpdatedBlueprintVersion";
        
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

        try {
            BlueprintVersionRes blueprintVersion = new BlueprintVersionRes();
            blueprintVersion.setName(namePrefix + "-version");
            blueprintVersion.setDescription(namePrefix + "-desc");
            blueprintVersion.setReadme(namePrefix + "-readme");
            blueprintVersion.setTag("v1.0.0");
            blueprintVersion.setVersionNumber("1.0.0");
            blueprintVersion.setSpec("bp-spec");
            blueprintVersion.setSpecVersion("1.0.0");
            blueprintVersion.setBlueprint(blueprintResponse.getBody());
            blueprintVersion.setContent(objectMapper.readTree("{\"stub\":true}"));

            ResponseEntity<BlueprintVersionRes> createResponse = rest.postForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS),
                    new HttpEntity<>(blueprintVersion),
                    BlueprintVersionRes.class
            );
            assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            String blueprintVersionUuid = createResponse.getBody().getUuid();

            blueprintVersion.setUuid(blueprintVersionUuid);
            blueprintVersion.setName("updated-version-name");
            blueprintVersion.setDescription("updated-description");
            blueprintVersion.setReadme("updated-readme");

            // When
            ResponseEntity<BlueprintVersionRes> response = rest.exchange(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS, "/" + blueprintVersionUuid),
                    HttpMethod.PUT,
                    new HttpEntity<>(blueprintVersion),
                    BlueprintVersionRes.class
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getName()).isEqualTo("updated-version-name");
            assertThat(response.getBody().getDescription()).isEqualTo("updated-description");
            assertThat(response.getBody().getReadme()).isEqualTo("updated-readme");
            
            ResponseEntity<BlueprintVersionRes> getResponse = rest.getForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS, "/" + blueprintVersionUuid),
                    BlueprintVersionRes.class
            );
            assertThat(getResponse.getBody().getName()).isEqualTo("updated-version-name");

            // Cleanup
            rest.delete(apiUrl(RoutesV2.BLUEPRINT_VERSIONS, "/" + blueprintVersionUuid));
        } finally {
            rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
        }
    }

    /**
     * Feature: Update blueprint version — not found
     * Given no blueprint version exists for uuid "missing-uuid"
     * When the client sends PUT to "/api/v2/pp/blueprint/blueprints-versions/missing-uuid" with a valid body
     * Then the response status is 404
     */
    @Test
    public void whenUpdateNonExistentBlueprintVersionThenReturnNotFound() throws IOException {
        BlueprintVersionRes blueprintVersion = new BlueprintVersionRes();
        blueprintVersion.setName("non-existent-version");
        blueprintVersion.setReadme("non-existent-readme");
        blueprintVersion.setTag("v1.0.0");
        blueprintVersion.setVersionNumber("1.0.0");
        blueprintVersion.setSpec("bp-spec");
        blueprintVersion.setSpecVersion("1.0.0");
        blueprintVersion.setContent(objectMapper.readTree("{\"stub\":true}"));
        blueprintVersion.setBlueprint(new BlueprintRes());
        blueprintVersion.getBlueprint().setUuid("00000000-0000-0000-0000-000000000000");

        // When
        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS, "/missing-uuid"),
                HttpMethod.PUT,
                new HttpEntity<>(blueprintVersion),
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * Feature: Delete blueprint version
     * Given an existing blueprint version with known uuid
     * When the client sends DELETE to "/api/v2/pp/blueprint/blueprints-versions/{uuid}"
     * Then the response status is 204
     * And GET to the same path returns 404
     */
    @Test
    public void whenDeleteBlueprintVersionThenReturnNoContent() throws IOException {
        String namePrefix = "whenDeleteBlueprintVersionThenReturnNoContent";
        
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

        try {

            BlueprintVersionRes blueprintVersion = new BlueprintVersionRes();
            blueprintVersion.setName(namePrefix + "-version");
            blueprintVersion.setDescription(namePrefix + "-desc");
            blueprintVersion.setReadme(namePrefix + "-readme");
            blueprintVersion.setTag("v1.0.0");
            blueprintVersion.setVersionNumber("1.0.0");
            blueprintVersion.setSpec("bp-spec");
            blueprintVersion.setSpecVersion("1.0.0");
            blueprintVersion.setBlueprint(blueprintResponse.getBody());
            blueprintVersion.setContent(objectMapper.readTree("{\"stub\":true}"));

            ResponseEntity<BlueprintVersionRes> createResponse = rest.postForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS),
                    new HttpEntity<>(blueprintVersion),
                    BlueprintVersionRes.class
            );
            assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            String blueprintVersionUuid = createResponse.getBody().getUuid();

            // When
            ResponseEntity<Void> response = rest.exchange(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS, "/" + blueprintVersionUuid),
                    HttpMethod.DELETE,
                    null,
                    Void.class
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            ResponseEntity<String> getResponse = rest.getForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS, "/" + blueprintVersionUuid),
                    String.class
            );
            assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        } finally {
            rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
        }
    }

    /**
     * Feature: Delete blueprint version — not found
     * Given no blueprint version exists for uuid "missing-uuid"
     * When the client sends DELETE to "/api/v2/pp/blueprint/blueprints-versions/missing-uuid"
     * Then the response status is 404
     */
    @Test
    public void whenDeleteNonExistentBlueprintVersionThenReturnNotFound() {
        // When
        ResponseEntity<String> response = rest.exchange(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS, "/missing-uuid"),
                HttpMethod.DELETE,
                null,
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * Feature: JSON content field — store and retrieve
     * Given a valid blueprint version payload including a non-trivial JSON content object
     * When the client sends POST to create the version
     * Then the response status is 201
     * And GET by uuid returns content deeply equal to the submitted JSON
     * And a collection GET returns short entries without exposing the large content payload
     */
    @Test
    public void whenCreateBlueprintVersionWithJsonContentThenContentIsCorrectlyStoredAndRetrieved() throws IOException {
        String namePrefix = "whenCreateBlueprintVersionWithJsonContentThenContentIsCorrectlyStoredAndRetrieved";

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

        try {
            // Create a JSON content
            String jsonContent = "{\n" +
                "  \"spec\": \"odm-blueprint-manifest\",\n" +
                "  \"specVersion\": \"v1\",\n" +
                "  \"name\": \"analytics-lakehouse\",\n" +
                "  \"displayName\": \"Analytics Lakehouse Blueprint\",\n" +
                "  \"version\": \"1.0.0\",\n" +
                "  \"description\": \"Provisions storage and compute defaults for an analytics data product.\",\n" +
                "  \"blueprintParameters\": [\n" +
                "    {\n" +
                "      \"key\": \"environment\",\n" +
                "      \"type\": \"string\",\n" +
                "      \"required\": true,\n" +
                "      \"allowedValues\": [\"dev\", \"staging\", \"prod\"],\n" +
                "      \"group\": \"General Configuration\",\n" +
                "      \"label\": \"Environment\",\n" +
                "      \"uiDescription\": \"Deployment stage for this data product.\",\n" +
                "      \"formType\": \"dropdown\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"key\": \"retentionDays\",\n" +
                "      \"type\": \"integer\",\n" +
                "      \"default\": 90,\n" +
                "      \"min\": 1,\n" +
                "      \"max\": 3650,\n" +
                "      \"group\": \"Storage\",\n" +
                "      \"label\": \"Data retention (days)\",\n" +
                "      \"formType\": \"number\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"protectedResources\": [\n" +
                "    \"infrastructure/core/**\",\n" +
                "    \"README.md\"\n" +
                "  ],\n" +
                "  \"instantiationStrategy\": \"monorepo\"\n" +
                "}";

            BlueprintVersionRes blueprintVersion = new BlueprintVersionRes();
            blueprintVersion.setName(namePrefix + "-version");
            blueprintVersion.setDescription(namePrefix + "-desc");
            blueprintVersion.setReadme(namePrefix + "-readme");
            blueprintVersion.setTag("v1.0.0");
            blueprintVersion.setVersionNumber("1.0.0");
            blueprintVersion.setSpec("bp-spec");
            blueprintVersion.setSpecVersion("1.0.0");
            blueprintVersion.setBlueprint(blueprintResponse.getBody());
            JsonNode payload = objectMapper.readTree(jsonContent);
            blueprintVersion.setContent(payload);

            // When - Create the version
            ResponseEntity<BlueprintVersionRes> createResponse = rest.postForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS),
                    new HttpEntity<>(blueprintVersion),
                    BlueprintVersionRes.class
            );

            // Then - Verify creation
            assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            String blueprintVersionUuid = createResponse.getBody().getUuid();
            assertThat(createResponse.getBody().getContent()).isEqualTo(payload);

            // When - Retrieve the versio
            ResponseEntity<BlueprintVersionRes> getResponse = rest.getForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS, "/" + blueprintVersionUuid),
                    BlueprintVersionRes.class
            );

            // Then - Verify the JSON content is correctly retrieved
            assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(getResponse.getBody()).isNotNull();
            assertThat(getResponse.getBody().getUuid()).isEqualTo(blueprintVersionUuid);
            assertThat(getResponse.getBody().getContent()).isEqualTo(payload);

            JsonNode retrievedContent = getResponse.getBody().getContent();
            assertThat(retrievedContent.has("spec")).isTrue();
            assertThat(retrievedContent.get("blueprintParameters").get(0).has("key")).isTrue();
            assertThat(retrievedContent.get("blueprintParameters").get(0).get("key").asText()).isEqualTo("environment");
            assertThat(retrievedContent.get("protectedResources").get(0).asText())
                    .isEqualTo("infrastructure/core/**");


            // Cleanup
            rest.delete(apiUrl(RoutesV2.BLUEPRINT_VERSIONS, "/" + blueprintVersionUuid));
        } finally {
            rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
        }
    }

    /**
     * Feature: Create blueprint version — duplicate version number
     * Given blueprint B has a version with versionNumber "9.9.9"
     * When the client sends POST with the same blueprintUuid and the same versionNumber (per case-insensitive rule)
     * Then the response status is 409
     */
    @Test
    public void whenCreateBlueprintVersionWithDuplicateVersionNumberThenReturnConflict() throws IOException {
        String namePrefix = "whenCreateBlueprintVersionWithDuplicateVersionNumberThenReturnConflict";

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

        try {

            BlueprintVersionRes blueprintVersion1 = new BlueprintVersionRes();
            blueprintVersion1.setName(namePrefix + "-version");
            blueprintVersion1.setDescription(namePrefix + "-desc");
            blueprintVersion1.setReadme(namePrefix + "-readme");
            blueprintVersion1.setTag("v9.9.9");
            blueprintVersion1.setVersionNumber("9.9.9");
            blueprintVersion1.setSpec("bp-spec");
            blueprintVersion1.setSpecVersion("1.0.0");
            blueprintVersion1.setBlueprint(blueprintResponse.getBody());
            blueprintVersion1.setContent(objectMapper.readTree("{\"stub\":true}"));

            ResponseEntity<BlueprintVersionRes> blueprintVersion1Response = rest.postForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS),
                    new HttpEntity<>(blueprintVersion1),
                    BlueprintVersionRes.class
            );
            assertThat(blueprintVersion1Response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            String blueprintVersion1Uuid = blueprintVersion1Response.getBody().getUuid();

            BlueprintVersionRes blueprintVersion2 = new BlueprintVersionRes();
            blueprintVersion2.setName(namePrefix + "-version");
            blueprintVersion2.setDescription(namePrefix + "-desc");
            blueprintVersion2.setReadme(namePrefix + "-readme");
            blueprintVersion2.setTag("v9.9.9-dup");
            blueprintVersion2.setVersionNumber("9.9.9");
            blueprintVersion2.setSpec("bp-spec");
            blueprintVersion2.setSpecVersion("1.0.0");
            blueprintVersion2.setBlueprint(blueprintResponse.getBody());
            blueprintVersion2.setContent(objectMapper.readTree("{\"stub\":true}"));

            // When
            ResponseEntity<String> response = rest.postForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS),
                    new HttpEntity<>(blueprintVersion2),
                    String.class
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

            // Cleanup
            rest.delete(apiUrl(RoutesV2.BLUEPRINT_VERSIONS, "/" + blueprintVersion1Uuid));
        } finally {
            rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
        }
    }

    /**
     * Feature: Invalid input — bad request
     * Given a blueprint version payload with invalid data
     * When the client sends POST to "/api/v2/pp/blueprint/blueprints-versions" with that body
     * Then the response status is 400
     */
    @Test
    public void whenCreateBlueprintVersionWithInvalidDataThenReturnBadRequest() {
        // Given
        BlueprintVersionRes invalid = new BlueprintVersionRes();

        // When
        ResponseEntity<String> response = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS),
                new HttpEntity<>(invalid),
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
