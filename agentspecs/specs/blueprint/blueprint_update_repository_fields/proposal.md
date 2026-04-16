# Blueprint — use-case update of documentation and repository configuration

**Purpose:** This document is the **development guide for code logic**. It explains why and how; the companion `specs.md` defines testable requirements (Gherkin). Implementers should follow this proposal when writing code and use `specs.md` when writing tests.

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
  - Use-case package: command (domain types), presenter, factory, outbound ports — **per `agentspecs/guidelines/USE_CASE_IMPLEMENTATION.md`** — persistence port loads the existing `**Blueprint**`, applies allowed mutations, saves **outside** the generic overwrite template (direct repository / dedicated service method / entity merge inside the port).
  - **Validation:** Run **semantic validation** on the aggregate (or merged repo) using the **same rules** as **RegisterBlueprintSemanticValidationOutboundPortImpl**. Run **structural / required / length / enum** validation consistent with **BlueprintServiceImpl**. For now, duplicate the validation rules when necessary and don't reuse the validation that are already implemented but use the same logic.
  - Transaction boundary via `**TransactionalOutboundPort**` where other use cases do.
  - Integration tests per `**specs.md**`.
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

- Gherkin scenarios in `**specs.md**` pass as integration tests.
- Successful POST updates persisted `**displayName**` / `**description**` and, when provided, repository fields; `**GET**` matches.
- Invalid repo URLs, SSH URLs, unsafe paths, invalid provider/owner types, missing required repo fields, or invalid blueprint fields → **400**; no partial persist.
- Semantic behavior matches register (`**RegisterBlueprintSemanticValidationOutboundPortImpl**`) for the same inputs on repo fields.
- **No** use of `**overwriteResource**` / generic CRUD update path for this capability.
