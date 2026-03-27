# Register blueprint — testable requirements (Gherkin)

Feature: Register blueprint via public use-case endpoint

  As an API client  
  I want to register a new blueprint with repository configuration in one request  
  So that I get a created blueprint with semantic validation applied without using the hidden CRUD create endpoint

---

## Scenario: Successful registration returns 201 and created blueprint

```gherkin
Given the API is available
And a valid RegisterBlueprintCommandRes is prepared with a complete blueprint (name, displayName, description, blueprintRepo with valid URLs and paths)
When the client sends POST to "/api/v2/pp/blueprint/blueprints/register" with Content-Type application/json
Then the response status is 201
And the response body is a RegisterBlueprintResponseRes
And the nested blueprint has a non-null uuid
And the nested blueprint name matches the request
And a subsequent GET to "/api/v2/pp/blueprint/blueprints/{uuid}" returns 200 with the same logical blueprint data
```

**Requirement ID:** `REG-BP-001`

---

## Scenario: Invalid HTTP remote URL returns 400

```gherkin
Given a RegisterBlueprintCommandRes that is otherwise valid for blueprint creation
And the blueprintRepo.remoteUrlHttp is not a valid http(s) URL (e.g. "not-a-url")
When the client sends POST to "/api/v2/pp/blueprint/blueprints/register"
Then the response status is 400
And no blueprint is persisted for that logical registration attempt
```

**Requirement ID:** `REG-BP-002`

---

## Scenario: Invalid SSH remote URL returns 400

```gherkin
Given a RegisterBlueprintCommandRes that is otherwise valid
And the blueprintRepo.remoteUrlSsh does not match the allowed SSH URL shape (e.g. plain "https://wrong-scheme")
When the client sends POST to "/api/v2/pp/blueprint/blueprints/register"
Then the response status is 400
```

**Requirement ID:** `REG-BP-003`

---

## Scenario: Invalid provider base URL returns 400

```gherkin
Given a RegisterBlueprintCommandRes that is otherwise valid
And the blueprintRepo.providerBaseUrl is not a valid http(s) base URI
When the client sends POST to "/api/v2/pp/blueprint/blueprints/register"
Then the response status is 400
```

**Requirement ID:** `REG-BP-004`

---

## Scenario: Path traversal in repository paths returns 400

```gherkin
Given a RegisterBlueprintCommandRes that is otherwise valid
And one of manifestRootPath, descriptorTemplatePath, or readmePath contains ".." (path traversal)
When the client sends POST to "/api/v2/pp/blueprint/blueprints/register"
Then the response status is 400
```

**Requirement ID:** `REG-BP-005`

---

## Notes

- CRUD-level rules (required fields, lengths, enums, uniqueness) remain in `BlueprintServiceImpl`; these scenarios do not duplicate them.
- The register use case does **not** create a `BlueprintVersion`.
