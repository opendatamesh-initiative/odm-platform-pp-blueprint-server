package org.opendatamesh.platform.pp.blueprint.rest.v2.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.opendatamesh.platform.pp.blueprint.rest.v2.BlueprintApplicationIT;
import org.opendatamesh.platform.pp.blueprint.rest.v2.RoutesV2;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.label.LabelRes;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link LabelController}.
 * Scenarios trace to {@code spdd/analysis/BDMD-5154-202609040922-[Analysis]-blueprint-categorization-tags.md}
 * and {@code spdd/prompt/BDMD-5154-202609041545-[Feat]-api-blueprint-categorization-labels.md}.
 */
public class LabelControllerIT extends BlueprintApplicationIT {

    /**
     * Feature: Create label
     * Given the API is available
     * And a valid label payload is prepared
     * When the client sends POST to "/api/v2/pp/blueprint/labels"
     * Then the response status is 201
     * And a subsequent GET by the returned uuid returns the same logical data
     */
    @Test
    public void whenCreateLabelThenReturnCreatedLabel() {
        String namePrefix = "whenCreateLabelThenReturnCreatedLabel";

        LabelRes label = newLabel(namePrefix, "#0E8A16");

        ResponseEntity<LabelRes> response = rest.postForEntity(
                apiUrl(RoutesV2.LABELS),
                new HttpEntity<>(label),
                LabelRes.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getUuid()).isNotNull();
        assertThat(response.getBody().getName()).isEqualTo(label.getName());
        assertThat(response.getBody().getDescription()).isEqualTo(label.getDescription());
        assertThat(response.getBody().getColor()).isEqualTo("#0E8A16");

        String labelUuid = response.getBody().getUuid();

        ResponseEntity<LabelRes> getResponse = rest.getForEntity(
                apiUrl(RoutesV2.LABELS, "/" + labelUuid),
                LabelRes.class
        );
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().getName()).isEqualTo(label.getName());

