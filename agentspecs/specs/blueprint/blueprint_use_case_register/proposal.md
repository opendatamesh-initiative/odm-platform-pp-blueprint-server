# Register blueprint — proposal

## Goal

Expose a dedicated use case that **registers a new blueprint with its repository configuration**. The client sends one command body; the server creates the `Blueprint` (including repository metadata) ONLY, returning the created **blueprint.**

This complements the existing **hidden** CRUD endpoint on `BlueprintController` by offering a single public “registration” flow with **additional semantic validation** on blueprint parameters (paths, URLs, etc.) that does **not** belong in generic CRUD `validate` hooks.

## REST contract


| Item           | Value                                           |
| -------------- | ----------------------------------------------- |
| Method / path  | `POST /api/v2/pp/blueprint/blueprints/register` |
| Success status | `201 Created`                                   |
| Request body   | `RegisterBlueprintCommandRes` (see below)       |
| Response body  | `RegisterBlueprintResponseRes`                  |


OpenAPI: document the endpoint on `BlueprintController` (or a dedicated `*UseCaseController` if the project splits use-case routes); use `@Schema` on the command and result types.

## Command resource (`RegisterBlueprintCommandRes`)

Package: `rest.v2.resources.blueprint.usecases.register` (or equivalent under `blueprint` aggregate).

The command must carry **everything needed** to build:

1. A new `Blueprint` — same information as `BlueprintRes` used for create (name, displayName, description, `blueprintRepo` nested object, etc.). Do **not** send a blueprint `uuid` (server-generated).
2. The initial `BlueprintVersion` — same fields as `BlueprintVersionRes` for create **except** the nested `blueprint` object and **except** `blueprintUuid` (derived after blueprint creation). Include: `name`, `description`, `readme`, `tag`, `spec`, `specVersion`, `versionNumber`, `content`, and audit fields if the API requires them (`createdBy` / `updatedBy` when applicable).

Shape (conceptual):

- `blueprint`: `BlueprintRes` (subset acceptable if documented; must match what `BlueprintService.createResource` needs).
- Version fields: either a nested object (e.g. `initialVersion`) mirroring version fields, or top-level fields grouped under a clear prefix — choose one structure and keep mappers straightforward.

## Validation split (no duplication with CRUD)


| Layer                                                          | Responsibility                                                                                                                                                                                                                                                                                                             |
| -------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `**BlueprintServiceImpl` / `BlueprintVersionCrudServiceImpl`** | Keeps existing **required fields**, **length limits**, **enums**, **reconcile** (e.g. load parent blueprint), **natural keys** (unique name / unique version per blueprint). **Do not re-implement these rules** in the use case.                                                                                          |
| **Register use case**                                          | **Additional semantic checks** only, e.g. HTTP(S) / SSH URL shape, well-formed paths for `manifestRootPath`, `descriptorTemplatePath`, `readmePath` (no `..` traversal, leading slash policy, etc.), and `providerBaseUrl` as a valid base URI. Centralize these in a small validator or port so the use case stays clear. |


If a semantic rule is already fully enforced by CRUD (same exception type and message), **do not** repeat it.

## Application architecture

Follow `agentspecs/guidelines/USE_CASE_IMPLEMENTATION.md`:

1. `**RegisterBlueprintCommand`** (record) — domain command built from `RegisterBlueprintCommandRes` via mappers.
2. `**RegisterBlueprintPresenter**` — e.g. `presentRegistered(Blueprint)` or similar; result mapped to `RegisterBlueprintResponseRes`.
3. `**RegisterBlueprint**` (implements `UseCase`) — orchestration only: run semantic validation → call outbound port(s) that delegate to `**BlueprintService.createResource**`  with `blueprintUuid` set, inside `TransactionalOutboundPort` so create in one transaction.
4. `**RegisterBlueprintFactory**` — wires ports + `TransactionalOutboundPort`.
5. `**BlueprintUseCasesService**` (or `RegisterBlueprintUseCasesService`) — maps REST DTO → command, runs factory + `execute()`, maps to `RegisterBlueprintResponseRes`.

**Controller** — thin: single method calling the use cases service; no business rules.

## Out of scope for this change

- Changing CRUD validation behavior except where needed to avoid duplication (prefer leaving CRUD as-is).
- Listing or search behavior.
- Replacing existing `POST /blueprints` or `POST /blueprints-versions` CRUD endpoints (they remain; registration is the documented public flow).
- Creating BlueprintVersion during the use case

## Testing

Integration tests:  `` dedicated `*UseCaseControllerIT` against `specs.md` (Gherkin). Trace each test to a scenario in `specs.md` via a comment on the test method.

## References

- `BlueprintController` base path: `/api/v2/pp/blueprint/blueprints`
- `BlueprintServiceImpl.validate` / `BlueprintVersionCrudServiceImpl.validate` — existing CRUD rules
- `agentspecs/guidelines/USE_CASE_IMPLEMENTATION.md` — layers and naming

