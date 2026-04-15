# Blueprint version — editor update of name and description (testable requirements)

**Feature:** Users can update **only** a persisted blueprint version’s `**name`** and `**description**`. Fields such as `**readme**`, `**tag**`, `**spec**`, `**specVersion**`, `**versionNumber**`, `**content**`, `**uuid**`, and nested `**blueprint**` must remain unchanged by this operation. On success, `updatedBy` is set to the user performing the update; `createdBy` remains the value from before the request.

**Implementation note:** Prefer a **POST** on `BlueprintVersionsUseCaseController` with a small command DTO (not full `BlueprintVersionRes`) and a dedicated use case (factory, command, presenter, outbound port per `USE_CASE_IMPLEMENTATION.md`). Integration tests compare `GET` before/after for all non-documentation fields and for audit fields as specified below.

---

## Editor updates blueprint version name and description

**Requirement:** Given an existing blueprint version, when an user calls the documentation update endpoint with new `name` and `description`, the API returns **200** and the persisted version shows the updated values. `**readme`**, `**tag**`, `**spec**`, `**specVersion**`, `**versionNumber**`, `**content**`, `**uuid**`, and nested `**blueprint**` (and any other non-documentation fields) are **unchanged** compared to immediately before the request when compared field by field where applicable. `**createdBy`** is unchanged; `**updatedBy**` equals the acting user’s id.

**Test:** `whenUpdatesBlueprintVersionNameAndDescriptionThenOnlyThoseFieldsAreUpdated`

```gherkin
Given a blueprint version exists with uuid "V"
And the blueprint version documentation update endpoint is available
And the current version has known values for name, description, readme, tag, spec, specVersion, versionNumber, content, blueprint, createdBy, and updatedBy
When an authorized user sends a POST to the documentation update URL for version "V"
  with body setting name to "New version name" and description to "New description text"
Then the response status is 200
And the response body has name "New version name" and description "New description text"
And GET "/api/v2/pp/blueprint/blueprints-versions/V" returns the same uuid, readme, tag, spec, specVersion, versionNumber, content, and blueprint as before the request
And GET returns createdBy equal to the pre-update createdBy
And GET returns updatedBy equal to the user that made the change
```
---

## Invalid payload — bad request

**Requirement:** Values that violate validation (e.g. blank `name` if required, exceeding max length) return **400 Bad Request**; persistence is unchanged.

**Test:** `whenSendsInvalidBlueprintVersionUpdateFieldThenBadRequest`

```gherkin
Given a blueprint exists
When an editor sends the editor blueprint version update request with a payload that violates validation rules
Then the response status is 400
And GET the blueprint version returns the same field values as before the request
```

---


