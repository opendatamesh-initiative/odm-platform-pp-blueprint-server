# Blueprint version REST API (v2) — testable requirements

**Feature:** Expose CRUD for single blueprint versions and a paginated search that returns lightweight `BlueprintVersionShortRes` rows (no heavy JSON blobs), at `/api/v2/pp/blueprint/blueprints-versions`.

**Implementation note:** Writes and read-by-id use `BlueprintVersionCrudService` → full `BlueprintVersionRes`. Collection `GET` uses `BlueprintVersionQueryService.findAllResourcesShort` → `Page<BlueprintVersionShortRes>`. Repositories: `BlueprintVersionsRepository` (e.g. `existsByVersionNumberIgnoreCaseAndBlueprintUuidAndUuidNot` for uniqueness), `BlueprintVersionsShortRepository` with spec helpers (`hasName`, `hasBlueprintUuid`, `hasTag`, `hasVersionNumber`, `matchSearch`). Integration tests: `BlueprintVersionsControllerIT` extends `BlueprintApplicationIT`, `TestRestTemplate`, `RoutesV2`. Default list: `createdAt` DESC, page size **20**. Valid sort properties: `uuid`, `blueprintUuid`, `name`, `description`, `tag`, `createdAt`, `updatedAt`. Invalid sort → **400**. Duplicate `(blueprintUuid, versionNumber)` on create → **409** (`ResourceConflictException`).

---

## Create blueprint version (happy path)

**Requirement:** `POST /api/v2/pp/blueprint/blueprints-versions` with a valid `BlueprintVersionRes` returns **201 Created** and a body matching the persisted entity (including link to parent `blueprintUuid`, metadata fields, and audit as applicable).

**Test:** `whenCreateBlueprintVersionThenReturnCreatedBlueprintVersion`

```gherkin
Given a parent blueprint exists (valid blueprintUuid)
And a valid blueprint version payload is prepared
When the client sends POST to "/api/v2/pp/blueprint/blueprints-versions" with that JSON body
Then the response status is 201
And the response body is a BlueprintVersionRes consistent with the created row
And GET by the returned uuid returns the same logical data
```

---

## Get blueprint version by id

**Requirement:** `GET /api/v2/pp/blueprint/blueprints-versions/{uuid}` returns **200** and `BlueprintVersionRes` when the version exists.

**Test:** `whenGetBlueprintVersionByIdThenReturnBlueprintVersion`

```gherkin
Given a blueprint version exists with a known uuid
When the client sends GET to "/api/v2/pp/blueprint/blueprints-versions/{uuid}"
Then the response status is 200
And the response body matches the stored version
```

---

## Get blueprint version by id — not found

**Requirement:** `GET` for an unknown uuid returns **404**.

**Test:** `whenGetBlueprintVersionByNonExistentIdThenReturnNotFound`

```gherkin
Given no blueprint version exists for uuid "non-existent-uuid"
When the client sends GET to "/api/v2/pp/blueprint/blueprints-versions/non-existent-uuid"
Then the response status is 404
```

---

## Search blueprint versions — paginated short list

**Requirement:** `GET /api/v2/pp/blueprint/blueprints-versions` returns **200** and a Spring Data page where each `content` element is a `BlueprintVersionShortRes` (no full `content` JSON blob). Default sort `createdAt` DESC, page size **20**; `totalElements` is correct for the dataset.

**Test:** `whenSearchBlueprintVersionsThenReturnPaginatedResults`

```gherkin
Given one or more blueprint versions exist
When the client sends GET to "/api/v2/pp/blueprint/blueprints-versions" with default pagination
Then the response status is 200
And the body has "content" as an array of short DTOs (uuid, blueprintUuid, name, description, tag, versionNumber, createdBy, updatedBy, audit timestamps as defined on BlueprintVersionShortRes)
And "totalElements" matches the number of matching versions
And default ordering is createdAt descending
```

---

## Search blueprint versions — text / filter parameter

**Requirement:** When search options (e.g. free-text `search` or other query params bound on `BlueprintVersionSearchOptions`) are applied, only matching versions appear and `totalElements` reflects the filter.

