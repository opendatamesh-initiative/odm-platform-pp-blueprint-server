# SPDD Analysis: Blueprint lineage metadata in the data product descriptor (backend + UI)

## Original Business Requirement

I want to store some blueprint metadata to track Blueprint LIneage inside the data product descriptor.  
This metadata should contain informations about:

- The Bluprint Version used.
- The parameter values used to instantiate the data product.

To do that we should:

- Extend the specification model on the parser repository
- Import the new version of the parser repository into the blueprint service
- Modify the instantiate use case of the blueprint service by parsing and inspecting the descriptor file after the template has been instantiated, then adding the metadata before pushing to the data product repo.

### Scope addition: UI (blindata-ui)

In the ui repository should be update the descriptorSdk of the specification to accomodate the new blueprint extension.
Then in the builder homepage, it should be displayed as a readonly a reference on the blueprint lineage, so I can know which blueprint has been used and if i click i go back to that blueprint version detail page

## Future Developments (Out of Scope)

 The following enhancements are identified but not included in the current scope.

### Protected Resource Policy Extensions

The `protected resource` section in the blueprint manifest must be extended to support two additional policy types:

#### A. File Immutability Policy

**Objective:** Ensure that generated data products are consistent with the original blueprint artifacts.

**Description:**

- At blueprint publication time:
  - Compute hashes for selected files/folders.
- At data product level:
  - Recompute hashes on the corresponding files/folders using the repository state at the tagged version.
- Verification:
  - Ensure that both hashes match.

**Outcome:**

- Guarantees that no unintended modifications occurred between blueprint definition and data product realization.

#### B. Parameter Sanity Check Policy

**Objective:** Validate that the data product is correctly derived from the declared parameters.

**Description:**

1. Compute hashes of selected files/folders from the data product repository at a given version tag.
2. Re-instantiate the blueprint using:
  - The recorded blueprint version
  - The stored parameter values
3. Compute hashes on the re-instantiated output.
4. Compare hashes for consistency.

**Outcome:**

- Ensures that:
  - The parameters declared in the metadata are correct.
  - The data product faithfully reflects the blueprint instantiation.

## Domain Concept Identification

### Existing Concepts (from codebase)

- **Blueprint version (persisted)**: A versioned blueprint artifact (`BlueprintVersion`) tied to a blueprint, with fields such as semantic `versionNumber`, manifest `content` (JSON), `spec` / `specVersion`, and optional `tag` — represents the published blueprint snapshot used for instantiation.
- **Instantiation command**: `InstantiateBlueprintVersionCommand` carries `blueprintName`, `blueprintVersion` (string selector used with persistency), `blueprintParameters` as `Map<String, JsonNode>`, target repositories, auth headers, and commit author fields — this is the authoritative source of caller-supplied parameter values.
- **Resolved parameter set for templating**: `InstantiateBlueprintVersionTemplatingOutboundPortImpl#retrieveFullListOfParametersAndValues` merges manifest-declared parameters with request values and manifest defaults — this is the effective parameter map applied to Velocity and is the natural basis for “parameter values used to instantiate” metadata.
- **Instantiation pipeline (monorepo, no composition)**: `InstantiateBlueprintVersion` loads the blueprint version, validates manifest and parameters, initializes Git, clones sources and targets, runs `monorepoNoCompositionRenderAndCopy` on the templating port, then `commitAndPush` per target path — the extension point for “after template instantiated, before push” sits between templating completion and Git push (or equivalently as a final templating phase before leaving the working tree).
- **Descriptor template location**: `BlueprintRepo.descriptorTemplatePath` documents where the descriptor template lives in the blueprint repository; after Velocity processing, the rendered descriptor is expected at the sibling path of each `.vm` template (same pattern as other templates).
- **Blueprint-side artifact relocation**: `.odm/blueprint/` is already used to relocate README and persist the stored manifest YAML into the instantiated tree — establishes a precedent for Open Data Mesh–owned metadata alongside the product repo, separate from the data product descriptor file itself.
- **Data product descriptor (spec side)**: The parser library models the root as `DataProductVersion` with nested `Info` and other components; `Parser` supports deserialize/serialize round-trips for governance of the descriptor document structure.
- **Extensibility in the parser model**: `ComponentBase` carries `additionalProperties` with Jackson any-getter/any-setter, so unknown or extension fields can be represented in memory — relevant when deciding between a strictly typed new field versus spec-aligned extension keys until the specification text catches up.
- **Builder descriptor SDK (TypeScript)**: Under `src/pages/dataops/builder/descriptorSdk/`, the `DataProductVersion` class implements `fromRaw` / `toRaw` with an explicit allowlist of known root keys and defers other keys to `ComponentBase`-style handling — today blueprint-specific descriptor extensions are not first-class fields on this model; editor flows consume parsed descriptors via `DescriptorEditorShell` and adapters.
- **Builder home page**: `BuilderHomePage` / `BuilderHomeDescriptorContent` compose the data product builder landing experience (title, devops card, release versions, Git selection toolbar, descriptor warnings, and `BuilderHomeDescriptorPreview` once the descriptor is loaded in Redux under `state.dataops.builder.selectionToolbar.dataProductDescriptor`).
- **Blueprints navigation and routes**: `useBlueprintsNavigation` builds URLs under `/dataops/agent/:agentUuid/odm/:configUuid/blueprints/`; `BlueprintsRoutes` exposes blueprint **detail** at `detail/:blueprintId` (version choice is driven inside that page rather than a dedicated `detail/:blueprintId/versions/:versionUuid` “version detail” URL).

