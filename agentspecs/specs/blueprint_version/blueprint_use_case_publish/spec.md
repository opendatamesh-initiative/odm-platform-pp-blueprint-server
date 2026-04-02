# Publish blueprint version — testable requirements (Gherkin)

Feature: Publish blueprint versions via public use-case endpoint

  As an API client  
  I want to publish a new blueprint version with blueprint and blueprint repository configuration in one request
  So that I get a created blueprint version with semantic validation applied without using the hidden CRUD create endpoint

---

## Scenario: Successful publish returns 201 and created blueprint version

```gherkin
Given the API is available
And a valid PublishBlueprintVersionCommandRes is prepared with a complete blueprint version (name, displayName, description, content, spec, specVersion, versionNumber, blueprint and blueprintRepo nested objects)
When the client sends POST to "/api/v2/pp/blueprint/blueprints-versions/publish" with Content-Type application/json
Then the response status is 201
And the response body is a PublishBlueprintVersionResponseRes
And the nested blueprint has a non-null uuid
And the nested blueprint name matches the request
And a subsequent GET to "/api/v2/pp/blueprint/blueprints-versions/{uuid}" returns 200 with the same logical blueprint data
```

**Requirement ID:** `PUB-BP-001`

---

## Scenario: Invalid spec returns 400

```gherkin
Given a PublishBlueprintVersionCommandRes that is otherwise valid for blueprint version creation
And the blueprintVersion.spec is not a valid spec (e.g. "not-a-valid-spec")
When the client sends POST to "/api/v2/pp/blueprint/blueprints-versions/publish"
Then the response status is 400
And no blueprint version is persisted for that logical publish attempt
```

**Requirement ID:** `PUB-BP-002`

---

## Scenario: Publishing a blueprintVersion with name and versionNumber already present returns 409 Conflict

```gherkin
Given a PublishBlueprintVersionCommandRes that is otherwise valid for blueprint version creation
And the blueprintVersion.uuid and blueprintVersion.versionNumber are already present
When the client sends POST to "/api/v2/pp/blueprint/blueprints-versions/publish"
Then the response status is 409
And no blueprint version is persisted for that logical publish attempt

```

**Requirement ID:** `PUB-BP-003`

## Scenario: Publishing a blueprintVersion with name and tag already present returns 409 Conflict

```gherkin
Given a PublishBlueprintVersionCommandRes that is otherwise valid for blueprint version creation
And the blueprintVersion.uuid and blueprintVersion.tag are already present
When the client sends POST to "/api/v2/pp/blueprint/blueprints-versions/publish"
Then the response status is 409
And no blueprint version is persisted for that logical publish attempt

```

**Requirement ID:** `PUB-BP-004`

---

## Scenario: Invalid manifest content returns 400 Bad Request

```gherkin
Given a PublishBlueprintVersionCommandRes that is otherwise valid for blueprint version creation
And the blueprintVersion.content is not a valid content
When the client sends POST to "/api/v2/pp/blueprint/blueprints-versions/publish"
Then the response status is 400
And no blueprint version is persisted for that logical publish attempt

```

**Requirement ID:** `PUB-BP-005`

---

## Notes

- CRUD-level rules (required fields, lengths, enums, uniqueness) remain in `BlueprintVersionCrudServiceImpl`; these scenarios do not duplicate them.
- The publish use case does **not** create a `Blueprint` or `BlueprintRepo`.
- **Auto-fill** (`OdmBlueprintManifestFieldGenerationVisitor` / `OdmBlueprintManifestFieldGenerator`) should be covered by separate unit tests; manifest validation scenarios above assume either pre-filled manifests or the post-autofill validation step, as implemented in `PublishBlueprintVersion.execute()`.