**Test:** `whenSearchBlueprintVersionsWithSearchParameterThenReturnFilteredResults`

```gherkin
Given multiple blueprint versions exist with different names or searchable fields
When the client sends GET with the supported search/filter query parameters
Then the response status is 200
And every entry in "content" matches the filter semantics
And "totalElements" equals the filtered count
```

---

## Update blueprint version

**Requirement:** `PUT /api/v2/pp/blueprint/blueprints-versions/{uuid}` with valid body returns **200** and updated `BlueprintVersionRes`; follow-up `GET` reflects changes.

**Test:** `whenUpdateBlueprintVersionThenReturnUpdatedBlueprintVersion`

```gherkin
Given an existing blueprint version with known uuid
When the client sends PUT with valid updated fields
Then the response status is 200
And GET by the same uuid returns the updated data
```

---

## Update blueprint version — not found

**Requirement:** `PUT` for unknown uuid returns **404**.

**Test:** `whenUpdateNonExistentBlueprintVersionThenReturnNotFound`

```gherkin
Given no blueprint version exists for uuid "missing-uuid"
When the client sends PUT to "/api/v2/pp/blueprint/blueprints-versions/missing-uuid" with a valid body
Then the response status is 404
```

---

## Delete blueprint version

**Requirement:** `DELETE` for an existing version returns **204**; subsequent `GET` returns **404**.

**Test:** `whenDeleteBlueprintVersionThenReturnNoContent`

```gherkin
Given an existing blueprint version with known uuid
When the client sends DELETE to "/api/v2/pp/blueprint/blueprints-versions/{uuid}"
Then the response status is 204
And GET to the same path returns 404
```

---

## Delete blueprint version — not found

**Requirement:** `DELETE` for unknown uuid returns **404**.

**Test:** `whenDeleteNonExistentBlueprintVersionThenReturnNotFound`

```gherkin
Given no blueprint version exists for uuid "missing-uuid"
When the client sends DELETE to "/api/v2/pp/blueprint/blueprints-versions/missing-uuid"
Then the response status is 404
```

---

## JSON content field — store and retrieve

**Requirement:** Creating and reading a version with a JSON `content` payload persists and returns the same structured content on `GET` (full `BlueprintVersionRes`); list/search short DTOs omit heavy `content` as per `BlueprintVersionShortRes`.

**Test:** `whenCreateBlueprintVersionWithJsonContentThenContentIsCorrectlyStoredAndRetrieved`

```gherkin
Given a valid blueprint version payload including a non-trivial JSON content object
When the client sends POST to create the version
Then the response status is 201
And GET by uuid returns content deeply equal to the submitted JSON
And a collection GET returns short entries without exposing the large content payload
```

---

## Create blueprint version — duplicate version number

**Requirement:** Creating a version whose `versionNumber` duplicates another version for the same `blueprintUuid` (case rules per `existsByVersionNumberIgnoreCaseAndBlueprintUuidAndUuidNot`) returns **409 Conflict**.

```gherkin
Given blueprint B has a version with versionNumber "1.0.0"
When the client sends POST with the same blueprintUuid and the same versionNumber (per case-insensitive rule)
Then the response status is 409
```

---

## End-to-end consistency (create → list → update → delete)

**Requirement:** A version created via `POST` appears in paginated search with expected short fields; after `PUT`, search reflects updates; after `DELETE`, it no longer appears and `GET` is **404**.

```gherkin
Given a parent blueprint exists
When the client creates a blueprint version via POST
Then GET collection includes an entry for that version with matching short fields
When the client updates that version via PUT
Then GET collection reflects updated short fields where applicable
When the client deletes that version via DELETE
Then GET by uuid returns 404
And the version no longer appears in default search results
```

---

## Invalid input — bad request

**Requirement:** `POST` / `PUT` with payloads failing validation return **400** (aligned with global exception handling).

```gherkin
Given an invalid blueprint version payload
When the client sends POST to "/api/v2/pp/blueprint/blueprints-versions"
Then the response status is 400
```
