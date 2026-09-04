# SPDD Analysis: Align Code to Updated Blueprint Manifest Specification

See companion analyses `BDMD-4820-202608251703` / `BDMD-4820-202608271040` and prompts `BDMD-4820-202608261148` / `BDMD-4820-202608271455` for runtime instantiate/update behavior across all repository topologies.

## Original Business Requirement

BDMD-4820 I have updated the manifest specification, now I want to align the existing code to that specification.
Projects involved: blueprint service, blindata ui

In scope: update code to support the new specificaiton.
Out of scope: add new features not yet implemented.

---

The authoritative manifest SRS is `src/main/java/org/opendatamesh/platform/pp/blueprint/manifest/README.md`. Do not paste that document here; treat it as the single source of truth for schema fields, validation constraints, examples, and the Java parser API.

### Current schema (summary)

```yaml
targetRepositories:
  - key: main-repository
    description: ...
    isRoot: true   # exactly one entry
composition:       # module identity + parameterMapping ONLY — NO targets
  - module: storage
    blueprintName: ...
    blueprintVersion: ...
    parameterMapping:
      x: { $param: parentKey }
      y: { value: literal }
instantiation:     # typed routing list
  - type: root
    targets:
      - sourcePath: ./
        repo: main-repository
        destinationPath: ./
  - type: module
    moduleName: storage
    targets:
      - sourcePath: ./
        repo: main-repository
        destinationPath: data-plane/storage
```

Java model: `ManifestTargetRepository` (key, description, isRoot), `ManifestInstantiationEntry` (type ROOT|MODULE, moduleName, targets), `ManifestTarget` (sourcePath, repo, destinationPath), `ManifestComposition` (no targets).

---

## Domain Concept Identification

### Existing Concepts (from codebase)

- **Blueprint Manifest**: Authoritative YAML/JSON contract (`spec: odm-blueprint-manifest`) parsed by Jackson in the blueprint service (`manifest` package) and mirrored by the Blindata UI Manifest SDK (`manifestSdk`). Relationship: root document governing parameters, protection, composition, and instantiation routing for a blueprint version’s `content`.
- **Manifest Parser / SDK**: Tree-in/tree-out binding with `additionalProperties` / extension converters (Java) and `fromRaw` / serialization visitors (JS). Relationship: shared contract surface that must stay synchronized with the README schema.
- **Manifest Parameter (+ validation + ui)**: Declared inputs with `key`, `type`, `required`, `default`, `validation`, and `ui` (`group`, `label`, `description`, `formType`). Relationship: aligned with the specification; drives parameter collection UIs and instantiate-time validation.
- **Protected Resource (+ integrity)**: Immutable path/glob markers; integrity digest populated on the lineage copy in the data-product repo. Relationship: model exists in both stacks; behavior unchanged by this alignment.
- **Composition module (`composition[]`)**: Child blueprint reference (`module`, `blueprintName`, `blueprintVersion`, `parameterMapping`) without routing targets. Relationship: routing for modules lives on matching `instantiation[]` entries with `type: module` and `moduleName`.
- **Typed instantiation routing (`instantiation[]`)**: List of routing directives with `type: root` (parent blueprint files) or `type: module` (`moduleName` matching `composition[].module`), each with non-empty `targets[]` using `sourcePath` → `repo` + `destinationPath`. Relationship: separates composition identity from routing.
- **Logical repository key (`targetRepositories[]`)**: Abstract destination alias (`key`, optional `description`, `isRoot`) that the client resolves to a concrete Git repository at instantiation time. Relationship: topology vocabulary referenced by all route `repo` fields. Exactly one entry sets `isRoot: true` for lineage / descriptor / registry primary pointer.
- **Instantiation scenario (runtime)**: Derived from `targetRepositories` key cardinality + composition presence into four topologies (1→1, N→1, 1→N, N→N). Relationship: scenario detection is based on the current topology model; runtime enablement of all four is covered by companion instantiate/update analyses.
- **Target repository (API / UI)**: Instantiate/update commands pass `targetRepositories` with `targetId` (manifest `targetRepositories[].key`) and Git repository metadata. Relationship: request `targetId` reconciles with the manifest key; root vs module role lives in the manifest (`instantiation[]` entry type and `composition[]`), not on the request entry.
- **Manifest validator / autofiller**: Visitors that enforce uniqueness, `isRoot`, route `repo` references, multi-target `sourcePath`, relative paths, and related constraints against `targetRepositories`, typed `instantiation[]`, and `composition[]`.
- **Registration / scaffold templates (UI)**: Default manifest YAML emitted at blueprint registration uses `targetRepositories` + typed `instantiation[]` so newly registered blueprints are valid under the contract.
- **Instantiate & update use cases**: Clone, render, checkpoint-tag, and merge/update flows gated on scenario detection from the current schema. Relationship: schema alignment keeps models/parsers/validators/SDK on the README contract; multi-repo runtime is companion work.

