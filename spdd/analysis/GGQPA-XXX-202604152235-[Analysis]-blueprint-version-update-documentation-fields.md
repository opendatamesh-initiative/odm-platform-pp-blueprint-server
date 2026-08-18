# SPDD Analysis: Blueprint version update of name and description

## Proposal

**Purpose:** This document is the **development guide for code logic**. It explains why and how; the Spec section below defines testable requirements (Gherkin). Implementers should follow this proposal when writing code and use the Spec section when writing tests.

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
  - Integration tests aligned with the Spec section (e.g. extending `BlueprintApplicationIT` / patterns used by `BlueprintVersionsControllerIT`).
- **Out of scope**
  - Replacing or changing manifest `**content`**, `**tag**`, `**versionNumber**`, or spec fields through this endpoint.
  - Changing the parent blueprint association.
  - Changing how the generic `**PUT**` on `BlueprintVersionsController` behaves for callers that send a full body.

## Proposed direction

- **Layering (per `spdd/norms/USE_CASE_IMPLEMENTATION.md`):** Add a thin REST endpoint on `BlueprintVersionsUseCaseController` that delegates to `BlueprintVersionUseCasesService`. Map request DTO → domain **command** (record) carrying only the allowed strings and blueprint version identity (e.g. UUID); run a **use case** built by a `@Component` **factory**; persistence through an **outbound port** that loads and updates the existing `BlueprintVersion` entity inside `TransactionalOutboundPort` where appropriate.
- **Request shape:** Introduce e.g. `UpdateBlueprintVersionDocumentationFieldsCommandRes` with **only** `name` and `description` (optional-field rules as per product). Clients must **not** send audit or manifest fields here; those are server-controlled or unchanged.
- **Response:** Return **200 OK** with the **full** `BlueprintVersionRes` so clients see updated `name` and `description`, unchanged technical fields, and audit fields with `**updatedBy`** reflecting the actor and `**createdBy**` unchanged.
- **Timestamps:** If the persistence layer updates `updatedAt` on save (via `VersionedRes` semantics), that is acceptable; scenarios in the Spec section may assert `updatedAt` is not before the previous value where test clocks allow.
- **Endpoint that will be used**: /api/v2/pp/blueprint/blueprints-versions/update-documentation-fields
- **Transactional behavior:** Single transaction for the version row update.

## Success criteria

- Gherkin scenarios in the Spec section are implemented as automated tests and pass after implementation.
- User can change version `name` and/or `description`; a subsequent `GET` shows the new values and **unchanged** manifest fields, parent blueprint reference, and `**createdBy`**; `updatedBy` matches the user that update the blueprint version.
- Invalid payloads return **400**; version data unchanged on failure.

## Spec

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