        rest.delete(apiUrl(RoutesV2.LABELS, "/" + labelUuid));
    }

    /**
     * Feature: Get label by id
     * Given a label exists with a known uuid
     * When the client sends GET to "/api/v2/pp/blueprint/labels/{uuid}"
     * Then the response status is 200
     */
    @Test
    public void whenGetLabelByIdThenReturnLabel() {
        String namePrefix = "whenGetLabelByIdThenReturnLabel";

        ResponseEntity<LabelRes> created = rest.postForEntity(
                apiUrl(RoutesV2.LABELS),
                new HttpEntity<>(newLabel(namePrefix, "#FF0000")),
                LabelRes.class
        );
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String labelUuid = created.getBody().getUuid();

        ResponseEntity<LabelRes> response = rest.getForEntity(
                apiUrl(RoutesV2.LABELS, "/" + labelUuid),
                LabelRes.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getUuid()).isEqualTo(labelUuid);
        assertThat(response.getBody().getName()).isEqualTo(namePrefix);

        rest.delete(apiUrl(RoutesV2.LABELS, "/" + labelUuid));
    }

    /**
     * Feature: Search labels
     * Given labels exist with distinguishable names
     * When the client sends GET with a name filter
     * Then matching is case-insensitive substring (LIKE): "tes" finds "Test"
     */
    @Test
    public void whenSearchLabelsThenReturnFilteredResults() {
        String namePrefix = "whenSearchLabelsThenReturnFilteredResults";

        ResponseEntity<LabelRes> first = rest.postForEntity(
                apiUrl(RoutesV2.LABELS),
                new HttpEntity<>(newLabel(namePrefix + "-Test", "#111111")),
                LabelRes.class
        );
        ResponseEntity<LabelRes> second = rest.postForEntity(
                apiUrl(RoutesV2.LABELS),
                new HttpEntity<>(newLabel(namePrefix + "-other", "#222222")),
                LabelRes.class
        );
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String firstUuid = first.getBody().getUuid();
        String secondUuid = second.getBody().getUuid();

        try {
            ResponseEntity<JsonNode> exactish = rest.getForEntity(
                    apiUrl(RoutesV2.LABELS, "?name=" + namePrefix + "-Test"),
                    JsonNode.class
            );
            assertThat(exactish.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(exactish.getBody()).isNotNull();
            JsonNode exactContent = exactish.getBody().get("content");
            assertThat(exactContent.isArray()).isTrue();
            assertThat(exactContent.size()).isEqualTo(1);
            assertThat(exactContent.get(0).get("name").asText()).isEqualTo(namePrefix + "-Test");

            ResponseEntity<JsonNode> substring = rest.getForEntity(
                    apiUrl(RoutesV2.LABELS, "?name=tes"),
                    JsonNode.class
            );
            assertThat(substring.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(substring.getBody()).isNotNull();
            JsonNode substringContent = substring.getBody().get("content");
            assertThat(substringContent.isArray()).isTrue();
            assertThat(substringContent.size()).isGreaterThanOrEqualTo(1);
            boolean foundTest = false;
            for (JsonNode node : substringContent) {
                if ((namePrefix + "-Test").equals(node.get("name").asText())) {
                    foundTest = true;
                }
                assertThat(node.get("name").asText().toLowerCase()).contains("tes");
            }
            assertThat(foundTest).isTrue();
        } finally {
            rest.delete(apiUrl(RoutesV2.LABELS, "/" + firstUuid));
            rest.delete(apiUrl(RoutesV2.LABELS, "/" + secondUuid));
        }
    }

    /**
     * Feature: Update label
     * Given an existing label
     * When the client sends PUT with updated fields
     * Then the response status is 200 and GET reflects the update
     */
    @Test
    public void whenUpdateLabelThenReturnUpdatedLabel() {
        String namePrefix = "whenUpdateLabelThenReturnUpdatedLabel";

        ResponseEntity<LabelRes> created = rest.postForEntity(
                apiUrl(RoutesV2.LABELS),
                new HttpEntity<>(newLabel(namePrefix, "#000000")),
                LabelRes.class
        );
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String labelUuid = created.getBody().getUuid();

        LabelRes update = newLabel(namePrefix + "-updated", "#ABCDEF");
        update.setUuid(labelUuid);
        update.setDescription("updated-description");

        ResponseEntity<LabelRes> response = rest.exchange(
                apiUrl(RoutesV2.LABELS, "/" + labelUuid),
                HttpMethod.PUT,
                new HttpEntity<>(update),
                LabelRes.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getName()).isEqualTo(namePrefix + "-updated");
        assertThat(response.getBody().getColor()).isEqualTo("#ABCDEF");
        assertThat(response.getBody().getDescription()).isEqualTo("updated-description");

        ResponseEntity<LabelRes> getResponse = rest.getForEntity(
                apiUrl(RoutesV2.LABELS, "/" + labelUuid),
                LabelRes.class
        );
        assertThat(getResponse.getBody().getName()).isEqualTo(namePrefix + "-updated");

        rest.delete(apiUrl(RoutesV2.LABELS, "/" + labelUuid));
    }

    /**
     * Feature: Delete label
     * Given an existing label
     * When the client sends DELETE
     * Then the response status is 204 and GET returns 404
     */
    @Test
    public void whenDeleteLabelThenReturnNoContentAndLabelIsDeleted() {
        String namePrefix = "whenDeleteLabelThenReturnNoContentAndLabelIsDeleted";

        ResponseEntity<LabelRes> created = rest.postForEntity(
                apiUrl(RoutesV2.LABELS),
                new HttpEntity<>(newLabel(namePrefix, "#123456")),
                LabelRes.class
        );
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String labelUuid = created.getBody().getUuid();

        ResponseEntity<Void> response = rest.exchange(
                apiUrl(RoutesV2.LABELS, "/" + labelUuid),
                HttpMethod.DELETE,
                null,
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        ResponseEntity<String> getResponse = rest.getForEntity(
                apiUrl(RoutesV2.LABELS, "/" + labelUuid),
                String.class
        );
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * Feature: Create label — duplicate name conflict
     * Given a label already exists with a name
     * When the client sends POST with the same name (case-insensitive)
     * Then the response status is 409
     */
    @Test
    public void whenCreateLabelWithDuplicateNameThenReturnConflict() {
        String namePrefix = "whenCreateLabelWithDuplicateNameThenReturnConflict";

        ResponseEntity<LabelRes> first = rest.postForEntity(
                apiUrl(RoutesV2.LABELS),
                new HttpEntity<>(newLabel(namePrefix, "#111111")),
                LabelRes.class
        );
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String firstUuid = first.getBody().getUuid();

        LabelRes duplicate = newLabel(namePrefix.toUpperCase(), "#222222");
        ResponseEntity<String> response = rest.postForEntity(
                apiUrl(RoutesV2.LABELS),
                new HttpEntity<>(duplicate),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        rest.delete(apiUrl(RoutesV2.LABELS, "/" + firstUuid));
    }

    /**
     * Feature: Create label — illegal name characters
     * Given a label payload with characters outside the simple set
     * When the client sends POST
     * Then the response status is 400
     */
    @Test
    public void whenCreateLabelWithIllegalNameCharactersThenReturnBadRequest() {
        LabelRes invalid = newLabel("illegal name", "#FF0000");

        ResponseEntity<String> response = rest.postForEntity(
                apiUrl(RoutesV2.LABELS),
                new HttpEntity<>(invalid),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * Feature: Create label — invalid color
     * Given a label payload with a color that is not #RRGGBB
     * When the client sends POST
     * Then the response status is 400
     */
    @Test
    public void whenCreateLabelWithInvalidColorThenReturnBadRequest() {
        LabelRes invalid = newLabel("whenCreateLabelWithInvalidColorThenReturnBadRequest", "red");

        ResponseEntity<String> response = rest.postForEntity(
                apiUrl(RoutesV2.LABELS),
                new HttpEntity<>(invalid),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * Feature: Create label — missing name
     * Given a label payload without a name
     * When the client sends POST
     * Then the response status is 400
     */
    @Test
    public void whenCreateLabelWithMissingNameThenReturnBadRequest() {
        LabelRes invalid = new LabelRes();
        invalid.setDescription("no-name");
        invalid.setColor("#FF0000");

        ResponseEntity<String> response = rest.postForEntity(
                apiUrl(RoutesV2.LABELS),
                new HttpEntity<>(invalid),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * Feature: Get label by id — not found
     * Given no label exists for the uuid
     * When the client sends GET
     * Then the response status is 404
     */
    @Test
    public void whenGetLabelWithUnknownUuidThenReturnNotFound() {
        ResponseEntity<String> response = rest.getForEntity(
                apiUrl(RoutesV2.LABELS, "/non-existent-uuid"),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * Feature: Create/update/read round-trip group
     * Given a valid label payload with group Status
     * When the client creates, reads, and updates the group
     * Then each response returns the trimmed group
     */
    @Test
    public void whenCreateAndUpdateLabelWithGroupThenRoundTrip() {
        String namePrefix = "whenCreateAndUpdateLabelWithGroupThenRoundTrip";

        LabelRes label = newLabel(namePrefix, "#0E8A16");
        label.setGroup("Status");

        ResponseEntity<LabelRes> created = rest.postForEntity(
                apiUrl(RoutesV2.LABELS),
                new HttpEntity<>(label),
                LabelRes.class
        );
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        assertThat(created.getBody().getGroup()).isEqualTo("Status");
        String labelUuid = created.getBody().getUuid();

        try {
            ResponseEntity<LabelRes> getAfterCreate = rest.getForEntity(
                    apiUrl(RoutesV2.LABELS, "/" + labelUuid),
                    LabelRes.class
            );
            assertThat(getAfterCreate.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(getAfterCreate.getBody().getGroup()).isEqualTo("Status");

            LabelRes update = newLabel(namePrefix, "#0E8A16");
            update.setUuid(labelUuid);
            update.setGroup("Release Status");

            ResponseEntity<LabelRes> updated = rest.exchange(
                    apiUrl(RoutesV2.LABELS, "/" + labelUuid),
                    HttpMethod.PUT,
                    new HttpEntity<>(update),
                    LabelRes.class
            );
            assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(updated.getBody().getGroup()).isEqualTo("Release Status");

            ResponseEntity<LabelRes> getAfterUpdate = rest.getForEntity(
                    apiUrl(RoutesV2.LABELS, "/" + labelUuid),
                    LabelRes.class
            );
            assertThat(getAfterUpdate.getBody().getGroup()).isEqualTo("Release Status");
        } finally {
            rest.delete(apiUrl(RoutesV2.LABELS, "/" + labelUuid));
        }
    }

    /**
     * Feature: Omit, null, and blank group are accepted as no group
     */
    @Test
    public void whenCreateLabelWithOmittedNullOrBlankGroupThenAcceptedAsNoGroup() {
        String namePrefix = "whenCreateLabelWithOmittedNullOrBlankGroupThenAcceptedAsNoGroup";

        LabelRes omitted = newLabel(namePrefix + "-omitted", "#111111");
        LabelRes withNull = newLabel(namePrefix + "-null", "#222222");
        withNull.setGroup(null);
        LabelRes blank = newLabel(namePrefix + "-blank", "#333333");
        blank.setGroup("");

        ResponseEntity<LabelRes> omittedCreated = rest.postForEntity(
                apiUrl(RoutesV2.LABELS), new HttpEntity<>(omitted), LabelRes.class);
        ResponseEntity<LabelRes> nullCreated = rest.postForEntity(
                apiUrl(RoutesV2.LABELS), new HttpEntity<>(withNull), LabelRes.class);
        ResponseEntity<LabelRes> blankCreated = rest.postForEntity(
                apiUrl(RoutesV2.LABELS), new HttpEntity<>(blank), LabelRes.class);

        assertThat(omittedCreated.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(nullCreated.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(blankCreated.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertNoGroup(omittedCreated.getBody());
        assertNoGroup(nullCreated.getBody());
        assertNoGroup(blankCreated.getBody());

        String omittedUuid = omittedCreated.getBody().getUuid();
        String nullUuid = nullCreated.getBody().getUuid();
        String blankUuid = blankCreated.getBody().getUuid();
        try {
            assertNoGroup(rest.getForEntity(apiUrl(RoutesV2.LABELS, "/" + omittedUuid), LabelRes.class).getBody());
            assertNoGroup(rest.getForEntity(apiUrl(RoutesV2.LABELS, "/" + nullUuid), LabelRes.class).getBody());
            assertNoGroup(rest.getForEntity(apiUrl(RoutesV2.LABELS, "/" + blankUuid), LabelRes.class).getBody());
        } finally {
            rest.delete(apiUrl(RoutesV2.LABELS, "/" + omittedUuid));
            rest.delete(apiUrl(RoutesV2.LABELS, "/" + nullUuid));
            rest.delete(apiUrl(RoutesV2.LABELS, "/" + blankUuid));
        }
    }

    /**
     * Feature: Whitespace-only group is trimmed away and not stored
     */
    @Test
    public void whenCreateLabelWithWhitespaceOnlyGroupThenStoredAsNoGroup() {
        String namePrefix = "whenCreateLabelWithWhitespaceOnlyGroupThenStoredAsNoGroup";

        LabelRes label = newLabel(namePrefix, "#ABCDEF");
        label.setGroup("   ");

        ResponseEntity<LabelRes> created = rest.postForEntity(
                apiUrl(RoutesV2.LABELS),
                new HttpEntity<>(label),
                LabelRes.class
        );
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertNoGroup(created.getBody());
        String labelUuid = created.getBody().getUuid();

        try {
            assertNoGroup(rest.getForEntity(apiUrl(RoutesV2.LABELS, "/" + labelUuid), LabelRes.class).getBody());
        } finally {
            rest.delete(apiUrl(RoutesV2.LABELS, "/" + labelUuid));
        }
    }

    /**
     * Feature: Padded group is trimmed on write
     */
    @Test
    public void whenCreateLabelWithPaddedGroupThenTrimmed() {
        String namePrefix = "whenCreateLabelWithPaddedGroupThenTrimmed";

        LabelRes label = newLabel(namePrefix, "#0E8A16");
        label.setGroup("  Status  ");

        ResponseEntity<LabelRes> created = rest.postForEntity(
                apiUrl(RoutesV2.LABELS),
                new HttpEntity<>(label),
                LabelRes.class
        );
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().getGroup()).isEqualTo("Status");
        String labelUuid = created.getBody().getUuid();

        try {
            ResponseEntity<LabelRes> getResponse = rest.getForEntity(
                    apiUrl(RoutesV2.LABELS, "/" + labelUuid),
                    LabelRes.class
            );
            assertThat(getResponse.getBody().getGroup()).isEqualTo("Status");
        } finally {
            rest.delete(apiUrl(RoutesV2.LABELS, "/" + labelUuid));
        }
    }

    /**
     * Feature: Distinct groups are exact trimmed strings (no case-folding)
     */
    @Test
    public void whenCreateLabelsWithStatusAndStatusGroupsThenBothSucceed() {
        String namePrefix = "whenCreateLabelsWithStatusAndStatusGroupsThenBothSucceed";

        LabelRes status = newLabel(namePrefix + "-stable", "#111111");
        status.setGroup("Status");
        LabelRes statusLower = newLabel(namePrefix + "-experimental", "#222222");
        statusLower.setGroup("status");

        ResponseEntity<LabelRes> first = rest.postForEntity(
                apiUrl(RoutesV2.LABELS), new HttpEntity<>(status), LabelRes.class);
        ResponseEntity<LabelRes> second = rest.postForEntity(
                apiUrl(RoutesV2.LABELS), new HttpEntity<>(statusLower), LabelRes.class);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getBody().getGroup()).isEqualTo("Status");
        assertThat(second.getBody().getGroup()).isEqualTo("status");

        String firstUuid = first.getBody().getUuid();
        String secondUuid = second.getBody().getUuid();
        try {
            assertThat(rest.getForEntity(apiUrl(RoutesV2.LABELS, "/" + firstUuid), LabelRes.class)
                    .getBody().getGroup()).isEqualTo("Status");
            assertThat(rest.getForEntity(apiUrl(RoutesV2.LABELS, "/" + secondUuid), LabelRes.class)
                    .getBody().getGroup()).isEqualTo("status");
        } finally {
            rest.delete(apiUrl(RoutesV2.LABELS, "/" + firstUuid));
            rest.delete(apiUrl(RoutesV2.LABELS, "/" + secondUuid));
        }
    }

    /**
     * Feature: Name uniqueness is global — different groups do not allow the same name
     */
    @Test
    public void whenCreateLabelWithDuplicateNameInDifferentGroupThenReturnConflict() {
        String namePrefix = "whenCreateLabelWithDuplicateNameInDifferentGroupThenReturnConflict";

        LabelRes first = newLabel(namePrefix, "#111111");
        first.setGroup("Status");
        ResponseEntity<LabelRes> created = rest.postForEntity(
                apiUrl(RoutesV2.LABELS),
                new HttpEntity<>(first),
                LabelRes.class
        );
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String firstUuid = created.getBody().getUuid();

        LabelRes duplicate = newLabel(namePrefix, "#222222");
        duplicate.setGroup("Release");
        ResponseEntity<String> response = rest.postForEntity(
                apiUrl(RoutesV2.LABELS),
                new HttpEntity<>(duplicate),
                String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        rest.delete(apiUrl(RoutesV2.LABELS, "/" + firstUuid));
    }

    /**
     * Feature: Group with internal spaces is allowed (not subject to the name character pattern)
     */
    @Test
    public void whenCreateLabelWithGroupContainingSpacesThenAccepted() {
        String namePrefix = "whenCreateLabelWithGroupContainingSpacesThenAccepted";

        LabelRes label = newLabel(namePrefix, "#0E8A16");
        label.setGroup("Release Status");

        ResponseEntity<LabelRes> created = rest.postForEntity(
                apiUrl(RoutesV2.LABELS),
                new HttpEntity<>(label),
                LabelRes.class
        );
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().getGroup()).isEqualTo("Release Status");

        rest.delete(apiUrl(RoutesV2.LABELS, "/" + created.getBody().getUuid()));
    }

    /**
     * Feature: Group of 255 characters after trim is accepted
     */
    @Test
    public void whenCreateLabelWithGroupOf255ThenAccepted() {
        String namePrefix = "whenCreateLabelWithGroupOf255ThenAccepted";
        String group = "a".repeat(255);

        LabelRes label = newLabel(namePrefix, "#0E8A16");
        label.setGroup("  " + group + "  ");

        ResponseEntity<LabelRes> created = rest.postForEntity(
                apiUrl(RoutesV2.LABELS),
                new HttpEntity<>(label),
                LabelRes.class
        );
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().getGroup()).isEqualTo(group);

        rest.delete(apiUrl(RoutesV2.LABELS, "/" + created.getBody().getUuid()));
    }

    /**
     * Feature: Group longer than 255 after trim is rejected
     */
    @Test
    public void whenCreateLabelWithGroupLongerThan255ThenReturnBadRequest() {
        LabelRes label = newLabel("whenCreateLabelWithGroupLongerThan255ThenReturnBadRequest", "#FF0000");
        label.setGroup("a".repeat(256));

        ResponseEntity<String> response = rest.postForEntity(
                apiUrl(RoutesV2.LABELS),
                new HttpEntity<>(label),
                String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private static LabelRes newLabel(String name, String color) {
        LabelRes label = new LabelRes();
        label.setName(name);
        label.setDescription(name + "-desc");
        label.setColor(color);
        return label;
    }

    private static void assertNoGroup(LabelRes label) {
        assertThat(label).isNotNull();
        assertThat(label.getGroup()).isNullOrEmpty();
    }
}