### New Concepts Required

None beyond the current contract already summarized above. Topology is inferred from `targetRepositories` cardinality (not a strategy enum). Creation policies (`create_if_missing` / `must_exist`) and physical URLs remain orchestrator-owned outside the manifest.

### Key Business Rules

- Manifest remains focused on provisioning/orchestration; DP descriptor path stays in the platform Blueprint model, not the manifest.
- Client resolves each `targetRepositories[].key` and supplies mapped repositories to the instantiate endpoint; creation policies are not part of the manifest.
- Lineage manifest copy is written only to the target whose key has `targetRepositories[].isRoot: true`.
- Validation must enforce: unique repository keys; exactly one `isRoot: true`; unique composition module aliases; composition/instantiation module alignment; every route `repo` references a declared key; non-empty `instantiation[]` with exactly one `type: root`; non-empty entry `targets`; multi-entry `targets` require explicit `sourcePath`; relative paths only (no absolute / `..`).
- Parameter `type` defaults to `string` when omitted; `required` defaults to false; `schemaRef` remains explicitly unsupported.
- **Scope guardrail (this analysis):** Align models, parsers, validators, autofillers, fixtures, SDK, and currently implemented flows to the README schema. Runtime expansion for composition/polyrepo instantiate and update is companion work (`BDMD-4820-202608251703` / `-202608271040`).

## Strategic Approach

### Solution Direction

Treat the README as a **breaking schema alignment** across the blueprint service and Blindata UI. Use `targetRepositories`, typed `instantiation[]`, and `composition[]` (module identity only) as the sole instantiation vocabulary. Re-ground scenario detection on `targetRepositories` cardinality + composition presence. Parameters, protected resources, and UI metadata remain as-is aside from any incidental coupling to removed fields. Update test YAML examples, registration scaffolds, and process docs so authors and clients only see the current contract.

High-level data flow (unchanged at product level): publish/register blueprint version with manifest content → UI deserializes manifest → collect parameters and resolve repository key(s) → instantiate/update endpoints validate and render into mapped target repository(ies).

### Key Design Decisions

- **Hard cut to the current schema (no dual-read)**: Dual-read would prolong two models and ambiguous validation. → **Hard cut only.** No migration, dual-read, or special rejection path for removed vocabulary (`strategy`, postfix/`createPolicy` targets, and similar).
- **How to detect monorepo vs polyrepo**: Options include counting `targetRepositories[]`, inspecting distinct `repo` references in routes, or retaining a deprecated field. → **Derive topology from `targetRepositories` cardinality** (and/or distinct referenced keys), with composition presence selecting composition-related scenarios.
- **Instantiate/update request contract (`targetId` only)**: Spec expects targets mapped by repository key. → **`targetId` reconciles with `targetRepositories[].key`**. Root vs module is expressed by manifest `instantiation[]` entry type and `composition[]`, not by a request enum. Remove `BlueprintRepositoryLogicalType` from request/result DTOs. Minimal key-mapping on the existing list-based target payload is **in scope** for this alignment; multi-repo selection UX is companion/UI work.
- **Root target repository for lineage**: Designated via **`targetRepositories[].isRoot: true`** (exactly one entry). Not inferred from list order, covering route, or reserved key name.
- **Empty entry `targets`**: Must fail validation; each `instantiation[]` entry requires non-empty `targets`, and exactly one `type: root` entry is required.
- **Where to keep cross-repo analysis**: Spec source of truth is blueprint-server; UI SDK mirrors it. → **Single analysis artifact under blueprint-server `spdd/analysis/`**, covering both codebases for REASONS Canvas inputs.

### Alternatives Considered

- **Keep a redundant topology hint alongside `targetRepositories`**: Rejected — contradicts the specification and recreates dual sources of truth for topology.
- **Implement full polyrepo + composition instantiation as part of schema alignment**: Rejected for this analysis — runtime expansion is companion work; schema alignment must not silently expand product scope.
- **UI-only adapter that rewrites manifests into a parallel in-memory model**: Rejected — leaves the server unable to parse/validate the published contract and drifts the two stacks further apart.

