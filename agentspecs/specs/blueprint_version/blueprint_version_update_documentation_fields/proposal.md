# Blueprint version — editor update of name and description (published version)

**Purpose:** This document is the **development guide for code logic**. It explains why and how; the companion `specs.md` defines testable requirements (Gherkin). Implementers should follow this proposal when writing code and use `specs.md` when writing tests.

**Note:** Every bullet list below is **variable length** (0 to N items).

---

## Context

- `**BlueprintVersionRes`** (`src/main/java/org/opendatamesh/platform/pp/blueprint/rest/v2/resources/blueprintversion/BlueprintVersionRes.java`) exposes a published blueprint version, including human-facing `**name**` and `**description**`, plus manifest-related fields (`readme`, `tag`, `spec`, `specVersion`, `versionNumber`, `content`), parent `**blueprint**`, and audit fields `**createdBy**` / `**updatedBy**` (and timestamps from `VersionedRes`).
- Today, a **full** update exists on `BlueprintVersionsController` (`PUT` with a complete `BlueprintVersionRes`), which is appropriate for flows that replace the whole resource but is **too broad** for an editor who should only adjust **documentation-style** labels (`name`, `description`) on a version that is **already persisted** (published or otherwise stored).
- There is no dedicated, narrowly scoped operation that updates **only** `name` and `description` while leaving manifest content, tags, spec metadata, and parent blueprint links unchanged, and that applies **consistent audit attribution** for the edit.

## Goal

A user can update **only** the blueprint version’s `**name`** and `**description**`. All other blueprint version fields remain unchanged by this operation. On success, the persistence layer must record **who** performed the change setting the updatedBy. `createdBy` remains the **original creator** of the version (it is **not** overwritten by the client and does not change as a result of this edit); the API response must reflect the stored values for both fields.

## Scope

- **In scope**
  - A dedicated HTTP operation (prefer **POST** on `BlueprintVersionsUseCaseController`, consistent with other blueprint version use cases) whose path identifies the target blueprint version (e.g. by `uuid`) and whose request body contains **at most** `name` and `description` (dedicated `*CommandRes`, **not** full `BlueprintVersionRes`).
  - Preconditions: the target blueprint version **exists** (already published / persisted).
  - Persistence: load the existing entity, apply **only** `name` and `description`, validate, save; **no** changes to `readme`, `tag`, `spec`, `specVersion`, `versionNumber`, `content`, nested `blueprint`, or `uuid` through this path.
  - Set `updatedBy` from the payload on successful save; `createdBy` unchanged from the row before the request.
  - Validation for `name` / `description` (required/optional, max length, etc.) in use case layer; invalid input → **400 Bad Request**.
  - Integration tests aligned with `specs.md` (e.g. extending `BlueprintApplicationIT` / patterns used by `BlueprintVersionsControllerIT`).
- **Out of scope**
  - Replacing or changing manifest `**content`**, `**tag**`, `**versionNumber**`, or spec fields through this endpoint.
  - Changing the parent blueprint association.
  - Changing how the generic `**PUT**` on `BlueprintVersionsController` behaves for callers that send a full body.

## Proposed direction

- **Layering (per `agentspecs/guidelines/USE_CASE_IMPLEMENTATION.md`):** Add a thin REST endpoint on `BlueprintVersionsUseCaseController` that delegates to `BlueprintVersionUseCasesService`. Map request DTO → domain **command** (record) carrying only the allowed strings and blueprint version identity (e.g. UUID); run a **use case** built by a `@Component` **factory**; persistence through an **outbound port** that loads and updates the existing `BlueprintVersion` entity inside `TransactionalOutboundPort` where appropriate.
- **Request shape:** Introduce e.g. `UpdateBlueprintVersionDocumentationFieldsCommandRes` with **only** `name` and `description` (optional-field rules as per product). Clients must **not** send audit or manifest fields here; those are server-controlled or unchanged.
- **Response:** Return **200 OK** with the **full** `BlueprintVersionRes` so clients see updated `name` and `description`, unchanged technical fields, and audit fields with `**updatedBy`** reflecting the actor and `**createdBy**` unchanged.
- **Timestamps:** If the persistence layer updates `updatedAt` on save (via `VersionedRes` semantics), that is acceptable; scenarios in `specs.md` may assert `updatedAt` is not before the previous value where test clocks allow.
- **Endpoint that will be used**: /api/v2/pp/blueprint/blueprints-versions/update-documentation-fields
- **Transactional behavior:** Single transaction for the version row update.

## Success criteria

- Gherkin scenarios in `specs.md` are implemented as automated tests and pass after implementation.
- User can change version `name` and/or `description`; a subsequent `GET` shows the new values and **unchanged** manifest fields, parent blueprint reference, and `**createdBy`**; `updatedBy` matches the user that update the blueprint version.
- Invalid payloads return **400**; version data unchanged on failure.

