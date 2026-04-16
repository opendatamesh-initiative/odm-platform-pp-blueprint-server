# Blueprint — use-case update of documentation and repository configuration (testable requirements)

**Feature:** A **POST** use-case endpoint updates an existing blueprint’s **`displayName`** and **`description`**, and may update **nested repository configuration** in the same request. Validation must match **registration quality**: semantic rules equivalent to `**RegisterBlueprintSemanticValidationOutboundPortImpl**` (URLs, paths), plus required/length/enumeration rules **aligned with `**BlueprintServiceImpl**` validation** for blueprint and `**BlueprintRepo**`. **`uuid`** and blueprint **`name`** are not modified. This flow **does not** use generic CRUD **`PUT` / `overwriteResource`**.

**Implementation note:** Dedicated `**CommandRes**`, use-case stack under `**...services.usecases.*`**, persistence outside `**overwriteResource**` and follow the guidelines inside `**guidelines`** folder. Tests extend `**BlueprintApplicationIT**` (or project standard).

---

## Update display name and description only (repository omitted)

**Requirement:** When the command body sets `**displayName**` and `**description**` and **does not** include nested repository configuration (per API contract for “omit”), the API returns **200**, those two fields persist, and **`name`**, **`uuid`**, and existing **`blueprintRepo`** (if any) remain **unchanged** field-by-field except for audit timestamps if applicable.

**Test:** `whenUpdateDocumentationFieldsWithoutRepoThenRepoUnchanged`

```gherkin
Given a blueprint exists with uuid "B" and a configured blueprintRepo
And known values for name, uuid, displayName, description, and all blueprintRepo fields
When the client sends POST to the update-documentation-fields endpoint for blueprint "B"
  with body containing only displayName and description (no nested repository payload)
Then the response status is 200
And the response body shows the new displayName and description
And GET "/api/v2/pp/blueprint/blueprints/B" returns the same name, uuid, and blueprintRepo field values as before the request
```

---

## Update documentation and full repository configuration

**Requirement:** When the command includes a **complete** nested repository configuration (per product rules), the API returns **200** after validation; `**displayName**` and `**description**` persist as sent; `**blueprintRepo**` reflects the new configuration; **`name`** and **`uuid`** of the blueprint are unchanged.

**Test:** `whenUpdateDocumentationFieldsWithRepoThenRepoAndBlueprintUpdated`

```gherkin
Given a blueprint exists with uuid "B" and a configured blueprintRepo
When the client sends POST to the update-documentation-fields endpoint for blueprint "B"
  with body containing displayName, description, and nested repository fields that differ from the stored repo but are valid
Then the response status is 200
And GET by uuid "B" shows the updated displayName, description, and blueprintRepo matching the submitted configuration
And blueprint name and uuid are unchanged from before the request
```