## Risk & Gap Analysis

### Requirement Ambiguities

- *(Resolved)* **Stored blueprint versions with removed vocabulary**: Do not handle. Hard cut; no migration or dual-read.
- *(Resolved)* **Instantiate/update request contract**: Use `targetId` only — reconciles with manifest `targetRepositories[].key`. Drop request `type` / `BlueprintRepositoryLogicalType`; root vs module stays in the manifest.
- *(Resolved)* **“Root target repository” for lineage**: Exactly one `targetRepositories[].isRoot: true`.
- *(Resolved)* **Scope of “new features”**: Minimal key-mapping (`targetId` on existing target list) is in scope for alignment; composition/polyrepo runtime and polyrepo multi-picker UX are companion deliverables.

### Edge Cases

- **Manifest with one repository key but multiple root target path splits**: Still monorepo topology; path-splitting into the same key must honor `sourcePath` / `destinationPath`.
- **Root `instantiation[]` entry with empty `targets` and composition present**: Invalid under current structural rules (non-empty `targets` required); pure-orchestration parents are rejected.
- **Multiple `targets` without explicit `sourcePath`**: Must fail validation (default `./` not allowed when length > 1).
- **Composition `parameterMapping` literals vs parent key references**: Entries use `{ $param }` / `{ value }` object shape; parsing must accept that contract.
- **Registration scaffolds still emitting removed vocabulary**: Will produce invalid manifests the moment validators enforce the README — must be updated in the same alignment effort.

### Technical Risks

- **Wide blast radius on visitors/tests**: Instantiation model change touches Java model, visitors, extension visitor, validator, autofiller, instantiate/update ports, render service, parser tests, example YAMLs, and the entire UI Manifest SDK + repository step + registration templates. Mitigation: treat schema/model/parser/validator/fixtures as one vertical slice; then wire scenario detection; then UI SDK + consumers.
- **Duplicate UI repository-step modules**: Keep only `repositories_step/`; branch on repository-key topology helpers (`isSingleRepositoryTopology` / `soleRepositoryKey`).
- **Scenario enum still named MONOREPO/POLYREPO**: Fine as internal runtime taxonomy if derived from `targetRepositories`; risk if left coupled to a removed JSON field. Mitigation: resolve scenario only from current fields.
- **Docs (`blueprint-process.md`) drift**: Update references as part of alignment (documentation adjacent to code contract).
- **No DB migration for manifest JSON**: Manifests live as version `content` documents, not normalized tables. Mitigation: **none required** — hard cut per product decision.

### Acceptance Criteria Coverage

| AC# | Description | Addressable? | Gaps/Notes |
|-----|-------------|--------------|------------|
| AC1 | Blueprint-service manifest model and parser bind the README schema (`targetRepositories`, typed `instantiation[]`, `composition[]` without targets; route fields `repo` / `destinationPath`) | Yes | Core alignment |
| AC2 | Manifest validator enforces uniqueness, `isRoot`, repository-reference, multi-target `sourcePath`, and relative-path rules from the specification | Yes | Current structural rule set |
| AC3 | Autofiller and example/test fixtures produce/consume the current example shapes (incl. monorepo single-key default) | Yes | Includes `src/test/resources/manifest/*` and instantiate fixtures |
| AC4 | Supported runtime paths continue to instantiate and update successfully when manifests use the current shape | Yes | Scenario detection from `targetRepositories` + composition |
| AC5 | Clear errors for invalid manifests and for topologies not yet enabled in a given delivery slice | Yes | Companion analyses cover full four-topology runtime |
| AC6 | Blindata UI Manifest SDK model/parser/serializer/traverse mirrors the README schema | Yes | `ManifestTargetRepository`, `ManifestInstantiationEntry`, `ManifestTarget`, `ManifestComposition` |
| AC7 | UI instantiation repository step and validation use repository-key topology (not a strategy enum) | Yes | Polyrepo multi-picker UX remains separate scope |
| AC8 | Registration/init scaffold manifests emit `targetRepositories` + typed `instantiation[]` | Yes | `blueprintRepositoryInitTemplates` and similar |
| AC9 | Instantiate/update target entries carry `targetId` and reconcile with `targetRepositories[].key` (no request `type`) | Yes | Root/module role is manifest-side |
| AC10 | No delivery of unrelated unimplemented product features (`schemaRef`, etc.) under this ticket | Yes | Explicit out-of-scope check during REASONS/generate |
