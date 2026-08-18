# SPDD Analysis: Blueprint version REST API (v2) — CRUD and search

## Proposal

**Purpose:** This document is the **development guide for code logic**. It explains why and how; the Spec section below defines testable requirements (Gherkin). Implementers follow this proposal when writing service/repository code and use the Spec section when writing integration tests.

**Test-first workflow (expected):** The Spec section was derived from this proposal (and from `**BlueprintVersionsController**`). Implement `**BlueprintVersionsControllerIT**` against the Spec section. TestsTestRestTemplate  **should fail** until `**BlueprintVersionCrudServiceImpl**`, `**BlueprintVersionQueryServiceImpl**` (and related repository specifications, `**BlueprintVersionSearchOptions**`, mappers, optional `**BlueprintVersionShort**` projection path) are complete; then implement until tests pass.

**Note:** Every bullet list below is **variable length** (0 to N items). Add as many bullets as the change needs; do not pad or trim to a fixed number. Empty lists are allowed.

---

## Context

- **BlueprintVersion** is a JPA entity (`blueprint_versions` table) linked to `**Blueprint**` via `**blueprint_uuid**`, with fields such as `**uuid**`, `**name**`, `**description**`, `**tag**`, `**spec**`, `**specVersion**`, `**versionNumber**`, JSON `**content**`, `**createdBy**`, `**updatedBy**`, plus auditing from `**VersionedEntity**`. `**@PrePersist**` may align `**updatedBy**` with `**createdBy**` when appropriate.
- REST exposure is `**BlueprintVersionsController**`, base path `**/api/v2/pp/blueprint/blueprints-versions**`, producing JSON.
- **Writes and read-by-id** use `**BlueprintVersionCrudService**` → full `**BlueprintVersionRes**`.
- **Collection search** uses `**BlueprintVersionQueryService.findAllResourcesShort**`, returning `**Page<BlueprintVersionShortRes>**` for performance (no heavy embedded payloads such as large JSON blobs — align field omission with `**BlueprintVersionShortRes**` / `**BlueprintVersionShort**` definitions).
- `**BlueprintVersionCrudServiceImpl**` and `**BlueprintVersionQueryServiceImpl**` are stubs: they do not yet provide repository access, specifications, or mapping, so HTTP behavior is not production-ready.
- Integration tests should extend `**BlueprintApplicationIT`** and use `**TestRestTemplate`** in the same style as `**GitProviderControllerIT**`. Add `**RoutesV2**` enum entries (or equivalent URL helpers) for blueprint paths so tests stay maintainable.

## Goal

Clients can create, read, update, delete blueprint versions and list/search them with pagination and filters, using full `**BlueprintVersionRes**` for single-resource operations and `**BlueprintVersionShortRes**` for list/search responses, as documented in the Spec section.

## Scope

- **In scope**
  - `**BlueprintVersionCrudServiceImpl**`: full CRUD + mapping `**BlueprintVersion` ↔ BlueprintVersionRes`**, **`validate`/`reconcile`**, **`getRepository`**, **`getSpecFromFilters`** (even if some filters are deferred, document in **the Spec section**).
  - `**BlueprintVersionQueryServiceImpl`**: `**findAllResourcesShort**` (and internal `**findAllShort**` if needed) with `**Specification**` from `**BlueprintVersionSearchOptions**`, mapping to `**BlueprintVersionShortRes**`.
  - JPA repository for `**BlueprintVersion**` (if not already complete): extends `**PagingAndSortingAndSpecificationExecutorRepository**`.
  - `**BlueprintVersionsControllerIT**` scenarios covered by the Spec section (happy paths and key error cases: 404, 400 where applicable).
- **Out of scope**
  - Unrelated controllers (e.g. git-providers).
  - Changing the public URL shape of `**BlueprintVersionsController`**.
  - Non-HTTP modules outside blueprint versions service (unless a dependency is unavoidable for compilation).
  - Utils, configuration, exceptions, git folders

## Proposed direction

- **Split responsibilities:** `**BlueprintVersionCrudServiceImpl`** handles `**POST**`, `**GET /{uuid}**`, `**PUT /{uuid}**`, `**DELETE /{uuid}**`; `**BlueprintVersionQueryServiceImpl**` handles `**GET**` (collection) only.
- `**BlueprintVersionCrudServiceImpl`** must contain all the mapping (toRes, toEntity), validation, reconcile, getSpecFromFilters, beforeCreation, beforeOverwrite, overwriteResource.
- The validation inside `**BlueprintVersionCrudServiceImpl`** must check also the length and the required fields
- **Layering:** Keep `**BlueprintVersionController`** thin; all persistence and mapping live in `**BlueprintVersionCrudServiceImpl`** + `**BlueprintsVersionRepository`** + mapper/specs.
- `**BlueprintsVersionsRepository`** interface must have methods e.g existsByVersionNumberIgnoreCaseAndBlueprintUuidAndUuidNot.
- `**BlueprintsVersionsShortRepository`** interface must have methods like hasName, hasBlueprintUuid, hasTag, hasVersionNumber and matchSearch.
- **Search/list:** `**GET`** uses `**BlueprintVersionSearchOptions**` + `**Pageable**`. Default sort: `**createdAt` DESC**, page size **20**. Document valid sort properties (controller text lists: `**uuid`**, `**blueprintUuid**`, `**name**`, `**description**`, `**tag**`, `**createdAt**`, `**updatedAt**`). Response page contains `**BlueprintVersionShortRes**` entries only.
- **Consistency:** List/search results should be consistent with persisted entities (e.g. a created version appears in search with expected short fields).

## Success criteria

- All Gherkin scenarios in the Spec section are implemented in `**BlueprintVersionsControllerIT**` with traceability to the spec.
- With implementations complete, create → get-by-uuid → search contains entry → update → delete → get **404** holds for the flows defined in the Spec section.
- Paginated list returns standard Spring Data page JSON with `**content**` of short DTOs and correct `**totalElements**` for seeded data.
- Invalid inputs and missing resources behave as specified (**400** / **404**) and match global exception handling.
- When create a blueprint version with the duplicate version number then return a Conflict exception
- whenCreateBlueprintVersionThenReturnCreatedBlueprintVersion
- whenGetBlueprintVersionByIdThenReturnBlueprintVersion
- whenGetBlueprintVersionByNonExistentIdThenReturnNotFound
- whenSearchBlueprintVersionsThenReturnPaginatedResults
- whenUpdateBlueprintVersionThenReturnUpdatedBlueprintVersion
- whenUpdateNonExistentBlueprintVersionThenReturnNotFound
- whenDeleteBlueprintVersionThenReturnNoContent
- whenDeleteNonExistentBlueprintVersionThenReturnNotFound
- whenCreateBlueprintVersionWithJsonContentThenContentIsCorrectlyStoredAndRetrieved
- whenSearchBlueprintVersionsWithSearchParameterThenReturnFilteredResults

## Spec

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
