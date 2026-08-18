# SPDD Analysis: Blueprint update of documentation and repository fields

## Proposal

**Purpose:** This document is the **development guide for code logic**. It explains why and how; the Spec section below defines testable requirements (Gherkin). Implementers should follow this proposal when writing code and use the Spec section when writing tests.

**Note:** Every bullet list below is **variable length** (0 to N items). The folder name `blueprint_update_repository_fields` reflects that this use case may change **blueprint** presentation fields and **nested repository configuration** together.

---

## Context

- `**BlueprintRes`** exposes `**displayName**`, `**description**`, `**name**`, `**uuid**`, optional nested `**blueprintRepo**` (`BlueprintRepoRes`), and audit fields.
- Generic blueprint update exists as `**PUT** /api/v2/pp/blueprint/blueprints/{uuid}` on `**BlueprintController**`, delegating to `**BlueprintService**` / `**GenericMappedAndFilteredCrudService**` `**overwriteResource**`. That path is **not** the implementation target for this feature.
- **Register blueprint** (`RegisterBlueprint` use case) validates a new blueprint in two layers: (1) **semantic** checks on repository URLs and paths in `**RegisterBlueprintSemanticValidationOutboundPortImpl**` (HTTP/SSH URLs, provider base URL, path safety — no `..` or backslashes); (2) persistence via `**BlueprintService.create**`, which runs `**BlueprintServiceImpl.validate**` (required fields, lengths, provider/owner types, repository required fields when a repo is present).
- The product needs a **single dedicated use case** that can update, in one transaction: blueprint **`displayName`** and **`description`**, and the **full repository configuration** when a nested repository is part of the command, with **the same quality bar as registration** (semantic + structural/business validation), **without** going through CRUD overwrite.

## Goal

A caller invokes **one POST use-case endpoint** to update an **existing** blueprint’s **`displayName`**, **`description`**, and optionally **replace or set nested `blueprintRepo` configuration** from the request body. Validation must cover **all submitted repository-related values** using rules **equivalent to registration**: semantic rules aligned with **RegisterBlueprintSemanticValidationOutboundPortImpl**, and required/length/enumeration rules aligned with **BlueprintServiceImpl** blueprint and repository validation (invalid URLs, paths, provider types, missing required repo fields, etc. fail before persistence). Blueprint **`uuid`** and **`name`** remain **identifiers** supplied by context (path) and existing row — they are **not** changed by this use case. **GenericCrudService** / **overwriteResource** must **not** implement this feature.

## Scope

- **In scope**
  - New REST entry on `**BlueprintUseCaseController**` (or equivalent), **POST**, path as agreed (e.g. `**/api/v2/pp/blueprint/blueprints/update-documentation-fields**`).
  - Dedicated `**CommandRes**` carrying `**displayName**`, `**description**`, and an optional nested **repository configuration** DTO mirroring what validation needs (same conceptual shape as `**BlueprintRepoRes**` / entity fields when repo is updated).
  - Use-case package: command (domain types), presenter, factory, outbound ports — **per `spdd/norms/USE_CASE_IMPLEMENTATION.md`** — persistence port loads the existing `**Blueprint**`, applies allowed mutations, saves **outside** the generic overwrite template (direct repository / dedicated service method / entity merge inside the port).
  - **Validation:** Run **semantic validation** on the aggregate (or merged repo) using the **same rules** as **RegisterBlueprintSemanticValidationOutboundPortImpl**. Run **structural / required / length / enum** validation consistent with **BlueprintServiceImpl**. For now, duplicate the validation rules when necessary and don't reuse the validation that are already implemented but use the same logic.
  - Transaction boundary via `**TransactionalOutboundPort**` where other use cases do.
  - Integration tests per the Spec section.
- **Out of scope**
  - Implementing this behavior inside `**BlueprintController.put**` or `**overwriteResource**`.
  - Changing blueprint **`name`** or **`uuid`** through this endpoint.
  - Registering a brand-new blueprint (still `**RegisterBlueprint**`).

## Proposed direction

- **Command → domain:** Map `**CommandRes**` to a domain command holding blueprint uuid (from path), `**displayName**`, `**description**`, and optional nested repo data; merge onto the **loaded** `**Blueprint`** entity (preserve `**uuid**`, `**name**`, and repo `**uuid**`/keys as per persistence rules).
- **Repository update semantics:** If the command **omits** nested repository data, **only** `**displayName**` and `**description**` change; existing `**BlueprintRepo**` row and fields stay as stored. If the command **includes** nested repository configuration, **update** (or attach) the repository to match the validated payload; document whether partial repo objects are allowed or the client must send a **complete** repo shape when updating repo (pick one; tests follow).
- **Validation order:** Load blueprint → merge allowed fields from command → **validate** (structural then semantic, or semantic then structural — choose one order; both must run before save) → persist → present `**BlueprintRes**`.
- **Response:** **200 OK** with full `**BlueprintRes**` reflecting persisted state.
- **Errors:** **400** validation failures; **404** unknown blueprint uuid.

## Success criteria

- Gherkin scenarios in the Spec section pass as integration tests.
- Successful POST updates persisted `**displayName**` / `**description**` and, when provided, repository fields; `**GET**` matches.
- Invalid repo URLs, SSH URLs, unsafe paths, invalid provider/owner types, missing required repo fields, or invalid blueprint fields → **400**; no partial persist.
- Semantic behavior matches register (`**RegisterBlueprintSemanticValidationOutboundPortImpl**`) for the same inputs on repo fields.
- **No** use of `**overwriteResource**` / generic CRUD update path for this capability.

## Spec

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
