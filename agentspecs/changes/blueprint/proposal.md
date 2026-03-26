# Blueprint REST API (v2) — CRUD and search

**Purpose:** This document is the **development guide for code logic**. It explains why and how; the companion `**specs.md`** in this folder defines testable requirements (Gherkin). Implementers follow this proposal when writing service/repository code and use `**specs.md`** when writing integration tests.

**Test-first workflow (expected):** Derive `**specs.md`** from this proposal (and from `BlueprintController`). Implement `**BlueprintControllerIT`** against `**specs.md`**. Those tests **should fail** until `BlueprintServiceImpl` (and related pieces: repository specifications, `BlueprintSearchOptions`, `BlueprintMapper`) are complete; then implement until tests pass.

**Note:** Every bullet list below is **variable length** (0 to N items). Add as many bullets as the change needs; do not pad or trim to a fixed number. Empty lists are allowed.

---

## Context

- The **Blueprint** domain is modeled by JPA entity `Blueprint` (`blueprints` table) with fields such as `uuid`, `name`, `displayName`, `description`, optional nested `BlueprintRepo`, plus auditing from `VersionedEntity` (`createdAt`, `updatedAt`).
- REST exposure is `**BlueprintController`**, base path `**/api/v2/pp/blueprint/blueprints`**, producing JSON.
- `**BlueprintService**` extends `**GenericMappedAndFilteredCrudService**` and is the single entry used by the controller for create, read-by-id, paginated search, update, and delete.
- `**BlueprintServiceImpl**` currently extends `**GenericMappedAndFilteredCrudServiceImpl**` but does **not** yet supply the abstract hooks (`getRepository`, `getSpecFromFilters`, `toRes`, `toEntity`, `validate`, `reconcile`, etc.), so the application cannot behave correctly end-to-end.
- `**BlueprintSearchOptions`** exists as a placeholder: query parameters for search must be bound and translated into a JPA `**Specification<Blueprint>`** in the service implementation.
- Integration tests should extend `**BlueprintApplicationIT`** and use `**TestRestTemplate`** in the same style as `**GitProviderControllerIT**`. Add `**RoutesV2**` enum entries (or equivalent URL helpers) for blueprint paths so tests stay maintainable.

## Goal

Clients can create, read, update, delete, and paginate/search blueprints through the v2 HTTP API, with consistent status codes, JSON bodies aligned with `**BlueprintRes**`, and filtering/sorting behavior documented in `**specs.md**`.

## Scope

- **In scope**
  - Full implementation of `**BlueprintServiceImpl`** (and any small collaborators strictly required by it).
  - JPA repository `**BlueprintsRepository`** usage with `**Specification`**-based filtering driven by `**BlueprintSearchOptions`**.
  - Mapping between `**Blueprint**` and `**BlueprintRes**` via `**BlueprintMapper**` (and nested `**BlueprintRepo**` mapping if exposed on create/read/update).
  - Validation and reconcile hooks required by `**GenericCrudServiceImpl**` (e.g. required fields, business rules agreed in `**specs.md**`).
  - `**BlueprintControllerIT**` scenarios covered by `**specs.md**` (happy paths and key error cases: 404, 400 where applicable).
- **Out of scope**
  - Unrelated controllers (e.g. git-providers).
  - Changing the public URL shape of `**BlueprintController`**.
  - Non-HTTP modules outside blueprint service (unless a dependency is unavoidable for compilation).
  - Utils, configuration, exceptions, git folders

## Proposed direction

