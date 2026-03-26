# Blueprint REST API (v2) — testable requirements

**Feature:** Expose full CRUD and paginated search for blueprints at `/api/v2/pp/blueprint/blueprints`, returning `BlueprintRes` and standard Spring Data page JSON for list operations.

**Implementation note:** Logic lives in `BlueprintServiceImpl`, `BlueprintsRepository` (with `Spec` / `SpecUtils` for filters), and `BlueprintMapper`. Integration tests: `BlueprintControllerIT` extends `BlueprintApplicationIT`, uses `TestRestTemplate` and `RoutesV2` (or equivalent URL helpers). Default list: sort `createdAt` DESC, page size 20. Valid sort properties: `uuid`, `name`, `displayName`, `description`, `createdAt`, `updatedAt`. Invalid sort property → **400** (via `PropertyReferenceException` → global handler). Duplicate business keys → `ResourceConflictException` → **409**.

---

## Create blueprint (happy path)

**Requirement:** `POST /api/v2/pp/blueprint/blueprints` with a valid `BlueprintRes` body returns **201 Created** and a JSON body that matches the persisted blueprint (including generated or supplied `uuid`, and core fields such as `name`, `displayName`, `description`, and audit fields as applicable).

**Test:** `whenCreateBlueprintThenReturnCreatedBlueprint`

```gherkin
Given the API is available
And a valid blueprint payload is prepared (required fields satisfied per service validation)
When the client sends POST to "/api/v2/pp/blueprint/blueprints" with that JSON body
Then the response status is 201
And the response body is a BlueprintRes reflecting the created resource
And a subsequent GET by the returned uuid returns the same logical data
```

---

## Get blueprint by id

**Requirement:** `GET /api/v2/pp/blueprint/blueprints/{uuid}` returns **200 OK** and `BlueprintRes` when the blueprint exists.

**Test:** `whenGetBlueprintByIdThenReturnBlueprint`

```gherkin
Given a blueprint exists with a known uuid
When the client sends GET to "/api/v2/pp/blueprint/blueprints/{uuid}"
Then the response status is 200
And the response body matches the stored blueprint
```

---

## Get blueprint by id — not found

**Requirement:** `GET` for an unknown `uuid` returns **404** consistent with `NotFoundException` / global exception handling.

**Test:** `whenGetBlueprintWithNonExistentIdThenReturnNotFound`

```gherkin
Given no blueprint exists for uuid "non-existent-uuid"
When the client sends GET to "/api/v2/pp/blueprint/blueprints/non-existent-uuid"
Then the response status is 404
```

---

## Search blueprints — paginated list

**Requirement:** `GET /api/v2/pp/blueprint/blueprints` without filters returns **200** and a Spring Data page JSON object with `content`, `totalElements`, and pagination metadata. Default sorting is `createdAt` descending; default page size is **20**.

**Test:** `whenSearchBlueprintsThenReturnBlueprintsList`

```gherkin
Given one or more blueprints exist in the database
When the client sends GET to "/api/v2/pp/blueprint/blueprints" with default pagination
Then the response status is 200
And the body has a "content" array of BlueprintRes
And "totalElements" matches the number of matching blueprints
And results are ordered by createdAt descending by default
```

---

## Search blueprints — filters

**Requirement:** When `BlueprintSearchOptions` query parameters are bound and implemented, filtered `GET` returns only matching blueprints and an accurate `totalElements` for the filter.

**Test:** `whenSearchBlueprintsWithFiltersThenReturnFilteredResults`

```gherkin
Given blueprints exist with distinguishable field values (e.g. name or other supported filter fields)
When the client sends GET to "/api/v2/pp/blueprint/blueprints" including supported filter query parameters
Then the response status is 200
And every item in "content" satisfies the filter
And "totalElements" equals the count of matching rows
```

---

## Update blueprint

**Requirement:** `PUT /api/v2/pp/blueprint/blueprints/{uuid}` with a valid body returns **200** and updated `BlueprintRes`; a follow-up `GET` reflects the changes.

**Test:** `whenUpdateBlueprintThenReturnUpdatedBlueprint`

```gherkin
Given an existing blueprint with a known uuid
When the client sends PUT to "/api/v2/pp/blueprint/blueprints/{uuid}" with valid updated fields
Then the response status is 200
And the response body reflects the updated values
And GET by the same uuid returns the updated blueprint
```

---

## Delete blueprint

**Requirement:** `DELETE /api/v2/pp/blueprint/blueprints/{uuid}` returns **204 No Content** for an existing blueprint; subsequent `GET` returns **404**.

**Test:** `whenDeleteBlueprintThenReturnNoContentAndBlueprintIsDeleted`

```gherkin
Given an existing blueprint with a known uuid
When the client sends DELETE to "/api/v2/pp/blueprint/blueprints/{uuid}"
Then the response status is 204
And GET to the same path returns 404
```

---

## Create / update with invalid data — bad request

**Requirement:** `POST` or `PUT` with payloads that fail validation (missing required fields, invalid format, etc.) returns **400** per global handling (`BadRequestException` or equivalent).

**Test:** `whenCreateBlueprintWithInvalidDataThenReturnBadRequest`