### New Concepts Required

- **Blueprint lineage (descriptor-contained)**: A documented block of metadata inside the instantiated data product descriptor that records provenance from blueprint to data product — relates to `BlueprintVersion`, the resolved instantiation parameters, and the **single** on-disk descriptor file on the **root** target repository (the only place the data product descriptor lives for this flow).
- **Specification-level representation of lineage**: A first-class or otherwise normative place in the data product descriptor information model (parser repository) for that lineage block — relates to `DataProductVersion` / `Info` (or another appropriate spec-level container) so that tools sharing the parser treat lineage consistently rather than ad hoc JSON editing alone.
- **Descriptor SDK lineage projection (UI)**: A TypeScript mirror of the lineage shape on `DataProductVersion` (or the chosen spec node) so the builder and other clients parse/serialize the extension consistently with the Java parser — relates to the same JSON keys as the backend and to `fromRaw` / `toRaw` known-key lists.
- **Builder home lineage affordance**: A read-only summary control on the builder home that surfaces “which blueprint produced this descriptor” and deep-links back into the blueprints area — relates to descriptor content already loaded for preview and to blueprint routing utilities.

### Key Business Rules

- **Provenance completeness**: Recorded metadata must identify the `**BlueprintVersion` entity** used for instantiation by persisting **at least its natural keys** (the business identifiers that uniquely locate that version in the platform, e.g. parent blueprint identity plus version discriminator), together with stable technical identifiers where useful (e.g. the version `uuid` for API and UI deep-links). Parameter values must reflect what was applied during instantiation (including defaults merged from the manifest where applicable), aligned with how the service actually rendered templates.
- **Descriptor format (JSON and YAML)**: Instantiated descriptor files may be **JSON or YAML**; both are in scope. Parsing and serialization go through the descriptor parser so the same in-memory model covers either format.
- **Single descriptor location**: Lineage is written to the **one** data product descriptor associated with the **root** blueprint repository; non-root target repositories do not carry a separate descriptor for this feature.
- **Specification change scope (code only)**: The “specification model” evolves **only in repository code** (`odm-specification-dpdescriptor-parser`, consumers in `odm-platform-pp-blueprint-server` and `blindata-ui`). **Public specification documents on the website are explicitly out of scope** for this iteration.
- **Builder navigation scope**: Lineage-driven navigation from the builder operates **only within the same** DataOps **agent** and **config** as the current session (same `agentUuid` / `configUuid` used for `buildUrl`); cross-agent or cross-config deep links are out of scope.
- **Descriptor validity**: After embedding lineage, the descriptor must remain a valid data product descriptor according to the extended specification model and the parser’s deserialize/serialize expectations.
- **Ordering relative to Git push**: Lineage must be written to the descriptor in the working copy after Velocity rendering and before `commitAndPush`, so the committed data product state includes lineage.
- **Out-of-scope policies**: Future immutability and parameter sanity-check policies depend on stable lineage records but are not part of this scope — the lineage design should not preclude storing enough version and parameter detail to support those policies later.
- **Read-only lineage in the builder**: The builder home must not treat lineage as an editable descriptor subsection in v1; it is provenance for orientation and navigation (editing, if ever required, is a separate product decision).
- **Discoverability when lineage is absent**: Products instantiated before this feature or outside blueprint instantiation must show an empty or “not from blueprint” state without breaking the home layout.

