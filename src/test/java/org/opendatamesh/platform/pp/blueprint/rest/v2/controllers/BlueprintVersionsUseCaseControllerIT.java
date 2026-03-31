package org.opendatamesh.platform.pp.blueprint.rest.v2.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.opendatamesh.platform.pp.blueprint.rest.v2.BlueprintApplicationIT;
import org.opendatamesh.platform.pp.blueprint.rest.v2.RoutesV2;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.BlueprintVersionRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.publish.PublishBlueprintVersionCommandRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.publish.PublishBlueprintVersionResponseRes;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link BlueprintVersionsUseCaseController}.
 * Scenarios trace to {@code agentspecs/specs/blueprint_version/blueprint_use_case_publish/spec.md} (Gherkin).
 */
public class BlueprintVersionsUseCaseControllerIT extends BlueprintApplicationIT {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static String validManifestJson(String manifestName, String version) {
        return """
                {
                  "spec": "odm-blueprint-manifest",
                  "specVersion": "1.0.0",
                  "name": "%s",
                  "displayName": "Display %s",
                  "version": "%s",
                  "description": "Test manifest.",
                  "instantiation": {
                    "strategy": "monorepo"
                  }
                }
                """.formatted(manifestName, manifestName, version);
    }

    private PublishBlueprintVersionCommandRes publishCommand(
            BlueprintRes blueprint,
            String versionName,
            String manifestName,
            String manifestVersion,
            String spec,
            String specVersion
    ) throws IOException {
        PublishBlueprintVersionCommandRes cmd = new PublishBlueprintVersionCommandRes();
        PublishBlueprintVersionCommandRes.BlueprintVersion bv = new PublishBlueprintVersionCommandRes.BlueprintVersion();
        bv.setName(versionName);
        bv.setDescription("desc");
        bv.setReadme("readme");
        bv.setTag("v" + manifestVersion);
        bv.setSpec(spec);
        bv.setSpecVersion(specVersion);
        bv.setContent(objectMapper.readTree(validManifestJson(manifestName, manifestVersion)));
        PublishBlueprintVersionCommandRes.BlueprintVersion.Blueprint bp =
                new PublishBlueprintVersionCommandRes.BlueprintVersion.Blueprint();
        bp.setUuid(blueprint.getUuid());
        bv.setBlueprint(bp);
        cmd.setBlueprintVersion(bv);
        return cmd;
    }

