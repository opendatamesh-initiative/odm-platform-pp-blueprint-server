# Blueprint version REST API (v2) — CRUD, search (short DTO)

**Purpose:** This document is the **development guide for code logic**. It explains why and how; the companion `**specs.md`** in this folder defines testable requirements (Gherkin). Implementers follow this proposal when writing service/repository code and use `**specs.md**` when writing integration tests.

**Test-first workflow (expected):** Derive `**specs.md**` from this proposal (and from `**BlueprintVersionsController**`). Implement `**BlueprintVersionsControllerIT**` against `**specs.md**`. TestsTestRestTemplate  **should fail** until `**BlueprintVersionCrudServiceImpl**`, `**BlueprintVersionQueryServiceImpl**` (and related repository specifications, `**BlueprintVersionSearchOptions**`, mappers, optional `**BlueprintVersionShort**` projection path) are complete; then implement until tests pass.

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

Clients can create, read, update, delete blueprint versions and list/search them with pagination and filters, using full `**BlueprintVersionRes**` for single-resource operations and `**BlueprintVersionShortRes**` for list/search responses, as documented in `**specs.md**`.

## Scope

- **In scope**
  - `**BlueprintVersionCrudServiceImpl**`: full CRUD + mapping `**BlueprintVersion` ↔ BlueprintVersionRes`**, **`validate`/`reconcile`**, **`getRepository`**, **`getSpecFromFilters`** (even if some filters are deferred, document in **`specs.md`**).
  - `**BlueprintVersionQueryServiceImpl`**: `**findAllResourcesShort**` (and internal `**findAllShort**` if needed) with `**Specification**` from `**BlueprintVersionSearchOptions**`, mapping to `**BlueprintVersionShortRes**`.
  - JPA repository for `**BlueprintVersion**` (if not already complete): extends `**PagingAndSortingAndSpecificationExecutorRepository**`.
  - `**BlueprintVersionsControllerIT**` scenarios covered by `**specs.md**` (happy paths and key error cases: 404, 400 where applicable).
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

- All Gherkin scenarios in `**changes/blueprint_version/specs.md`** are implemented in `**BlueprintVersionsControllerIT**` with traceability to the spec.
- With implementations complete, create → get-by-uuid → search contains entry → update → delete → get **404** holds for the flows defined in `**specs.md`**.
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