## Strategic Approach

### Solution Direction

- Treat this as a **cross-repository** change: extend the **parser/specification model** and publish (for now only locally with a mvn install) a new parser version; then integrate that artifact into **odm-platform-pp-blueprint-server** and extend the **instantiate** use case so that, for the **root** target working tree only, the service locates the **single** rendered data product descriptor, parses it with the shared parser (from JSON or YAML via the parser pipeline), attaches lineage (`BlueprintVersion` natural keys + `uuid` and resolved parameters), serializes it back, and only then proceeds to Git commit/push for that tree — matching the existing high-level flow “validate → clone → render → push” already implemented in `InstantiateBlueprintVersion` (other targets are unchanged for descriptor lineage).
- Reuse established patterns: internal manifest handling already mirrors the descriptor parser approach (`ManifestParserFactory` aligned with `ParserFactory`); descriptor handling should follow the same “factory + parse + serialize” spirit for consistency.
- Keep **monorepo / no composition** as the primary supported instantiation path unless product requirements explicitly demand parity for other manifest strategies (today templating throws `UnsupportedOperationException` outside that case).
- Extend **blindata-ui** in parallel: update `**descriptorSdk`** so the lineage block round-trips with the same JSON contract as **odm-specification-dpdescriptor-parser**; on the **builder home**, render a **read-only** lineage reference sourced from the already-loaded descriptor (same Redux path as preview), with a **link** into the blueprints experience using stable identifiers persisted in lineage (blueprint id + blueprint version uuid or equivalent), **same agent/config session only**.

### Key Design Decisions

- **Where lineage lives in the information model (parser)**: Choosing between a dedicated typed property on `DataProductVersion` (or nested under `Info`) versus a spec-namespaced extension key carried through `ComponentBase` additional properties involves trade-offs between **spec clarity, JSON Schema/documentation alignment, visitor coverage, and migration cost** → **Recommendation**: introduce an explicit, typed lineage object on the appropriate spec node (with versioning of the lineage sub-schema if needed) so downstream tools and the future policy workstream have a stable contract; use `additionalProperties` only if the formal spec must stay unchanged in the short term.
- **Which blueprint version fields to persist**: **Resolved** — lineage must denote the `**BlueprintVersion` entity** used. Persist **at least the natural keys** required to uniquely identify that entity in the platform (parent blueprint identity plus version-level business keys such as `versionNumber` / published identity fields as defined on the entity), and **include the version `uuid`** as the stable technical identifier for UI and APIs. Optional supplementary fields (e.g. display `name`, source `tag`) may be added if useful for display, without replacing natural keys + uuid.
- **Parameter payload shape**: Values originate as `JsonNode` and include structured types → **Recommendation**: persist them in lineage as JSON-compatible structures (object/array/scalars) matching the resolved map used for Velocity, avoiding lossy stringification; document ordering/stability if comparisons are anticipated.
- **Dependency integration**: The blueprint server **POM already declares** the GitHub Packages repository for `odm-specification-dpdescriptor-parser` but **does not declare the parser dependency** → adding the dependency is required; **trade-off**: align Jackson versions between Spring Boot’s managed Jackson and the parser’s declared Jackson (parser POM pins older Jackson) to avoid subtle serialization issues → **Recommendation**: rely on Spring Boot’s dependency management for runtime Jackson where possible and bump parser alignment in the parser repo if integration tests show conflicts.
- **Descriptor path resolution**: Use `BlueprintRepo.descriptorTemplatePath` consistently with templating (normalize path, resolve rendered file after `.vm` removal). **Resolved scope**: enrichment applies to the **single** root data product descriptor only (not multiple targets); multi-file `$ref` graphs remain out of scope unless separately prioritized.
- **Where the builder reads lineage from**: **Resolved** — **descriptor-only** for lineage payload (parse content already in Redux). **Resolved** — use the **current DataOps session** (Redux-selected agent and config) for `buildUrl`; **same agent/config only**, no cross-context navigation. **Do not** reuse legacy private descriptor keys (`BlueprintDataProductAdditionalProperties`).
- **“Blueprint version detail page” navigation**: Today the primary blueprint **detail** route is `detail/:blueprintId` with in-page version selection; there is no first-class route segment for “open this exact published version” in `BlueprintsRoutes` → **Recommendation**: persist `blueprintId` (uuid) and `blueprintVersionUuid` (or equivalent) in lineage JSON, navigate to `buildUrl('detail/' + blueprintId)`, and either (a) pass `location.state` / query param (`?versionUuid=`) so `BlueprintsDetailPageContent` can pre-select the version, or (b) add an explicit version detail route — (a) is smaller; (b) is more bookmarkable.
- **UI ↔ Java parser contract**: Keep field names and nesting identical across parser Java, `descriptorSdk` TypeScript, and any OpenAPI/JSON Schema docs → **Recommendation**: single short design note (or ticket) listing canonical JSON paths for lineage to avoid drift.