    /**
     * Requirement ID: PUB-BP-001
     * Scenario: Successful publish returns 201 and created blueprint version
     */
    @Test
    public void whenPublishBlueprintVersionWithValidPayloadThenReturn201AndCreatedVersion() throws IOException {
        String prefix = "pubBp001";
        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName(prefix + "-bp");
        blueprint.setDisplayName(prefix + "-display");
        blueprint.setDescription(prefix + "-description");

        ResponseEntity<BlueprintRes> blueprintResponse = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                BlueprintRes.class
        );
        assertThat(blueprintResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String blueprintUuid = blueprintResponse.getBody().getUuid();

        try {
            PublishBlueprintVersionCommandRes cmd = publishCommand(
                    blueprintResponse.getBody(),
                    prefix + "-version",
                    prefix + "-manifest",
                    "1.0.0",
                    "odm-blueprint-manifest",
                    "1.0.0"
            );

            ResponseEntity<PublishBlueprintVersionResponseRes> response = rest.postForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS_PUBLISH),
                    new HttpEntity<>(cmd),
                    PublishBlueprintVersionResponseRes.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            BlueprintVersionRes created = response.getBody().getBlueprintVersion();
            assertThat(created).isNotNull();
            assertThat(created.getUuid()).isNotNull();
            assertThat(created.getBlueprint()).isNotNull();
            assertThat(created.getBlueprint().getUuid()).isNotNull();
            assertThat(created.getBlueprint().getName()).isEqualTo(prefix + "-bp");
            assertThat(created.getVersionNumber()).isEqualTo("1.0.0");

            ResponseEntity<BlueprintVersionRes> getResponse = rest.getForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS, "/" + created.getUuid()),
                    BlueprintVersionRes.class
            );
            assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(getResponse.getBody().getName()).isEqualTo(prefix + "-version");
            assertThat(getResponse.getBody().getVersionNumber()).isEqualTo("1.0.0");

            rest.delete(apiUrl(RoutesV2.BLUEPRINT_VERSIONS, "/" + created.getUuid()));
        } finally {
            rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
        }
    }

    /**
     * Requirement ID: PUB-BP-002
     * Scenario: Invalid spec returns 400
     */
    @Test
    public void whenPublishWithInvalidSpecThenReturn400AndNoVersionPersisted() throws IOException {
        String prefix = "pubBp002";
        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName(prefix + "-bp");
        blueprint.setDisplayName(prefix + "-display");
        blueprint.setDescription(prefix + "-description");

        ResponseEntity<BlueprintRes> blueprintResponse = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                BlueprintRes.class
        );
        assertThat(blueprintResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String blueprintUuid = blueprintResponse.getBody().getUuid();

        try {
            ResponseEntity<JsonNode> countBefore = rest.getForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS) + "?blueprintUuid=" + blueprintUuid,
                    JsonNode.class
            );
            int totalBefore = countBefore.getBody().get("totalElements").asInt();

            PublishBlueprintVersionCommandRes cmd = publishCommand(
                    blueprintResponse.getBody(),
                    prefix + "-version",
                    prefix + "-manifest",
                    "1.0.0",
                    "not-a-valid-spec",
                    "1.0.0"
            );

            ResponseEntity<String> response = rest.postForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS_PUBLISH),
                    new HttpEntity<>(cmd),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

            ResponseEntity<JsonNode> countAfter = rest.getForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS) + "?blueprintUuid=" + blueprintUuid,
                    JsonNode.class
            );
            assertThat(countAfter.getBody().get("totalElements").asInt()).isEqualTo(totalBefore);
        } finally {
            rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
        }
    }

    /**
     * Requirement ID: PUB-BP-003
     * Scenario: Publishing when name and versionNumber already exist returns 409
     */
    @Test
    public void whenPublishDuplicateNameAndVersionNumberThenReturn409() throws IOException {
        String prefix = "pubBp003";
        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName(prefix + "-bp");
        blueprint.setDisplayName(prefix + "-display");
        blueprint.setDescription(prefix + "-description");

        ResponseEntity<BlueprintRes> blueprintResponse = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                BlueprintRes.class
        );
        assertThat(blueprintResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String blueprintUuid = blueprintResponse.getBody().getUuid();

        try {
            PublishBlueprintVersionCommandRes first = publishCommand(
                    blueprintResponse.getBody(),
                    prefix + "-version",
                    prefix + "-manifest",
                    "1.0.0",
                    "odm-blueprint-manifest",
                    "1.0.0"
            );
            ResponseEntity<PublishBlueprintVersionResponseRes> ok = rest.postForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS_PUBLISH),
                    new HttpEntity<>(first),
                    PublishBlueprintVersionResponseRes.class
            );
            assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            String versionUuid = ok.getBody().getBlueprintVersion().getUuid();

            PublishBlueprintVersionCommandRes duplicate = publishCommand(
                    blueprintResponse.getBody(),
                    prefix + "-version",
                    prefix + "-manifest-other",
                    "1.0.0",
                    "odm-blueprint-manifest",
                    "1.0.0"
            );
            duplicate.getBlueprintVersion().setTag("v1.0.1");

            ResponseEntity<String> conflict = rest.postForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS_PUBLISH),
                    new HttpEntity<>(duplicate),
                    String.class
            );
            assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

            rest.delete(apiUrl(RoutesV2.BLUEPRINT_VERSIONS, "/" + versionUuid));
        } finally {
            rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
        }
    }

    /**
     * Requirement ID: PUB-BP-004
     * Scenario: Publishing when name and tag already exist returns 409
     */
    @Test
    public void whenPublishDuplicateNameAndTagThenReturn409() throws IOException {
        String prefix = "pubBp004";
        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName(prefix + "-bp");
        blueprint.setDisplayName(prefix + "-display");
        blueprint.setDescription(prefix + "-description");

        ResponseEntity<BlueprintRes> blueprintResponse = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                BlueprintRes.class
        );
        assertThat(blueprintResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String blueprintUuid = blueprintResponse.getBody().getUuid();

        try {
            PublishBlueprintVersionCommandRes first = publishCommand(
                    blueprintResponse.getBody(),
                    prefix + "-version",
                    prefix + "-manifest-a",
                    "1.0.0",
                    "odm-blueprint-manifest",
                    "1.0.0"
            );
            ResponseEntity<PublishBlueprintVersionResponseRes> ok = rest.postForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS_PUBLISH),
                    new HttpEntity<>(first),
                    PublishBlueprintVersionResponseRes.class
            );
            assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            String versionUuid = ok.getBody().getBlueprintVersion().getUuid();

            PublishBlueprintVersionCommandRes duplicateTag = publishCommand(
                    blueprintResponse.getBody(),
                    prefix + "-version",
                    prefix + "-manifest-b",
                    "2.0.0",
                    "odm-blueprint-manifest",
                    "1.0.0"
            );
            duplicateTag.getBlueprintVersion().setTag("v1.0.0");

            ResponseEntity<String> conflict = rest.postForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS_PUBLISH),
                    new HttpEntity<>(duplicateTag),
                    String.class
            );
            assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

            rest.delete(apiUrl(RoutesV2.BLUEPRINT_VERSIONS, "/" + versionUuid));
        } finally {
            rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
        }
    }

    /**
     * Requirement ID: PUB-BP-005
     * Scenario: Invalid manifest content returns 400
     */
    @Test
    public void whenPublishWithInvalidManifestContentThenReturn400() throws IOException {
        String prefix = "pubBp005";
        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName(prefix + "-bp");
        blueprint.setDisplayName(prefix + "-display");
        blueprint.setDescription(prefix + "-description");

        ResponseEntity<BlueprintRes> blueprintResponse = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                BlueprintRes.class
        );
        assertThat(blueprintResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String blueprintUuid = blueprintResponse.getBody().getUuid();

        try {
            ResponseEntity<JsonNode> countBefore = rest.getForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS) + "?blueprintUuid=" + blueprintUuid,
                    JsonNode.class
            );
            int totalBefore = countBefore.getBody().get("totalElements").asInt();

            PublishBlueprintVersionCommandRes cmd = new PublishBlueprintVersionCommandRes();
            PublishBlueprintVersionCommandRes.BlueprintVersion bv = new PublishBlueprintVersionCommandRes.BlueprintVersion();
            bv.setName(prefix + "-version");
            bv.setDescription("desc");
            bv.setReadme("readme");
            bv.setTag("v1.0.0");
            bv.setSpec("odm-blueprint-manifest");
            bv.setSpecVersion("1.0.0");
            bv.setContent(objectMapper.readTree(
                    "{\"spec\":\"odm-blueprint-manifest\",\"specVersion\":\"1.0.0\",\"name\":\"bad-manifest\","
                            + "\"version\":\"not-a-semver\",\"instantiation\":{\"strategy\":\"monorepo\"}}"));
            PublishBlueprintVersionCommandRes.BlueprintVersion.Blueprint bp =
                    new PublishBlueprintVersionCommandRes.BlueprintVersion.Blueprint();
            bp.setUuid(blueprintUuid);
            bv.setBlueprint(bp);
            cmd.setBlueprintVersion(bv);

            ResponseEntity<String> response = rest.postForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS_PUBLISH),
                    new HttpEntity<>(cmd),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

            ResponseEntity<JsonNode> countAfter = rest.getForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS) + "?blueprintUuid=" + blueprintUuid,
                    JsonNode.class
            );
            assertThat(countAfter.getBody().get("totalElements").asInt()).isEqualTo(totalBefore);
        } finally {
            rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
        }
    }
}