- **Layering:** Keep `**BlueprintController`** thin; all persistence and mapping live in `**BlueprintServiceImpl`** + `**BlueprintsRepository`** + mapper/specs.
- `**BlueprintServiceImpl`** must contain all the mapping (toRes, toEntity), validation, reconcile, getSpecFromFilters, beforeCreation, beforeOverwrite, overwriteResource
- The validation inside `**BlueprintServiceImpl`** must check also the length of the fields and the required
- `**BlueprintsRepository`** must have an inner class `**Spec`** that extends `**SpecUtils`** which have method e.g hasName
- **Read one:** `**GET /{uuid}`** returns `**BlueprintRes`** or **404** when missing (`**NotFoundException`** from this module maps to 404 in global exception handling — align tests with actual handler behavior).
- **Search:** `**GET`** (collection) uses `**BlueprintSearchOptions`** + `**Pageable`**. Default sort in controller: `**createdAt` DESC**, page size **20**. Valid sort properties (per OpenAPI text): `**uuid`**, `**name`**, `**displayName**`, `**description**`, `**createdAt**`, `**updatedAt**`. Invalid sort property → 400 if the stack surfaces that; otherwise document actual behavior in `**specs.md**`.
- **Create / update / delete:** `**POST`** → **201** + body; `**PUT /{uuid}`** → **200** + body; `**DELETE /{uuid}`** → **204**. Hidden Swagger annotations do not change HTTP semantics for clients.
- **Repository integration tests:** Prefer real DB via existing test containers (`**TestContainerConfig`**) so specifications and pagination are exercised realistically.

Outcome: `**BlueprintControllerIT`** passes with no stubs left in `**BlueprintServiceImpl`** for the scenarios in `**specs.md`**.

## Success criteria

- All Gherkin scenarios in `**changes/blueprint/specs.md**` are implemented as integration tests in `**BlueprintControllerIT**`, each test referencing the scenario (comment or name).
- All integration tests in `**BlueprintControllerIT**` must coverage at least the 90% of code generated.
- All integration tests in `**BlueprintControllerIT**` must be inside rest/v2/controllers folders in test.
- With service implementation complete, `**POST**` creates a blueprint and subsequent `**GET**` by returned `**uuid**` returns the same logical data.
- `**GET**` list returns a Spring Data page JSON shape (`content`, `totalElements`, pagination metadata) and respects default sort and at least one filter defined in `**specs.md**` once `**BlueprintSearchOptions**` is populated.
- `**PUT**` updates an existing blueprint; `**GET**` reflects changes; `**DELETE**` removes it and later `**GET**` returns **404**.
- Unknown `**uuid`** on `**GET`**, `**PUT`**, `**DELETE**` yields 404 (or documented alternative if product chooses differently — then update `**specs.md**`).
- When create a blueprint with duplicate `**uuid`** then return a Conflict exception
- When create a blueprint with duplicate `**name`** then return a Conflict exception
- When create a blueprint with repository then return a blueprint with repository
- When update blueprint repository the return the updated blueprint with modified repository
- When delete a blueprint than return no content and blueprint not exists anymore
- When delete a blueprint with repository then return no content then both are deleted
- When create a blueprint (with or without repository) with invalid data then return bad request
- When create a blueprint with repository with invalid provider type than return bad request
- When create a blueprint with invalid urls then return bad request
- whenCreateBlueprintThenReturnCreatedBlueprint
- whenGetBlueprintByIdThenReturnBlueprint
- whenGetBlueprintWithNonExistentIdThenReturnNotFound
- whenSearchBlueprintsThenReturnBlueprintsList
- whenSearchBlueprintsWithFiltersThenReturnFilteredResults
- whenUpdateBlueprintThenReturnUpdatedBlueprint
- whenDeleteBlueprintThenReturnNoContentAndBlueprintIsDeleted
- whenCreateBlueprintWithInvalidDataThenReturnBadRequest
- whenUpdateBlueprintWithDuplicateNameThenReturnConflict
- whenCreateBlueprintWithRepositoryThenReturnCreatedBlueprintWithRepository
- whenUpdateBlueprintRepositoryThenReturnUpdatedBlueprintWithModifiedRepository
- whenDeleteBlueprintWithRepositoryThenReturnNoContentAndBothAreDeleted
- whenGetBlueprintWithRepositoryThenReturnBlueprintWithRepositoryDetails
- whenCreateBlueprintWithRepositoryWithInvalidDataThenReturnBadRequest
- whenCreateBlueprintWithRepositoryWithInvalidProviderTypeThenReturnBadRequest
- whenCreateBlueprintWithRepositoryWithInvalidUrlsThenReturnBadRequest