### Alternatives Considered

- **Write lineage only under `.odm/blueprint/` (sidecar) without touching the descriptor)**: Rejected for this requirement because the business ask is explicitly to store lineage **inside the data product descriptor**; sidecar files remain valuable for other metadata but do not satisfy the stated placement.
- **Raw JSON/YAML patch without parser**: Rejected as the primary approach because the requirement calls for extending the **specification model** in the parser repository; bypassing the parser risks drift from the canonical model and weaker validation.
- **Inject lineage inside Velocity templates only**: Rejected as the sole mechanism because it duplicates logic across blueprints, is easy to omit or get wrong per template, and does not centralize enforcement in the platform service the way the requirement describes.
- **Builder-only lineage (no descriptor change)**: Rejected because the business requirement explicitly embeds lineage in the **descriptor** and the UI must reflect that contract, not a shadow copy stored only in the UI.
- **Legacy private descriptor keys (`BlueprintDataProductAdditionalProperties`) for navigation**: Rejected — legacy pattern; cross-area links must rely on the canonical lineage block and/or current session context, not ad hoc `_`-prefixed additional properties on the descriptor.

## Risk & Gap Analysis

### Resolved requirement decisions

The following items were previously ambiguous; they are **settled** for this initiative:


| Topic                                   | Decision                                                                                                                                                                                                                       |
| --------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **“Blueprint version used”**            | Refers to the `**BlueprintVersion` entity**. Lineage must store **at least its natural keys** (identifiers that uniquely locate that version in the platform), plus the version `**uuid`** as stable technical id for APIs/UI. |
| **Descriptor format**                   | **JSON and YAML** are both required. Parsing and serialization are handled through the **descriptor parser** model so format is not a separate manual path.                                                                    |
| **Multiple target repositories**        | The data product descriptor exists **only on the root** blueprint target; there is **exactly one** descriptor file to enrich per instantiation.                                                                                |
| **Specification model vs public docs**  | Update **code only** in `odm-specification-dpdescriptor-parser`, `odm-platform-pp-blueprint-server`, and `blindata-ui`. **No** change to the **public specification document on the website** in this scope.                   |
| **Agent/config for builder deep-links** | Navigation is **in-session only**: same **agent** and same **ODM config** as the builder’s current context when building `buildUrl` to the blueprint detail view.                                                              |


### Edge Cases