```gherkin
Given an invalid blueprint payload (violates validation rules agreed in service layer)
When the client sends POST to "/api/v2/pp/blueprint/blueprints" with that body
Then the response status is 400
```

---

## Update blueprint — duplicate name conflict

**Requirement:** `PUT` that would violate unique `name` constraint returns **409 Conflict** (`ResourceConflictException`).

**Test:** `whenUpdateBlueprintWithDuplicateNameThenReturnConflict`

```gherkin
Given blueprint A and blueprint B exist with different names
When the client sends PUT on B's uuid with name equal to A's name (and duplicates are disallowed)
Then the response status is 409
```

---

## Create blueprint — duplicate uuid conflict

**Requirement:** Creating a blueprint whose `uuid` is already used by another blueprint returns **409 Conflict**.

```gherkin
Given a blueprint already exists with uuid U
When the client sends POST with body containing the same uuid U
Then the response status is 409
```

---

## Create blueprint — duplicate name conflict

**Requirement:** Creating a blueprint whose `name` duplicates an existing blueprint (case rules as implemented) returns **409 Conflict**.

```gherkin
Given a blueprint exists with name "existing-name"
When the client sends POST with the same logical name (per uniqueness rules)
Then the response status is 409
```

---

## Blueprint with nested repository — create

**Requirement:** `POST` with a valid nested `blueprintRepo` returns **201** and `BlueprintRes` including persisted repository fields aligned with `BlueprintRepoRes`.

**Test:** `whenCreateBlueprintWithRepositoryThenReturnCreatedBlueprintWithRepository`

```gherkin
Given a valid blueprint payload including a complete nested blueprintRepo
When the client sends POST to "/api/v2/pp/blueprint/blueprints"
Then the response status is 201
And the response includes blueprintRepo with expected fields populated
```

---

## Blueprint with nested repository — read

**Requirement:** `GET` returns nested repository details when present.

**Test:** `whenGetBlueprintWithRepositoryThenReturnBlueprintWithRepositoryDetails`

```gherkin
Given a blueprint exists with an associated blueprintRepo
When the client sends GET to "/api/v2/pp/blueprint/blueprints/{uuid}"
Then the response status is 200
And blueprintRepo is present and matches stored data
```

---

## Blueprint with nested repository — update

**Requirement:** `PUT` can change nested repository fields; response and subsequent `GET` show the updated repository.

**Test:** `whenUpdateBlueprintRepositoryThenReturnUpdatedBlueprintWithModifiedRepository`

```gherkin
Given a blueprint with blueprintRepo exists
When the client sends PUT with modified blueprintRepo fields
Then the response status is 200
And GET returns the updated repository data
```

---

## Blueprint with nested repository — delete (cascade)

**Requirement:** `DELETE` on a blueprint that owns a repository removes the blueprint and associated repository data so both are gone (**204**; follow-up `GET` **404**).

**Test:** `whenDeleteBlueprintWithRepositoryThenReturnNoContentAndBothAreDeleted`

```gherkin
Given a blueprint with nested blueprintRepo exists
When the client sends DELETE to "/api/v2/pp/blueprint/blueprints/{uuid}"
Then the response status is 204
And GET for that blueprint returns 404
And the linked repository record no longer exists (or is unreachable as specified by persistence rules)
```

---

## Nested repository — invalid payload (generic)

**Requirement:** `POST` with `blueprintRepo` present but failing structural or field validation returns **400**.

**Test:** `whenCreateBlueprintWithRepositoryWithInvalidDataThenReturnBadRequest`

```gherkin
Given a blueprint payload with blueprintRepo missing required fields or invalid values per validation
When the client sends POST to "/api/v2/pp/blueprint/blueprints"
Then the response status is 400
```

---

## Nested repository — invalid provider type

**Requirement:** `POST` with `blueprintRepo` containing an invalid Git provider type (not in allowed set, e.g. not AZURE/BITBUCKET/GITHUB/GITLAB as applicable) returns **400**.

**Test:** `whenCreateBlueprintWithRepositoryWithInvalidProviderTypeThenReturnBadRequest`

```gherkin
Given a blueprint payload with blueprintRepo and an invalid provider type value
When the client sends POST to "/api/v2/pp/blueprint/blueprints"
Then the response status is 400
```

---

## Nested repository — invalid URLs

**Requirement:** `POST` with `blueprintRepo` containing invalid `remoteUrlHttp` and/or `remoteUrlSsh` (per URL validation rules) returns **400**.

**Test:** `whenCreateBlueprintWithRepositoryWithInvalidUrlsThenReturnBadRequest`

```gherkin
Given a blueprint payload with blueprintRepo and malformed or disallowed remote URLs
When the client sends POST to "/api/v2/pp/blueprint/blueprints"
Then the response status is 400
```

---

## Update / delete — not found

**Requirement:** `PUT` and `DELETE` for an unknown `uuid` return **404**.

```gherkin
Given no blueprint exists for uuid "missing-uuid"
When the client sends PUT to "/api/v2/pp/blueprint/blueprints/missing-uuid" with a valid body
Then the response status is 404

Given no blueprint exists for uuid "missing-uuid"
When the client sends DELETE to "/api/v2/pp/blueprint/blueprints/missing-uuid"
Then the response status is 404
```
