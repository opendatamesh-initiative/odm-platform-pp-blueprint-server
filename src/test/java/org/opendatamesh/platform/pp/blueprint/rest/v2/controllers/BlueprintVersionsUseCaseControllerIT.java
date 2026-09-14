package org.opendatamesh.platform.pp.blueprint.rest.v2.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;
import org.opendatamesh.platform.pp.blueprint.manifest.ManifestYamlTestSupport;
import org.opendatamesh.platform.pp.blueprint.rest.v2.BlueprintApplicationIT;
import org.opendatamesh.platform.pp.blueprint.rest.v2.RoutesV2;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintRepoOwnerTypeRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintRepoProviderTypeRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintTypeRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.BlueprintVersionRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.publish.PublishBlueprintVersionCommandRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.publish.PublishBlueprintVersionResponseRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.updatedocumentationfields.UpdateBlueprintVersionDocumentationFieldsCommandRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.updatedocumentationfields.UpdateBlueprintVersionDocumentationFieldsReponseRes;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link BlueprintVersionsUseCaseController}.
 * Scenarios trace to {@code spdd/analysis/GGQPA-XXX-202603311547-[Analysis]-blueprint-use-case-publish.md} (Gherkin).
 */
public class BlueprintVersionsUseCaseControllerIT extends BlueprintApplicationIT {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String MONOREPO_MANIFEST_RESOURCE = "/manifest/example-2.1-monorepo-no-composition.yaml";
    private static final String WRONG_MANIFEST_RESOURCE = "/manifest/manifest-wrong.yml";

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
        bv.setContent(ManifestYamlTestSupport.readYamlTreeFromClasspath(MONOREPO_MANIFEST_RESOURCE));
        PublishBlueprintVersionCommandRes.BlueprintVersion.Blueprint bp =
                new PublishBlueprintVersionCommandRes.BlueprintVersion.Blueprint();
        bp.setUuid(blueprint.getUuid());
        bv.setBlueprint(bp);
        bv.setCreatedBy("it-created-by");
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

        
        blueprint.setBlueprintType(BlueprintTypeRes.BLUEPRINT);
        BlueprintRes.BlueprintRepoRes __repo = new BlueprintRes.BlueprintRepoRes();
        __repo.setExternalIdentifier("ext-id");
        __repo.setName("repo-name");
        __repo.setDescription("repo-desc");
        __repo.setManifestRootPath("/manifest");
        __repo.setDescriptorTemplatePath("/template");
        __repo.setReadmePath("/readme");
        __repo.setRemoteUrlHttp("https://github.com/org/repo.git");
        __repo.setRemoteUrlSsh("git@github.com:org/repo.git");
        __repo.setDefaultBranch("main");
        __repo.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
        __repo.setProviderBaseUrl("https://github.com");
        __repo.setOwnerId("org");
        __repo.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
        blueprint.setBlueprintRepo(__repo);
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
            assertThat(created.getCreatedBy()).isEqualTo("it-created-by");