- **Missing or misconfigured descriptor path**: If `descriptorTemplatePath` does not yield a rendered file after templating, the service must define fail-fast vs skip-with-warning behavior.
- **Invalid post-template descriptor**: Template errors could produce **JSON or YAML** that does not deserialize to `DataProductVersion` — instantiation should surface a clear error before push.
- **Large parameter payloads**: Very large `JsonNode` values could bloat the descriptor; policy on size limits or redaction (secrets) is not stated.
- **Non-monorepo or composed blueprints**: Templating is not implemented for those manifest layouts; lineage in descriptor would not apply until those paths exist.
- **Descriptor loaded but preview hidden**: `BuilderHomeDescriptorContent` can hide the preview when warnings apply (`shouldShowDescriptorWarnings`); lineage UI must decide whether to show anyway, only when preview shows, or in the header row — needs UX choice.
- **Stale lineage after manual Git edits**: If a user edits the descriptor outside the builder, lineage display should reflect Git state after refresh; conflicts between lineage and reality are out of scope for v1 but possible.

### Technical Risks

- **YAML vs JSON on disk in blueprint-server**: Both formats are in scope; the service must **detect or infer** format when reading the rendered file and feed the parser consistently (e.g. YAML parse → `JsonNode` then existing `Parser` APIs) so behavior matches the UI and Java tests.
- **Serialize round-trip fidelity**: `ParserImpl` deep-copies and runs extension handlers; lineage placement must not drop unknown fields other teams rely on → mitigate with golden-file tests on representative descriptors.
- **Async Git callback structure**: `cloneRepositories` passes a callback into templating and then loops `commitAndPush`; any new step must preserve ordering and error propagation inside that callback chain.
- **Triple-repo contract drift**: Java parser, TypeScript `descriptorSdk`, and UI presentation can diverge if not updated in lockstep → mitigate with shared JSON examples in tests/fixtures and a short contract checklist in review.
- **Split router import surface**: Blueprints navigation (`useBlueprintsNavigation`) imports from `react-router-dom`, while builder routes (`OdmDataProductBuilderRoutes`, `BuilderHomePage`) import `useParams` / `useHistory` from `react-router` — lineage deep-link code must follow the conventions of the subtree it runs in to avoid broken navigation.

### Acceptance Criteria Coverage

The requirement does not number explicit acceptance criteria; the table below maps **implied** criteria from the stated scenario and delivery steps.


| AC# | Description                                                                      | Addressable? | Gaps/Notes                                                                                                                                               |
| --- | -------------------------------------------------------------------------------- | ------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | Store blueprint lineage metadata inside the instantiated data product descriptor | Yes          | Root descriptor only; parser model + path from `descriptorTemplatePath` after `.vm` removal.                                                             |
| 2   | Metadata includes which blueprint version was used                               | Yes          | Store `BlueprintVersion` natural keys + `uuid` (see Resolved requirement decisions).                                                                     |
| 3   | Metadata includes parameter values used for instantiation                        | Yes          | Use resolved parameter map (including defaults); clarify redaction and size.                                                                             |
| 4   | Extend specification model in parser repository                                  | Yes          | Parser code + version bump; **no** public website spec doc update in scope.                                                                              |
| 5   | Import new parser version into blueprint service                                 | Yes          | POM repository exists; explicit dependency and version property still needed.                                                                            |
| 6   | Instantiate use case: parse descriptor after templating, add metadata, then push | Yes          | Fits between `monorepoNoCompositionRenderAndCopy` and `commitAndPush` in `InstantiateBlueprintVersion`; may warrant a small dedicated outbound port for testability.          |
| 7   | Future policy extensions (immutability, parameter sanity)                        | Out of scope | Current lineage design should still record enough data to support them later.                                                                            |
| 8   | Extend blindata-ui `descriptorSdk` for the new blueprint lineage extension       | Yes          | Update `DataProductVersion` (or chosen node) `fromRaw`/`toRaw` known keys and tests under `descriptorSdk/tests/` per existing README guidance.           |
| 9   | Builder home shows read-only blueprint lineage reference                         | Yes          | Placement: e.g. new card/row near `BuilderHomeDescriptorPreview` or title area; only when descriptor parse exposes lineage.                              |
| 10  | Click lineage → navigate to blueprint version detail                             | Yes          | Same agent/config only; persist version `uuid` + blueprint id; use `detail/:blueprintId` + query/state to pre-select version (see Key Design Decisions). |