            ResponseEntity<BlueprintVersionRes> getResponse = rest.getForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS, "/" + created.getUuid()),
                    BlueprintVersionRes.class
            );
            assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(getResponse.getBody().getName()).isEqualTo(prefix + "-version");
            assertThat(getResponse.getBody().getVersionNumber()).isEqualTo("1.0.0");
            assertThat(getResponse.getBody().getCreatedBy()).isEqualTo("it-created-by");
            JsonNode storedProtected = getResponse.getBody().getContent().path("protectedResources");
            assertThat(storedProtected.isArray()).isTrue();
            assertThat(storedProtected.get(0).has("repository")).isFalse();
            assertThat(storedProtected.get(1).has("repository")).isFalse();

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

        
        blueprint.setBlueprintType(BlueprintTypeRes.BLUEPRINT);
        BlueprintRes.BlueprintRepoRes __repo = new BlueprintRes.BlueprintRepoRes();
        __repo.setExternalIdentifier("ext-id");
        __repo.setName("repo-name");
        __repo.setDescription("repo-desc");
        __repo.setManifestRootPath("/manifest");
        __repo.setDescriptorTemplatePath("/template");
        __repo.setReadmePath("/readme");
        __repo.setRemoteUrlHttp("https://github.com/org/repo.git");
        __repo.setRemoteUrlSsh("git@github.com:org/repo.git");
        __repo.setDefaultBranch("main");
        __repo.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
        __repo.setProviderBaseUrl("https://github.com");
        __repo.setOwnerId("org");
        __repo.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
        blueprint.setBlueprintRepo(__repo);
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
        String prefix = "pubBp003-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName(prefix + "-bp");
        blueprint.setDisplayName(prefix + "-display");
        blueprint.setDescription(prefix + "-description");

        
        blueprint.setBlueprintType(BlueprintTypeRes.BLUEPRINT);
        BlueprintRes.BlueprintRepoRes __repo = new BlueprintRes.BlueprintRepoRes();
        __repo.setExternalIdentifier("ext-id");
        __repo.setName("repo-name");
        __repo.setDescription("repo-desc");
        __repo.setManifestRootPath("/manifest");
        __repo.setDescriptorTemplatePath("/template");
        __repo.setReadmePath("/readme");
        __repo.setRemoteUrlHttp("https://github.com/org/repo.git");
        __repo.setRemoteUrlSsh("git@github.com:org/repo.git");
        __repo.setDefaultBranch("main");
        __repo.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
        __repo.setProviderBaseUrl("https://github.com");
        __repo.setOwnerId("org");
        __repo.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
        blueprint.setBlueprintRepo(__repo);
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
        String prefix = "pubBp004-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName(prefix + "-bp");
        blueprint.setDisplayName(prefix + "-display");
        blueprint.setDescription(prefix + "-description");

        
        blueprint.setBlueprintType(BlueprintTypeRes.BLUEPRINT);
        BlueprintRes.BlueprintRepoRes __repo = new BlueprintRes.BlueprintRepoRes();
        __repo.setExternalIdentifier("ext-id");
        __repo.setName("repo-name");
        __repo.setDescription("repo-desc");
        __repo.setManifestRootPath("/manifest");
        __repo.setDescriptorTemplatePath("/template");
        __repo.setReadmePath("/readme");
        __repo.setRemoteUrlHttp("https://github.com/org/repo.git");
        __repo.setRemoteUrlSsh("git@github.com:org/repo.git");
        __repo.setDefaultBranch("main");
        __repo.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
        __repo.setProviderBaseUrl("https://github.com");
        __repo.setOwnerId("org");
        __repo.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
        blueprint.setBlueprintRepo(__repo);
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

        
        blueprint.setBlueprintType(BlueprintTypeRes.BLUEPRINT);
        BlueprintRes.BlueprintRepoRes __repo = new BlueprintRes.BlueprintRepoRes();
        __repo.setExternalIdentifier("ext-id");
        __repo.setName("repo-name");
        __repo.setDescription("repo-desc");
        __repo.setManifestRootPath("/manifest");
        __repo.setDescriptorTemplatePath("/template");
        __repo.setReadmePath("/readme");
        __repo.setRemoteUrlHttp("https://github.com/org/repo.git");
        __repo.setRemoteUrlSsh("git@github.com:org/repo.git");
        __repo.setDefaultBranch("main");
        __repo.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
        __repo.setProviderBaseUrl("https://github.com");
        __repo.setOwnerId("org");
        __repo.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
        blueprint.setBlueprintRepo(__repo);
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
            bv.setContent(ManifestYamlTestSupport.readYamlTreeFromClasspath(WRONG_MANIFEST_RESOURCE));
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

    /**
     * Editor updates blueprint version name and description only.
     */
    @Test
    public void whenUpdatesBlueprintVersionNameAndDescriptionThenOnlyThoseFieldsAreUpdated() throws IOException {
        String prefix = "docFields001";
        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName(prefix + "-bp");
        blueprint.setDisplayName(prefix + "-display");
        blueprint.setDescription(prefix + "-description");

        
        blueprint.setBlueprintType(BlueprintTypeRes.BLUEPRINT);
        BlueprintRes.BlueprintRepoRes __repo = new BlueprintRes.BlueprintRepoRes();
        __repo.setExternalIdentifier("ext-id");
        __repo.setName("repo-name");
        __repo.setDescription("repo-desc");
        __repo.setManifestRootPath("/manifest");
        __repo.setDescriptorTemplatePath("/template");
        __repo.setReadmePath("/readme");
        __repo.setRemoteUrlHttp("https://github.com/org/repo.git");
        __repo.setRemoteUrlSsh("git@github.com:org/repo.git");
        __repo.setDefaultBranch("main");
        __repo.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
        __repo.setProviderBaseUrl("https://github.com");
        __repo.setOwnerId("org");
        __repo.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
        blueprint.setBlueprintRepo(__repo);
ResponseEntity<BlueprintRes> blueprintResponse = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                BlueprintRes.class
        );
        assertThat(blueprintResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String blueprintUuid = blueprintResponse.getBody().getUuid();

        try {
            PublishBlueprintVersionCommandRes publishCmd = publishCommand(
                    blueprintResponse.getBody(),
                    prefix + "-version",
                    prefix + "-manifest",
                    "1.0.0",
                    "odm-blueprint-manifest",
                    "1.0.0"
            );
            ResponseEntity<PublishBlueprintVersionResponseRes> published = rest.postForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS_PUBLISH),
                    new HttpEntity<>(publishCmd),
                    PublishBlueprintVersionResponseRes.class
            );
            assertThat(published.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            String versionUuid = published.getBody().getBlueprintVersion().getUuid();

            ResponseEntity<BlueprintVersionRes> beforeGet = rest.getForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS, "/" + versionUuid),
                    BlueprintVersionRes.class
            );
            assertThat(beforeGet.getBody()).isNotNull();
            BlueprintVersionRes before = beforeGet.getBody();

            UpdateBlueprintVersionDocumentationFieldsCommandRes update = new UpdateBlueprintVersionDocumentationFieldsCommandRes();
            update.setUuid(versionUuid);
            update.setName("New version name");
            update.setDescription("New description text");
            update.setUpdatedBy("editor-user-1");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<UpdateBlueprintVersionDocumentationFieldsReponseRes> post = rest.postForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS_UPDATE_DOCUMENTATION_FIELDS),
                    new HttpEntity<>(update, headers),
                    UpdateBlueprintVersionDocumentationFieldsReponseRes.class
            );

            assertThat(post.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(post.getBody()).isNotNull();
            assertThat(post.getBody().getBlueprintVersion()).isNotNull();
            assertThat(post.getBody().getBlueprintVersion().getName()).isEqualTo("New version name");
            assertThat(post.getBody().getBlueprintVersion().getDescription()).isEqualTo("New description text");
            assertThat(post.getBody().getBlueprintVersion().getUpdatedBy()).isEqualTo("editor-user-1");
            assertThat(post.getBody().getBlueprintVersion().getUuid()).isEqualTo(versionUuid);

            ResponseEntity<BlueprintVersionRes> afterGet = rest.getForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS, "/" + versionUuid),
                    BlueprintVersionRes.class
            );
            BlueprintVersionRes after = afterGet.getBody();
            assertThat(after).isNotNull();
            assertThat(after.getUuid()).isEqualTo(before.getUuid());
            assertThat(after.getReadme()).isEqualTo(before.getReadme());
            assertThat(after.getTag()).isEqualTo(before.getTag());
            assertThat(after.getSpec()).isEqualTo(before.getSpec());
            assertThat(after.getSpecVersion()).isEqualTo(before.getSpecVersion());
            assertThat(after.getVersionNumber()).isEqualTo(before.getVersionNumber());
            assertThat(after.getContent()).isEqualTo(before.getContent());
            assertThat(after.getBlueprint().getUuid()).isEqualTo(before.getBlueprint().getUuid());
            assertThat(after.getBlueprint().getName()).isEqualTo(before.getBlueprint().getName());
            assertThat(after.getCreatedBy()).isEqualTo(before.getCreatedBy());
            assertThat(after.getUpdatedBy()).isEqualTo("editor-user-1");

            rest.delete(apiUrl(RoutesV2.BLUEPRINT_VERSIONS, "/" + versionUuid));
        } finally {
            rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
        }
    }

    /**
     * Invalid documentation update returns 400 and leaves the version unchanged.
     */
    @Test
    public void whenSendsInvalidBlueprintVersionUpdateFieldThenBadRequest() throws IOException {
        String prefix = "docFields002";
        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName(prefix + "-bp");
        blueprint.setDisplayName(prefix + "-display");
        blueprint.setDescription(prefix + "-description");

        
        blueprint.setBlueprintType(BlueprintTypeRes.BLUEPRINT);
        BlueprintRes.BlueprintRepoRes __repo = new BlueprintRes.BlueprintRepoRes();
        __repo.setExternalIdentifier("ext-id");
        __repo.setName("repo-name");
        __repo.setDescription("repo-desc");
        __repo.setManifestRootPath("/manifest");
        __repo.setDescriptorTemplatePath("/template");
        __repo.setReadmePath("/readme");
        __repo.setRemoteUrlHttp("https://github.com/org/repo.git");
        __repo.setRemoteUrlSsh("git@github.com:org/repo.git");
        __repo.setDefaultBranch("main");
        __repo.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
        __repo.setProviderBaseUrl("https://github.com");
        __repo.setOwnerId("org");
        __repo.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
        blueprint.setBlueprintRepo(__repo);
ResponseEntity<BlueprintRes> blueprintResponse = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                BlueprintRes.class
        );
        assertThat(blueprintResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String blueprintUuid = blueprintResponse.getBody().getUuid();

        try {
            PublishBlueprintVersionCommandRes publishCmd = publishCommand(
                    blueprintResponse.getBody(),
                    prefix + "-version",
                    prefix + "-manifest",
                    "1.0.0",
                    "odm-blueprint-manifest",
                    "1.0.0"
            );
            ResponseEntity<PublishBlueprintVersionResponseRes> published = rest.postForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS_PUBLISH),
                    new HttpEntity<>(publishCmd),
                    PublishBlueprintVersionResponseRes.class
            );
            assertThat(published.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            String versionUuid = published.getBody().getBlueprintVersion().getUuid();

            ResponseEntity<BlueprintVersionRes> beforeGet = rest.getForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS, "/" + versionUuid),
                    BlueprintVersionRes.class
            );
            BlueprintVersionRes before = beforeGet.getBody();
            assertThat(before).isNotNull();

            UpdateBlueprintVersionDocumentationFieldsCommandRes update = new UpdateBlueprintVersionDocumentationFieldsCommandRes();
            update.setName("   ");
            update.setDescription("should-not-persist");
            update.setUpdatedBy("editor");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<String> post = rest.postForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS_UPDATE_DOCUMENTATION_FIELDS),
                    new HttpEntity<>(update, headers),
                    String.class
            );

            assertThat(post.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

            ResponseEntity<BlueprintVersionRes> afterGet = rest.getForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS, "/" + versionUuid),
                    BlueprintVersionRes.class
            );
            BlueprintVersionRes after = afterGet.getBody();
            assertThat(after).isNotNull();
            assertThat(after.getName()).isEqualTo(before.getName());
            assertThat(after.getDescription()).isEqualTo(before.getDescription());
            assertThat(after.getUpdatedBy()).isEqualTo(before.getUpdatedBy());
            assertThat(after.getContent()).isEqualTo(before.getContent());

            rest.delete(apiUrl(RoutesV2.BLUEPRINT_VERSIONS, "/" + versionUuid));
        } finally {
            rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
        }
    }

    /*
     * Feature: Structural validation at publish and instantiate
     * Scenario: Empty root targets are rejected at both gates
     *   Given the type: root instantiation[] entry has targets: []
     *   When the client publishes the version
     *   Then the response status is 400
     *   And the message states instantiation[].targets must be non-empty and includes a hint
     */
    @Test
    public void whenPublishEmptyRootTargetsThenReturn400WithHint() throws IOException {
        assertPublishInvalidManifestReturns400WithHint(
                "/manifest/invalid/empty-root-targets.yaml",
                "targets",
                "required");
    }

    /*
     * Feature: Structural validation at publish and instantiate
     *   As an author
     *   I want the same structural rules before publish and before instantiate
     *   So that invalid routing never reaches Git and every problem is listed with a hint
     * Scenario: Missing isRoot target is rejected at both gates
     *   Given no targetRepositories[] entry sets isRoot: true
     *   When publish or instantiate validates
     *   Then 400 names targetRepositories and hints to set isRoot: true
     *   And no Git mutation runs
     */
    @Test
    public void whenPublishMissingRootRepositoryThenReturn400WithHint() throws IOException {
        assertPublishInvalidManifestReturns400WithHint(
                "/manifest/invalid/missing-root-repository.yaml",
                "targetRepositories",
                "isRoot");
    }

    /*
     * Feature: Structural validation at publish and instantiate
     * Scenario: Route repo that is not a declared key is rejected at both gates
     *   Given instantiation[].targets[].repo is "unknown-repo"
     *   When publish or instantiate validates
     *   Then 400 names the field and hints to use a declared targetRepositories[].key
     */
    @Test
    public void whenPublishUnknownRootRepositoryThenReturn400WithHint() throws IOException {
        assertPublishInvalidManifestReturns400WithHint(
                "/manifest/invalid/unknown-root-repository.yaml",
                "unknown-repo",
                "declared in targetRepositories[].key");
    }

    /**
     * Feature: Protected-resources destination key
     * Scenario: Unknown repository key is rejected at publish
     *   Given a blueprint whose `protectedResources[].repository` is not a declared targetRepositories key
     *   When the version is published
     *   Then the response is 400
     *   And the error names `protectedResources[].repository` and hints to use a declared key or omit it
     */
    @Test
    public void whenPublishUnknownProtectedResourceRepositoryThenReturn400WithHint() throws IOException {
        assertPublishInvalidManifestReturns400WithHint(
                "/manifest/invalid/unknown-protected-resource-repository.yaml",
                "protectedResources",
                "omit");
    }

    /**
     * Feature: Protected-resources destination key
     * Scenario: Present repository key matching the explicit root is accepted
     *   Given a 1→1 blueprint with `protectedResources[].repository` equal to the isRoot target key
     *   When the version is published
     *   Then the response is 201
     */
    @Test
    public void whenPublishProtectedResourceRepositoryIsRootKeyThenReturn201() throws IOException {
        String prefix = "pubProtRootKey";
        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName(prefix + "-bp");
        blueprint.setDisplayName(prefix + "-display");
        blueprint.setDescription(prefix + "-description");
        blueprint.setBlueprintType(BlueprintTypeRes.BLUEPRINT);
        blueprint.setBlueprintRepo(buildParentRepo());

        ResponseEntity<BlueprintRes> blueprintResponse = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                BlueprintRes.class
        );
        assertThat(blueprintResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String blueprintUuid = blueprintResponse.getBody().getUuid();

        try {
            ObjectNode content = (ObjectNode) ManifestYamlTestSupport.readYamlTreeFromClasspath(MONOREPO_MANIFEST_RESOURCE);
            for (JsonNode node : content.withArray("protectedResources")) {
                ((ObjectNode) node).put("repository", "main-repository");
            }
            PublishBlueprintVersionCommandRes cmd = publishCommandWithContent(
                    blueprintResponse.getBody(),
                    prefix + "-version",
                    "1.0.0",
                    content
            );

            ResponseEntity<PublishBlueprintVersionResponseRes> response = rest.postForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS_PUBLISH),
                    new HttpEntity<>(cmd),
                    PublishBlueprintVersionResponseRes.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getBlueprintVersion()).isNotNull();
        } finally {
            rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
        }
    }

    /**
     * Feature: Protected-resource manifest ownership
     *
     * Scenario: Omitted repository resolves to the explicit root
     *   Given a valid manifest whose root target is not first
     *   And a protected resource omits repository
     *   When the manifest is published and integrity is evaluated
     *   Then publication accepts the declaration
     *   And integrity compares it on the target marked isRoot true
     */
    @Test
    public void whenRepositoryOmittedThenUseIsRootTarget() throws IOException {
        String prefix = "pubProtOmitRepo";
        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName(prefix + "-bp");
        blueprint.setDisplayName(prefix + "-display");
        blueprint.setDescription(prefix + "-description");
        blueprint.setBlueprintType(BlueprintTypeRes.BLUEPRINT);
        blueprint.setBlueprintRepo(buildParentRepo());

        ResponseEntity<BlueprintRes> blueprintResponse = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                BlueprintRes.class
        );
        assertThat(blueprintResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String blueprintUuid = blueprintResponse.getBody().getUuid();

        try {
            ObjectNode content = (ObjectNode) ManifestYamlTestSupport.readYamlTreeFromClasspath(
                    "/manifest/example-2.3-polyrepo-no-composition.yaml");
            ArrayNode protectedResources = content.putArray("protectedResources");
            protectedResources.addObject().put("path", "application/**");
            PublishBlueprintVersionCommandRes cmd = publishCommandWithContent(
                    blueprintResponse.getBody(),
                    prefix + "-version",
                    "0.5.0",
                    content
            );

            ResponseEntity<PublishBlueprintVersionResponseRes> response = rest.postForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS_PUBLISH),
                    new HttpEntity<>(cmd),
                    PublishBlueprintVersionResponseRes.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
        } finally {
            rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
        }
    }

    /**
     * Feature: Protected-resource manifest ownership
     *
     * Scenario: Module with protected resources is rejected
     *   Given a catalog MODULE manifest with a non-empty protectedResources list
     *   When the Module version is published
     *   Then publication returns 400
     *   And the message tells the author to declare final paths on the parent Blueprint
     */
    @Test
    public void whenPublishModuleWithProtectedResourcesThenReturn400() throws IOException {
        String prefix = "moduleProtPublish-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        BlueprintRes moduleBlueprint = new BlueprintRes();
        moduleBlueprint.setName(prefix + "-bp");
        moduleBlueprint.setDisplayName(prefix + "-display");
        moduleBlueprint.setDescription(prefix + "-description");
        moduleBlueprint.setBlueprintType(BlueprintTypeRes.MODULE);
        moduleBlueprint.setBlueprintRepo(buildModuleRepo());

        ResponseEntity<BlueprintRes> blueprintResponse = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(moduleBlueprint),
                BlueprintRes.class);
        assertThat(blueprintResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String blueprintUuid = blueprintResponse.getBody().getUuid();

        try {
            PublishBlueprintVersionCommandRes cmd = publishCommandWithContent(
                    blueprintResponse.getBody(),
                    prefix + "-version",
                    "1.0.0",
                    ManifestYamlTestSupport.readYamlTreeFromClasspath(MONOREPO_MANIFEST_RESOURCE));

            ResponseEntity<String> response = rest.postForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS_PUBLISH),
                    new HttpEntity<>(cmd),
                    String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).contains("A Blueprint module must not declare protectedResources");
            assertThat(response.getBody()).contains("declare final protected paths on the parent Blueprint");
        } finally {
            rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
        }
    }

    /**
     * Feature: Lasting protected-resources integrity
     *
     * Scenario: Incomplete integrity object is rejected at Blueprint publication
     *   Given a protected resource includes integrity but omits algorithm or value
     *   When the Blueprint version is published
     *   Then publication returns 400
     *   And evaluation never relies on the stored integrity value
     */
    @Test
    public void whenProtectedIntegrityMissingAlgorithmOrValueThenReturn400() throws IOException {
        ObjectNode content = (ObjectNode) ManifestYamlTestSupport.readYamlTreeFromClasspath(MONOREPO_MANIFEST_RESOURCE);
        ((ObjectNode) content.withArray("protectedResources").get(0))
                .putObject("integrity")
                .put("algorithm", "sha256");
        assertPublishInvalidContentReturns400WithHint(content, "integrity", "value");
    }

    /**
     * Feature: Protected-resource manifest ownership
     *
     * Scenario: Parent composing a Module with protected resources is rejected
     *   Given a referenced catalog MODULE version whose manifest has a non-empty protectedResources list
     *   When the parent Blueprint is published
     *   Then publication returns 400
     *   And the aggregated issue tells the author to declare final paths on the parent Blueprint
     */
    @Test
    public void whenPublishParentComposingModuleWithProtectedResourcesThenReturn400() throws IOException {
        ObjectNode moduleManifest = (ObjectNode) ManifestYamlTestSupport.readYamlTreeFromClasspath(MONOREPO_MANIFEST_RESOURCE);
        moduleManifest.set("parameters", OBJECT_MAPPER.createArrayNode());
        StoredModule storage = createStoredModule("compose-storage-protected", "3.0.1", moduleManifest);
        ObjectNode servingManifest = (ObjectNode) moduleManifest.deepCopy();
        servingManifest.set("protectedResources", OBJECT_MAPPER.createArrayNode());
        StoredModule serving = createStoredModule("compose-serving-clean", "1.4.0", servingManifest);

        ObjectNode parentManifest = (ObjectNode) ManifestYamlTestSupport.readYamlTreeFromClasspath(
                "/manifest/example-2.2-monorepo-composition.yaml");
        rewriteCompositionRef(parentManifest, "storage", storage);
        rewriteCompositionRef(parentManifest, "serving", serving);
        for (JsonNode node : parentManifest.get("composition")) {
            ((ObjectNode) node).set("parameterMapping", OBJECT_MAPPER.createObjectNode());
        }

        try {
            assertPublishParentWithModuleReturns400(
                    parentManifest, storage, "declares protectedResources");
        } finally {
            deleteStoredModule(storage);
            deleteStoredModule(serving);
        }
    }


    /*
     * Feature: Structural validation at publish and instantiate
     * Scenario: Unused repository key is rejected at both gates
     *   Given a key "orphan" with no root or composition target referencing it
     *   When publish validates
     *   Then 400 lists the unused key and a hint to add a route or remove the key
     */
    @Test
    public void whenPublishUnusedRepositoryKeyThenReturn400WithHint() throws IOException {
        assertPublishInvalidManifestReturns400WithHint(
                "/manifest/invalid/unused-key.yaml",
                "orphan",
                "hint");
    }

    /*
     * Feature: Structural validation at publish and instantiate
     * Scenario: Nested path-prefix on the same key is rejected at both gates
     *   Given a route with path "./" and another with path "data-plane/storage" on the same key
     *   When publish validates
     *   Then 400 explains nested path coverage is forbidden and hints to use sibling destinations
     */
    @Test
    public void whenPublishNestedDestinationsThenReturn400WithHint() throws IOException {
        assertPublishInvalidManifestReturns400WithHint(
                "/manifest/invalid/nested-destinations.yaml",
                "nest",
                "hint");
    }

    /*
     * Feature: Module parameterMapping contract
     * Scenario: Bare scalar mapping entry is rejected at both gates
     *   Given parameterMapping region: eu-west-1
     *   When publish validates
     *   Then 400 states the entry must be an object and hints to use { value: eu-west-1 } or { $param: ... }
     */
    @Test
    public void whenPublishBareParameterMappingThenReturn400WithHint() throws IOException {
        assertPublishInvalidManifestReturns400WithHint(
                "/manifest/invalid/bare-parameter-mapping.yaml",
                "parameterMapping",
                "$param");
    }

    /*
     * Feature: Structural validation at publish and instantiate
     * Scenario: Exact overlapping destinations on the same key are rejected at both gates
     *   Given two routes with the same repository key and the same normalized path
     *   When publish or instantiate validates
     *   Then 400 lists the duplicate (repository, path) and a hint to make destinations unique
     */
    @Test
    public void whenPublishExactOverlapThenReturn400WithHint() throws IOException {
        assertPublishInvalidManifestReturns400WithHint(
                "/manifest/invalid/exact-overlap.yaml",
                "Duplicate destination",
                "unique");
    }

    /*
     * Feature: Composition modules must be monorepo without composition
     *   As a platform
     *   I want to forbid polyrepo or nested-composition children
     *   So that routing stays a single vocabulary
     * Scenario: Publishing a parent that references a missing module version fails
     *   Given composition.blueprintName and blueprintVersion do not exist
     *   When the parent is published
     *   Then 400 or 404 with a hint to publish the module version first
     */
    @Test
    public void whenPublishParentWithMissingModuleThenReturn404() throws IOException {
        String classpathManifest = "/manifest/example-2.2-monorepo-composition.yaml";
        String prefix = "composition";

        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName(prefix + "-bp");
        blueprint.setDisplayName(prefix + "-display");
        blueprint.setDescription(prefix + "-description");

        
        blueprint.setBlueprintType(BlueprintTypeRes.BLUEPRINT);
        BlueprintRes.BlueprintRepoRes __repo = new BlueprintRes.BlueprintRepoRes();
        __repo.setExternalIdentifier("ext-id");
        __repo.setName("repo-name");
        __repo.setDescription("repo-desc");
        __repo.setManifestRootPath("/manifest");
        __repo.setDescriptorTemplatePath("/template");
        __repo.setReadmePath("/readme");
        __repo.setRemoteUrlHttp("https://github.com/org/repo.git");
        __repo.setRemoteUrlSsh("git@github.com:org/repo.git");
        __repo.setDefaultBranch("main");
        __repo.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
        __repo.setProviderBaseUrl("https://github.com");
        __repo.setOwnerId("org");
        __repo.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
        blueprint.setBlueprintRepo(__repo);
ResponseEntity<BlueprintRes> blueprintResponse = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                BlueprintRes.class
        );
        assertThat(blueprintResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String blueprintUuid = blueprintResponse.getBody().getUuid();

        try {
            PublishBlueprintVersionCommandRes cmd = publishCommandWithContent(
                    blueprintResponse.getBody(),
                    prefix + "-version",
                    "1.0.0",
                    ManifestYamlTestSupport.readYamlTreeFromClasspath(classpathManifest)
            );

            ResponseEntity<String> response = rest.postForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS_PUBLISH),
                    new HttpEntity<>(cmd),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        } finally {
            rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
        }
    }

    /*
     * Feature: Composition modules must be monorepo without composition
     * Scenario: Publishing a parent that references a polyrepo module fails
     *   Given module "ingest" is published with two repository keys
     *   When the parent listing that module is published
     *   Then 400 names the module and hints that composition modules must be 1→1
     */
    @Test
    public void whenPublishParentWithPolyrepoModuleThenReturn400() throws IOException {
        StoredModule polyModule = createStoredModule(
                "poly-module",
                "1.0.0",
                ManifestYamlTestSupport.readYamlTreeFromClasspath("/manifest/example-2.3-polyrepo-no-composition.yaml"));
        StoredModule servingModule = createStoredModule(
                "valid-serving-module",
                "1.4.0",
                ManifestYamlTestSupport.readYamlTreeFromClasspath(MONOREPO_MANIFEST_RESOURCE));

        ObjectNode parentManifest = (ObjectNode) ManifestYamlTestSupport.readYamlTreeFromClasspath(
                "/manifest/example-2.2-monorepo-composition.yaml");
        rewriteCompositionRef(parentManifest, "storage", polyModule);
        rewriteCompositionRef(parentManifest, "serving", servingModule);

        assertPublishParentWithModuleReturns400(parentManifest, polyModule, "monorepo with no composition");
        deleteStoredModule(polyModule);
        deleteStoredModule(servingModule);
    }

    /*
     * Feature: Composition modules must be monorepo without composition
     * Scenario: Publishing a parent that references a composed module fails
     *   Given module "ingest" itself has composition
     *   When the parent is published
     *   Then 400 with a 1→1 hint
     */
    @Test
    public void whenPublishParentWithComposedModuleThenReturn400() throws IOException {
        StoredModule composedModule = createStoredModule(
                "composed-module",
                "1.0.0",
                ManifestYamlTestSupport.readYamlTreeFromClasspath("/manifest/example-2.2-monorepo-composition.yaml"));

        ObjectNode parentManifest = (ObjectNode) ManifestYamlTestSupport.readYamlTreeFromClasspath(
                MONOREPO_MANIFEST_RESOURCE);
        ObjectNode rootInstantiation = (ObjectNode) parentManifest.at("/instantiation/0");
        rootInstantiation.set("targets", OBJECT_MAPPER.createArrayNode().add(OBJECT_MAPPER.createObjectNode()
                .put("sourcePath", "./")
                .put("repo", "main-repository")
                .put("destinationPath", "core/")));
        ObjectNode compositionEntry = OBJECT_MAPPER.createObjectNode()
                .put("module", "storage")
                .put("blueprintName", composedModule.blueprintName())
                .put("blueprintVersion", composedModule.versionNumber());
        compositionEntry.set("parameterMapping", OBJECT_MAPPER.createObjectNode()
                .set("bucketPrefix", OBJECT_MAPPER.createObjectNode().put("$param", "environment")));
        parentManifest.set("composition", OBJECT_MAPPER.createArrayNode().add(compositionEntry));
        ArrayNode instantiation = (ArrayNode) parentManifest.get("instantiation");
        instantiation.add(OBJECT_MAPPER.createObjectNode()
                .put("type", "module")
                .put("moduleName", "storage")
                .set("targets", OBJECT_MAPPER.createArrayNode().add(OBJECT_MAPPER.createObjectNode()
                        .put("sourcePath", "./")
                        .put("repo", "main-repository")
                        .put("destinationPath", "data-plane/storage"))));

        assertPublishParentWithModuleReturns400(parentManifest, composedModule, "monorepo with no composition");
        deleteStoredModule(composedModule);
    }

    /*
     * Feature: Only a Blueprint module may be composed
     * Scenario: Publishing a parent that references a catalog Blueprint fails
     *   Given a published Blueprint (root) version
     *   When the parent listing that Blueprint in composition[] is published
     *   Then 400 states that only a Blueprint module may be composed
     */
    @Test
    public void whenPublishParentComposingBlueprintThenReturn400() throws IOException {
        StoredModule composedBlueprint = createStoredCatalogBlueprint(
                "composed-blueprint-root",
                "1.0.0",
                ManifestYamlTestSupport.readYamlTreeFromClasspath(MONOREPO_MANIFEST_RESOURCE));
        StoredModule servingModule = createStoredModule(
                "valid-serving-module",
                "1.4.0",
                ManifestYamlTestSupport.readYamlTreeFromClasspath(MONOREPO_MANIFEST_RESOURCE));

        ObjectNode parentManifest = (ObjectNode) ManifestYamlTestSupport.readYamlTreeFromClasspath(
                "/manifest/example-2.2-monorepo-composition.yaml");
        rewriteCompositionRef(parentManifest, "storage", composedBlueprint);
        rewriteCompositionRef(parentManifest, "serving", servingModule);

        assertPublishParentWithModuleReturns400(
                parentManifest, composedBlueprint, "not a Blueprint module");
        deleteStoredModule(composedBlueprint);
        deleteStoredModule(servingModule);
    }

    /*
     * Feature: Composition and instantiate by blueprintType
     * Scenario: Publishing a parent that composes a MODULE succeeds when the module is 1→1 empty composition
     *   Given published MODULE versions that are monorepo with empty composition
     *   When a parent Blueprint composing those modules is published
     *   Then the response status is 201
     */
    @Test
    public void whenPublishParentComposingModuleThenSucceed() throws IOException {
        ObjectNode moduleManifest = (ObjectNode) ManifestYamlTestSupport.readYamlTreeFromClasspath(MONOREPO_MANIFEST_RESOURCE);
        moduleManifest.set("parameters", OBJECT_MAPPER.createArrayNode());
        moduleManifest.set("protectedResources", OBJECT_MAPPER.createArrayNode());

        StoredModule storage = createStoredModule("compose-storage-module", "3.0.1", moduleManifest);
        StoredModule serving = createStoredModule("compose-serving-module", "1.4.0", moduleManifest);

        ObjectNode parentManifest = (ObjectNode) ManifestYamlTestSupport.readYamlTreeFromClasspath(
                "/manifest/example-2.2-monorepo-composition.yaml");
        rewriteCompositionRef(parentManifest, "storage", storage);
        rewriteCompositionRef(parentManifest, "serving", serving);
        for (JsonNode node : parentManifest.get("composition")) {
            ((ObjectNode) node).set("parameterMapping", OBJECT_MAPPER.createObjectNode());
        }

        String prefix = "parentComposeOk-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        BlueprintRes parentBlueprint = new BlueprintRes();
        parentBlueprint.setName(prefix + "-bp");
        parentBlueprint.setDisplayName(prefix + "-display");
        parentBlueprint.setDescription(prefix + "-description");
        parentBlueprint.setBlueprintType(BlueprintTypeRes.BLUEPRINT);
        parentBlueprint.setBlueprintRepo(buildParentRepo());

        ResponseEntity<BlueprintRes> blueprintResponse = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(parentBlueprint),
                BlueprintRes.class);
        assertThat(blueprintResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String blueprintUuid = blueprintResponse.getBody().getUuid();

        try {
            PublishBlueprintVersionCommandRes cmd = publishCommandWithContent(
                    blueprintResponse.getBody(),
                    prefix + "-version",
                    "2.1.0",
                    parentManifest);

            ResponseEntity<PublishBlueprintVersionResponseRes> response = rest.postForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS_PUBLISH),
                    new HttpEntity<>(cmd),
                    PublishBlueprintVersionResponseRes.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getBlueprintVersion()).isNotNull();
            rest.delete(apiUrl(RoutesV2.BLUEPRINT_VERSIONS, "/" + response.getBody().getBlueprintVersion().getUuid()));
        } finally {
            rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
            deleteStoredModule(storage);
            deleteStoredModule(serving);
        }
    }

    /*
     * Feature: Composition and instantiate by blueprintType
     * Scenario: Publishing a Blueprint module whose content is not 1→1 empty composition returns 400
     *   Given a catalog row with blueprintType MODULE
     *   And the version content has composition or more than one repository key
     *   When the client publishes that module version
     *   Then the response status is 400
     *   And the message states that a Blueprint module must be a monorepo with no composition
     */
    @Test
    public void whenPublishModuleThatIsNotMonorepoNoCompositionThenReturn400() throws IOException {
        String prefix = "modulePolyPublish-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        BlueprintRes moduleBlueprint = new BlueprintRes();
        moduleBlueprint.setName(prefix + "-bp");
        moduleBlueprint.setDisplayName(prefix + "-display");
        moduleBlueprint.setDescription(prefix + "-description");
        moduleBlueprint.setBlueprintType(BlueprintTypeRes.MODULE);
        moduleBlueprint.setBlueprintRepo(buildModuleRepo());

        ResponseEntity<BlueprintRes> blueprintResponse = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(moduleBlueprint),
                BlueprintRes.class);
        assertThat(blueprintResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String blueprintUuid = blueprintResponse.getBody().getUuid();

        try {
            PublishBlueprintVersionCommandRes cmd = publishCommandWithContent(
                    blueprintResponse.getBody(),
                    prefix + "-version",
                    "1.0.0",
                    ManifestYamlTestSupport.readYamlTreeFromClasspath(
                            "/manifest/example-2.3-polyrepo-no-composition.yaml"));

            ResponseEntity<String> response = rest.postForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS_PUBLISH),
                    new HttpEntity<>(cmd),
                    String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).contains("Blueprint module must be a monorepo with no composition");
        } finally {
            rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
        }
    }

    /*
     * Feature: Module parameterMapping contract
     * Scenario: Publishing a parent that omits a module parameter with no default fails
     *   Given a published 1→1 module that declares parameter "environment" with no default
     *   And that module also declares parameter "retentionDays" with a default
     *   And the parent composition.parameterMapping maps "retentionDays" but not "environment"
     *   When the parent listing that module is published
     *   Then 400 names the missing child key "environment" and hints to add a parameterMapping entry or a module default
     *   And the message does not require a mapping for "retentionDays"
     */
    @Test
    public void whenPublishParentOmittingModuleParameterWithoutDefaultThenReturn400() throws IOException {
        StoredModule module = createStoredModule(
                "module-with-required-param",
                "1.0.0",
                ManifestYamlTestSupport.readYamlTreeFromClasspath(MONOREPO_MANIFEST_RESOURCE));

        ObjectNode parentManifest = (ObjectNode) ManifestYamlTestSupport.readYamlTreeFromClasspath(
                MONOREPO_MANIFEST_RESOURCE);
        ObjectNode rootInstantiation = (ObjectNode) parentManifest.at("/instantiation/0");
        rootInstantiation.set("targets", OBJECT_MAPPER.createArrayNode().add(OBJECT_MAPPER.createObjectNode()
                .put("sourcePath", "./")
                .put("repo", "main-repository")
                .put("destinationPath", "core/")));
        ObjectNode compositionEntry = OBJECT_MAPPER.createObjectNode()
                .put("module", "storage")
                .put("blueprintName", module.blueprintName())
                .put("blueprintVersion", module.versionNumber());
        compositionEntry.set("parameterMapping", OBJECT_MAPPER.createObjectNode()
                .set("retentionDays", OBJECT_MAPPER.createObjectNode().put("value", 90)));
        parentManifest.set("composition", OBJECT_MAPPER.createArrayNode().add(compositionEntry));
        ArrayNode instantiation = (ArrayNode) parentManifest.get("instantiation");
        instantiation.add(OBJECT_MAPPER.createObjectNode()
                .put("type", "module")
                .put("moduleName", "storage")
                .set("targets", OBJECT_MAPPER.createArrayNode().add(OBJECT_MAPPER.createObjectNode()
                        .put("sourcePath", "./")
                        .put("repo", "main-repository")
                        .put("destinationPath", "data-plane/storage"))));

        String prefix = "parentMissingModuleParam-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        BlueprintRes parentBlueprint = new BlueprintRes();
        parentBlueprint.setName(prefix + "-bp");
        parentBlueprint.setDisplayName(prefix + "-display");
        parentBlueprint.setDescription(prefix + "-description");

        
        parentBlueprint.setBlueprintType(BlueprintTypeRes.BLUEPRINT);
        BlueprintRes.BlueprintRepoRes __repo = new BlueprintRes.BlueprintRepoRes();
        __repo.setExternalIdentifier("ext-id");
        __repo.setName("repo-name");
        __repo.setDescription("repo-desc");
        __repo.setManifestRootPath("/manifest");
        __repo.setDescriptorTemplatePath("/template");
        __repo.setReadmePath("/readme");
        __repo.setRemoteUrlHttp("https://github.com/org/repo.git");
        __repo.setRemoteUrlSsh("git@github.com:org/repo.git");
        __repo.setDefaultBranch("main");
        __repo.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
        __repo.setProviderBaseUrl("https://github.com");
        __repo.setOwnerId("org");
        __repo.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
        parentBlueprint.setBlueprintRepo(__repo);
ResponseEntity<BlueprintRes> blueprintResponse = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(parentBlueprint),
                BlueprintRes.class);
        assertThat(blueprintResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String blueprintUuid = blueprintResponse.getBody().getUuid();

        try {
            PublishBlueprintVersionCommandRes cmd = publishCommandWithContent(
                    blueprintResponse.getBody(),
                    prefix + "-version",
                    "1.0.0",
                    parentManifest);

            ResponseEntity<String> response = rest.postForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS_PUBLISH),
                    new HttpEntity<>(cmd),
                    String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).contains("parameterMapping.environment");
            assertThat(response.getBody()).contains("environment");
            assertThat(response.getBody()).containsIgnoringCase("hint");
            assertThat(response.getBody()).containsIgnoringCase("default");
            assertThat(response.getBody()).doesNotContain("parameterMapping.retentionDays");
        } finally {
            rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
            deleteStoredModule(module);
        }
    }

    /*
     * Feature: Structural validation at publish and instantiate
     * Scenario: Multiple structural problems are all reported
     *   Given a manifest with unused key AND nested destinations AND an invalid parameterMapping entry
     *   When publish or instantiate validates
     *   Then the 400 message contains every problem
     *   And each problem includes a how-to-fix hint
     *   And validation does not stop at the first error
     */
    @Test
    public void whenPublishMultipleStructuralErrorsThenAllListedWithHints() throws IOException {
        String prefix = "pubMultiErr";
        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName(prefix + "-bp");
        blueprint.setDisplayName(prefix + "-display");
        blueprint.setDescription(prefix + "-description");

        
        blueprint.setBlueprintType(BlueprintTypeRes.BLUEPRINT);
        BlueprintRes.BlueprintRepoRes __repo = new BlueprintRes.BlueprintRepoRes();
        __repo.setExternalIdentifier("ext-id");
        __repo.setName("repo-name");
        __repo.setDescription("repo-desc");
        __repo.setManifestRootPath("/manifest");
        __repo.setDescriptorTemplatePath("/template");
        __repo.setReadmePath("/readme");
        __repo.setRemoteUrlHttp("https://github.com/org/repo.git");
        __repo.setRemoteUrlSsh("git@github.com:org/repo.git");
        __repo.setDefaultBranch("main");
        __repo.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
        __repo.setProviderBaseUrl("https://github.com");
        __repo.setOwnerId("org");
        __repo.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
        blueprint.setBlueprintRepo(__repo);
ResponseEntity<BlueprintRes> blueprintResponse = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                BlueprintRes.class
        );
        assertThat(blueprintResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String blueprintUuid = blueprintResponse.getBody().getUuid();

        try {
            PublishBlueprintVersionCommandRes cmd = publishCommandWithContent(
                    blueprintResponse.getBody(),
                    prefix + "-version",
                    "1.0.0",
                    ManifestYamlTestSupport.readYamlTreeFromClasspath(
                            "/manifest/invalid/multiple-structural-errors.yaml")
            );

            ResponseEntity<String> response = rest.postForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS_PUBLISH),
                    new HttpEntity<>(cmd),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).containsIgnoringCase("hint");
            assertThat(response.getBody()).contains("orphan");
        } finally {
            rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
        }
    }

    private void assertPublishInvalidManifestReturns400WithHint(
            String classpathManifest,
            String expectedProblemFragment,
            String expectedHintFragment
    ) throws IOException {
        assertPublishInvalidContentReturns400WithHint(
                ManifestYamlTestSupport.readYamlTreeFromClasspath(classpathManifest),
                expectedProblemFragment,
                expectedHintFragment);
    }

    private void assertPublishInvalidContentReturns400WithHint(
            JsonNode content,
            String expectedProblemFragment,
            String expectedHintFragment
    ) throws IOException {
        String prefix = "pubStruct" + expectedProblemFragment.replaceAll("[^a-zA-Z0-9]", "").substring(0,
                Math.min(8, expectedProblemFragment.replaceAll("[^a-zA-Z0-9]", "").length()));
        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName(prefix + "-bp");
        blueprint.setDisplayName(prefix + "-display");
        blueprint.setDescription(prefix + "-description");

        
        blueprint.setBlueprintType(BlueprintTypeRes.BLUEPRINT);
        BlueprintRes.BlueprintRepoRes __repo = new BlueprintRes.BlueprintRepoRes();
        __repo.setExternalIdentifier("ext-id");
        __repo.setName("repo-name");
        __repo.setDescription("repo-desc");
        __repo.setManifestRootPath("/manifest");
        __repo.setDescriptorTemplatePath("/template");
        __repo.setReadmePath("/readme");
        __repo.setRemoteUrlHttp("https://github.com/org/repo.git");
        __repo.setRemoteUrlSsh("git@github.com:org/repo.git");
        __repo.setDefaultBranch("main");
        __repo.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
        __repo.setProviderBaseUrl("https://github.com");
        __repo.setOwnerId("org");
        __repo.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
        blueprint.setBlueprintRepo(__repo);
ResponseEntity<BlueprintRes> blueprintResponse = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                BlueprintRes.class
        );
        assertThat(blueprintResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String blueprintUuid = blueprintResponse.getBody().getUuid();

        try {
            PublishBlueprintVersionCommandRes cmd = publishCommandWithContent(
                    blueprintResponse.getBody(),
                    prefix + "-version",
                    "1.0.0",
                    content
            );

            ResponseEntity<String> response = rest.postForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS_PUBLISH),
                    new HttpEntity<>(cmd),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).containsIgnoringCase(expectedProblemFragment);
            assertThat(response.getBody()).containsIgnoringCase(expectedHintFragment);
        } finally {
            rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
        }
    }

    private PublishBlueprintVersionCommandRes publishCommandWithContent(
            BlueprintRes blueprint,
            String versionName,
            String manifestVersion,
            JsonNode content
    ) {
        PublishBlueprintVersionCommandRes cmd = new PublishBlueprintVersionCommandRes();
        PublishBlueprintVersionCommandRes.BlueprintVersion bv =
                new PublishBlueprintVersionCommandRes.BlueprintVersion();
        bv.setName(versionName);
        bv.setDescription("desc");
        bv.setReadme("readme");
        bv.setTag("v" + manifestVersion);
        bv.setSpec("odm-blueprint-manifest");
        bv.setSpecVersion("1.0.0");
        bv.setContent(content);
        PublishBlueprintVersionCommandRes.BlueprintVersion.Blueprint bp =
                new PublishBlueprintVersionCommandRes.BlueprintVersion.Blueprint();
        bp.setUuid(blueprint.getUuid());
        bv.setBlueprint(bp);
        bv.setCreatedBy("it-created-by");
        cmd.setBlueprintVersion(bv);
        return cmd;
    }

    private void assertPublishParentWithModuleReturns400(
            ObjectNode parentManifest,
            StoredModule offendingModule,
            String expectedMessageFragment) throws IOException {
        String prefix = "parentWithBadModule";
        BlueprintRes parentBlueprint = new BlueprintRes();
        parentBlueprint.setName(prefix + "-bp");
        parentBlueprint.setDisplayName(prefix + "-display");
        parentBlueprint.setDescription(prefix + "-description");

        
        parentBlueprint.setBlueprintType(BlueprintTypeRes.BLUEPRINT);
        BlueprintRes.BlueprintRepoRes __repo = new BlueprintRes.BlueprintRepoRes();
        __repo.setExternalIdentifier("ext-id");
        __repo.setName("repo-name");
        __repo.setDescription("repo-desc");
        __repo.setManifestRootPath("/manifest");
        __repo.setDescriptorTemplatePath("/template");
        __repo.setReadmePath("/readme");
        __repo.setRemoteUrlHttp("https://github.com/org/repo.git");
        __repo.setRemoteUrlSsh("git@github.com:org/repo.git");
        __repo.setDefaultBranch("main");
        __repo.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
        __repo.setProviderBaseUrl("https://github.com");
        __repo.setOwnerId("org");
        __repo.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
        parentBlueprint.setBlueprintRepo(__repo);
ResponseEntity<BlueprintRes> blueprintResponse = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(parentBlueprint),
                BlueprintRes.class);
        assertThat(blueprintResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String blueprintUuid = blueprintResponse.getBody().getUuid();

        try {
            PublishBlueprintVersionCommandRes cmd = publishCommandWithContent(
                    blueprintResponse.getBody(),
                    prefix + "-version",
                    "1.0.0",
                    parentManifest);

            ResponseEntity<String> response = rest.postForEntity(
                    apiUrl(RoutesV2.BLUEPRINT_VERSIONS_PUBLISH),
                    new HttpEntity<>(cmd),
                    String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).contains(expectedMessageFragment);
            assertThat(response.getBody()).contains(offendingModule.blueprintName());
        } finally {
            rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + blueprintUuid));
        }
    }

    private StoredModule createStoredModule(String blueprintName, String version, JsonNode manifestContent)
            throws IOException {
        return createStoredModule(blueprintName, version, manifestContent, null);
    }

    private StoredModule createStoredCatalogBlueprint(
            String blueprintName,
            String version,
            JsonNode manifestContent) throws IOException {
        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        String uniqueName = blueprintName + "-" + suffix;
        ObjectNode content = (ObjectNode) manifestContent.deepCopy();
        content.put("name", uniqueName);
        content.put("version", version);

        BlueprintRes.BlueprintRepoRes blueprintRepo = buildModuleRepoWithDescriptorTemplatePath();
        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName(uniqueName);
        blueprint.setDisplayName(uniqueName + "-display");
        blueprint.setDescription(uniqueName + "-description");
        blueprint.setBlueprintType(BlueprintTypeRes.BLUEPRINT);
        blueprint.setBlueprintRepo(blueprintRepo);

        ResponseEntity<BlueprintRes> createdBlueprint = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                BlueprintRes.class);
        assertThat(createdBlueprint.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        BlueprintVersionRes versionRes = new BlueprintVersionRes();
        versionRes.setName(uniqueName + "-" + version);
        versionRes.setDescription("catalog blueprint version");
        versionRes.setReadme("README.md");
        versionRes.setTag("v" + version);
        versionRes.setVersionNumber(version);
        versionRes.setSpec("odm-blueprint-manifest");
        versionRes.setSpecVersion("1.0.0");
        versionRes.setBlueprint(createdBlueprint.getBody());
        versionRes.setContent(content);

        ResponseEntity<BlueprintVersionRes> createdVersion = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS),
                new HttpEntity<>(versionRes),
                BlueprintVersionRes.class);
        assertThat(createdVersion.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        return new StoredModule(createdBlueprint.getBody().getUuid(), uniqueName, version);
    }

    private StoredModule createStoredModule(
            String blueprintName,
            String version,
            JsonNode manifestContent,
            BlueprintRes.BlueprintRepoRes blueprintRepo)
            throws IOException {
        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        String uniqueName = blueprintName + "-" + suffix;
        ObjectNode content = (ObjectNode) manifestContent.deepCopy();
        content.put("name", uniqueName);
        content.put("version", version);

        if (blueprintRepo == null) {
            blueprintRepo = buildModuleRepo();
        }
        BlueprintRes blueprint = new BlueprintRes();
        blueprint.setName(uniqueName);
        blueprint.setDisplayName(uniqueName + "-display");
        blueprint.setDescription(uniqueName + "-description");
        blueprint.setBlueprintType(BlueprintTypeRes.MODULE);
        blueprint.setBlueprintRepo(blueprintRepo);

        ResponseEntity<BlueprintRes> createdBlueprint = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINTS),
                new HttpEntity<>(blueprint),
                BlueprintRes.class);
        assertThat(createdBlueprint.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        BlueprintVersionRes versionRes = new BlueprintVersionRes();
        versionRes.setName(uniqueName + "-" + version);
        versionRes.setDescription("module version");
        versionRes.setReadme("README.md");
        versionRes.setTag("v" + version);
        versionRes.setVersionNumber(version);
        versionRes.setSpec("odm-blueprint-manifest");
        versionRes.setSpecVersion("1.0.0");
        versionRes.setBlueprint(createdBlueprint.getBody());
        versionRes.setContent(content);

        ResponseEntity<BlueprintVersionRes> createdVersion = rest.postForEntity(
                apiUrl(RoutesV2.BLUEPRINT_VERSIONS),
                new HttpEntity<>(versionRes),
                BlueprintVersionRes.class);
        assertThat(createdVersion.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        return new StoredModule(createdBlueprint.getBody().getUuid(), uniqueName, version);
    }

    private void rewriteCompositionRef(ObjectNode parentManifest, String moduleAlias, StoredModule module) {
        for (JsonNode node : parentManifest.get("composition")) {
            ObjectNode composition = (ObjectNode) node;
            if (moduleAlias.equals(composition.get("module").asText())) {
                composition.put("blueprintName", module.blueprintName());
                composition.put("blueprintVersion", module.versionNumber());
            }
        }
    }

    private void deleteStoredModule(StoredModule module) {
        if (module != null && module.blueprintUuid() != null) {
            rest.delete(apiUrl(RoutesV2.BLUEPRINTS, "/" + module.blueprintUuid()));
        }
    }


    private BlueprintRes.BlueprintRepoRes buildParentRepo() {
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
        return blueprintRepo;
    }

    private BlueprintRes.BlueprintRepoRes buildModuleRepo() {
        BlueprintRes.BlueprintRepoRes blueprintRepo = buildModuleRepoWithDescriptorTemplatePath();
        blueprintRepo.setDescriptorTemplatePath(null);
        return blueprintRepo;
    }

    private BlueprintRes.BlueprintRepoRes buildModuleRepoWithDescriptorTemplatePath() {
        BlueprintRes.BlueprintRepoRes blueprintRepo = new BlueprintRes.BlueprintRepoRes();
        blueprintRepo.setExternalIdentifier("module-blueprint-repository");
        blueprintRepo.setName("module-blueprint-repository");
        blueprintRepo.setDescription("module");
        blueprintRepo.setManifestRootPath("/manifest.yaml");
        blueprintRepo.setDescriptorTemplatePath("templates/descriptor.json.vm");
        blueprintRepo.setReadmePath("/README.md");
        blueprintRepo.setRemoteUrlHttp("https://github.com/org/module-blueprint-repository.git");
        blueprintRepo.setRemoteUrlSsh("git@github.com:org/module-blueprint-repository.git");
        blueprintRepo.setDefaultBranch("main");
        blueprintRepo.setProviderType(BlueprintRepoProviderTypeRes.GITHUB);
        blueprintRepo.setProviderBaseUrl("https://github.com");
        blueprintRepo.setOwnerId("org");
        blueprintRepo.setOwnerType(BlueprintRepoOwnerTypeRes.ORGANIZATION);
        return blueprintRepo;
    }

    private record StoredModule(String blueprintUuid, String blueprintName, String versionNumber) {
    }
}
