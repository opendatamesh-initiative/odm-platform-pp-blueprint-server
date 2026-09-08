# SPDD Analysis: Explicit Blueprint vs Blueprint Module Distinction

## Original Business Requirement

BDMD-4820
Extend Blueprint model to explicit the difference between a blueprint component (module) and a blueprint.

Currently:
- implicit carried on the descriptorTemplatePath field.

Add rules for blueprint components:
- cannot be instantiated alone (only when used by other blueprints)

Official names of the two blueprint types: Blueprint --> the "root"
Blueprint module --> the "component"
Is blueprintType required on every Blueprint, including hidden CRUD create? yes
Mutability after register / after first publish / after being composed: DO NOT ALLOW
(allow change ONLY on hidden crud)
May a catalog Blueprint still be composed if it has no descriptor path? NO
*Must a Blueprint have `descriptorTemplatePath yes. A module MUST NOT.
Must a component be 1→1 with empty composition even when not used as a child? yes, checked at module publish time.
Backfill of existing blank-path rows (treat them as blueprints, do not backfill)
UI: Analysis out of scope (deferred)

## Domain Concept Identification

### Existing Concepts (from codebase)

- **Blueprint**: Catalog identity of a reusable Git-backed template (`blueprints` table). Has name, display name, description, and exactly one nested repository configuration. Name is the natural key. There is currently **one** catalog type; both root templates that produce a data product and reusable modules are stored as the same concept.
- **Blueprint repository (`BlueprintRepo`)**: Git and file-pointer metadata for a Blueprint (`blueprints_repositories`). Holds provider identity, clone URLs, default branch, and three file pointers: `manifestRootPath` (required), `readmePath` (optional), `descriptorTemplatePath` (optional today). The last of these is **today’s implicit discriminator**.
- **Descriptor template path**: Repository-relative location of a Velocity data-product descriptor template. Business meaning today is overloaded: (1) “where to render the descriptor on the designated root target at instantiate/update,” and (2) “this catalog entry is a root Blueprint, not a module.” Publish, instantiate, and update-data-product all reject a composed child when this path is non-blank.
- **Blueprint version**: Immutable published snapshot of a catalog entry’s manifest content, version number, and documentation. Versions do not carry their own descriptor path; they inherit the parent row’s repository pointers. Composition looks up children by **blueprint name + version number**.
- **Composition module (manifest role)**: A child referenced from a parent version’s `composition[]` (`module` alias + `blueprintName` + `blueprintVersion`) and placed by `instantiation[]` entries with type `module`. A child used this way must already be a **published 1→1 (monorepo, empty composition)** version. This is a **use-time role inside a parent manifest**. After this change it must also match catalog blueprint type **Blueprint module**.
- **Parent / root (runtime role)**: The catalog version being published, instantiated, or used to update a data product. Instantiation designates the data-product root **target repository** with `targetRepositories[].isRoot: true`. Descriptor render and lineage apply only on that target. Catalog blueprint type **Blueprint** is the “root”; `isRoot` on a target remains a repository designation, not the catalog blueprint type.
- **Instantiation type (`root` | `module`)**: Manifest routing discriminant for *how files are copied into a target*, not *what a catalog entry is*. `type: root` always means the parent source; `type: module` always means a named composition child.
- **Register / update-documentation-fields / hidden CRUD**: Public and hidden mutation paths for the same aggregate. Register creates the catalog row; update-documentation-fields may replace repository pointers (including `descriptorTemplatePath`); hidden CRUD create/overwrite persist the same entity.

### New Concepts Required

- **Blueprint type (catalog discriminant)**: An explicit, required property of the catalog identity. Two official names: **Blueprint** (the “root”) and **Blueprint module** (the “component”). Immutable on public APIs (register, update-documentation-fields); changeable **only** via hidden CRUD overwrite. This concept does not exist on the entity, REST resource, search filters, or schema today. It must **replace** inference from `descriptorTemplatePath`, not live beside an equally authoritative path check.
- **Blueprint module**: Catalog blueprint type for a reusable composition child. Same persistence aggregate as Blueprint (not a separate table or Git repository type). Allowed uses: register, publish (with 1→1 empty composition checked at **that** publish), and be composed by a Blueprint. Forbidden uses: standalone instantiate, standalone update-data-product, owning a descriptor template, appearing as a composition child of anything that is itself a Blueprint module, composing others.

### Conceptual relationships

- Blueprint type belongs to the catalog row (**Blueprint** entity), not to `BlueprintRepo`, and not to `BlueprintVersion`. Repository pointers describe *where files live*; versions describe *what was published*; blueprintType describes *what this catalog entry is for*. All versions of one name share one blueprintType. Public APIs cannot change it; hidden CRUD overwrite can, and then every version of that name follows the new blueprintType.
- `descriptorTemplatePath` remains a **file location** on `BlueprintRepo`, constrained **by** blueprintType: **required** on a Blueprint; **forbidden** on a Blueprint module. It is not a type flag.
- Manifest `composition[].module` / instantiation `type: module` remain **roles inside a parent version**. Only catalog blueprint type **Blueprint module** may fill those roles. Catalog blueprint type **Blueprint** must never be composed, including when (illegally) missing a descriptor path.
- Instantiation `isRoot` remains a **target-repository** designation on the parent’s manifest. Catalog “root” is blueprintType **Blueprint**.

### Key Business Rules

- **Official blueprint types**: **Blueprint** = root. **Blueprint module** = component. One catalog, two blueprint types, shared aggregate, unique name space, Git configuration, and version lifecycle.
- **Blueprint type (`blueprintType`) is required on every write that creates the row**: Register, hidden CRUD create, and any other create path must receive blueprintType. Omission is invalid; the server must not infer it from `descriptorTemplatePath`.
- **Blueprint type is immutable on public APIs; hidden CRUD overwrite may change it**: Register and update-documentation-fields must not change blueprintType after create (update-documentation-fields does not carry blueprintType; a payload that tried to would be ignored or rejected). Hidden CRUD `PUT` is the **only** allowed mutation of blueprintType, including after publish and after the row has been composed. The overwrite payload must still satisfy blueprintType↔path rules for the **new** blueprintType (Blueprint requires path; module forbids it).
- **Blueprint type is authoritative**: Clients and server logic must not infer blueprintType from whether `descriptorTemplatePath` is blank.
- **A Blueprint must have `descriptorTemplatePath`**: Required, non-blank, on every create/update that carries repository configuration for blueprintType Blueprint.
- **A Blueprint module must not have `descriptorTemplatePath`**: Absent or blank. A valorized path on a module is invalid at register/update and remains a collected error if a parent tries to compose that module.
- **Only a Blueprint module may be a composition child**: Composing a catalog Blueprint is always illegal, including when it has no descriptor path.
- **A Blueprint module cannot be instantiated alone**: Instantiate and update-data-product accept a **parent** version. If that parent’s blueprintType is Blueprint module, the request fails. A module is materialized only as a composition child while instantiating or updating **from a Blueprint**. Register and publish of a module remain allowed so parents can reference a published name@version.
- **A Blueprint module must be 1→1 with empty composition at its own publish**: Checked when the **module’s** version is published, not only when a parent later uses it. A module version whose content is not monorepo with empty composition must not be published.
- **Existing rows**: Do **not** backfill blueprintType from `descriptorTemplatePath`. Treat existing rows as **Blueprint**. Do not invent a descriptor path for blank-path rows.

## Strategic Approach

### Solution Direction

Add a required catalog blueprint type on the existing Blueprint entity (**Blueprint** | **Blueprint module**) and carry it through hidden CRUD, register, read, and search. Blueprint type is set at create, frozen on public update, and changeable **only** on hidden CRUD overwrite. Constrain `descriptorTemplatePath` by blueprintType (required on Blueprint, forbidden on module). Shift composition, instantiate, update-data-product, and **module publish** off the path heuristic onto blueprintType.

Data flow stays inside current architecture: REST command → register / CRUD / update-documentation-fields validate blueprintType + path rules → persist → later publish / instantiate / update-data-product load blueprintType and apply policy (parent must be Blueprint; children must be Blueprint modules; a module’s own publish must be 1→1 empty composition). Persistence follows the existing Flyway + JPA pattern (`odm_blueprint` schema, `ddl-auto: validate`). Mapping follows existing MapStruct Blueprint ↔ `BlueprintRes` conventions. Search follows `BlueprintSearchOptions` + repository `Specs`.

Do **not** split blueprint types into two tables, two REST resources, or two version aggregates. Do **not** put blueprintType on `BlueprintRepo`. Do **not** put blueprintType on the manifest specification. Do **not** implement UI in this change (deferred).

### Key Design Decisions

- **Where blueprintType lives**: On the catalog Blueprint entity, required, shared by all versions. Not on `BlueprintRepo` (Git/file metadata). Not on `BlueprintVersion` (would allow blueprintType to flip by release).
- **How to model the discriminant**: A two-value catalog enum on Blueprint, in the same style as `BlueprintRepoProviderType` / `BlueprintRepoOwnerType`. Official names: **Blueprint** (root) and **Blueprint module** (component). Avoid a boolean `isRoot` / `isComponent` (collides with instantiation `isRoot` and with “component” in the manifest spec types).
- **Blueprint type on every create, including hidden CRUD**: Required. No default-from-path. Hidden CRUD create and register share the same invariant.
- **Blueprint type mutability**: Public paths freeze blueprintType after create. Update-documentation-fields does not expose blueprintType and must not change it. Hidden CRUD overwrite **is** allowed to change blueprintType (the escape hatch for operators / existing rows). The PUT body is validated against the **incoming** blueprintType: switching to Blueprint requires a non-blank `descriptorTemplatePath`; switching to Blueprint module requires a blank/absent path. Omit blueprintType on PUT keeps the stored value. There is no extra lock after first publish or first composition — hidden CRUD may still change blueprintType then; subsequent parent publish/instantiate/update use the new blueprintType.
- **Relationship to `descriptorTemplatePath`**: Keep the field. **Blueprint → path required. Blueprint module → path must not be set.** Blueprint type decides allowed uses; path is only the descriptor location for a Blueprint.
- **Composition and instantiate gates**: (1) Parent of instantiate / update-data-product must be blueprintType **Blueprint** — refuse a module with a hint that it can only be used when composed by a Blueprint. (2) Children in `composition[]` must be blueprintType **Blueprint module** — refuse composing a Blueprint even if its path is blank. Keep a secondary check that a module must not declare a descriptor path.
- **Module publish topology**: When the version being published belongs to blueprintType **Blueprint module**, validate that version’s content is monorepo with empty composition **before** create. Parent publish still also checks each child is a module and 1→1 (defense in depth). Blueprint (root) publish keeps today’s parent composition rules and does not require the parent itself to be 1→1 empty.
- **Existing rows**: Add blueprintType with default **Blueprint**. Do **not** classify from `descriptorTemplatePath`. Do **not** backfill blank paths. Existing blank-path Blueprints violate the new “path required” write rule until operators set a path; they are not converted to modules.
- **Search/list**: Expose blueprintType on `BlueprintRes` and add an optional search filter by blueprintType so later UI (deferred) and other API clients can list roots vs modules. Server-side filter is in scope; UI is not.
- **Naming vs `ManifestComponentBase`**: Manifest “component” is OpenAPI-style extensibility. Catalog blueprint type is **Blueprint module**. Implementation must not reuse that type family.

### Alternatives Considered

- **Keep inferring from `descriptorTemplatePath`**: Rejected — this is the problem statement. Path is now a constrained pointer, not a type.
- **Two separate aggregates / APIs**: Rejected — versions, Git repo config, unique names, composition lookup, register, and publish are shared.
- **Blueprint type on `BlueprintRepo`**: Rejected — blueprintType is not a file pointer or Git provider attribute.
- **Derive blueprintType from manifest shape**: Rejected — blueprintType is a catalog fact at registration, before any version exists; a module’s 1→1 empty composition is also a valid shape for a simple Blueprint.
- **Boolean `isModule` / `isRoot`**: Rejected — collides with instantiation `isRoot` and composition “module” alias.
- **Backfill blank-path rows as modules**: Rejected — existing rows are Blueprints; do not infer module from a blank path.
- **Allow blueprintType change on public update-documentation-fields**: Rejected — public APIs must not reclassify a catalog entry. Hidden CRUD overwrite is the only allowed change path.
- **Forbid blueprintType change on hidden CRUD as well**: Rejected — operators need a way to correct blueprintType (including existing rows defaulted to Blueprint) without a public product flow.

## Risk & Gap Analysis

### Requirement Ambiguities

- **API enum token spelling**: Official display names are **Blueprint** and **Blueprint module**. Exact serialized tokens (e.g. `BLUEPRINT` / `MODULE`) are an implementation choice for REASONS Canvas; they must match those two blueprint types and not reuse instantiation `root`/`module` in a confusing way.
- **Hidden CRUD overwrite when payload omits blueprintType vs sends a different blueprintType**: Change is allowed only on this path. Natural reading: omit → keep stored; different value → persist the new blueprintType. If the new blueprintType does not match the path rules, reject the whole overwrite (no partial persist).
- **Instantiate / update-data-product of an existing Blueprint whose `descriptorTemplatePath` is still blank**: New writes require the path; existing rows were not backfilled. Whether those historical rows fail at instantiate until the path is set is not spelled out; the consistent reading is that a Blueprint without a path is invalid for descriptor-owning use, so instantiate should fail rather than skip render as today.
- **Update-documentation-fields replacing repo without touching blueprintType**: Stored blueprintType stays; path rules still apply to the new repo payload according to stored blueprintType (Blueprint still requires path; module still forbids it).
- **No formal numbered ACs** were in the original ticket; coverage below is inferred from the stated goal plus the decisions above.
- **UI** is explicitly out of scope (deferred).

### Edge Cases

- **Existing blank-path rows**: Remain blueprintType **Blueprint**. They do not become modules. They fail the new “Blueprint must have `descriptorTemplatePath`” rule on the next validating write. Instantiate of those rows should not silently skip descriptor render the way today’s blank-path parents do.
- **Existing non-blank-path rows that were used as composition children**: Today that is already rejected. After the change they stay blueprintType **Blueprint**, so composing them remains illegal (now by blueprintType, not by path).
- **Instantiate or update-data-product targeting a Blueprint module**: Fail before Git/registry work; the module is only pulled in as a child of a Blueprint parent.
- **Publish of a Blueprint module whose manifest is not 1→1 empty composition**: Fail at **that** publish, even if no parent has referenced it yet.
- **Publish of a Blueprint (root) that is itself 1→1 empty**: Still allowed; 1→1 empty is required for modules, not forbidden for Blueprints.
- **Client sends blueprintType on public update-documentation-fields**: Blueprint type is not part of that command; stored blueprintType stays. Path rules still follow stored blueprintType.
- **Hidden CRUD PUT changes blueprintType**: Allowed. New blueprintType and `descriptorTemplatePath` must be consistent in the same payload. All published versions of that name immediately follow the new blueprintType on the next parent publish/instantiate/update (no historical version keeps the old blueprintType).
- **Hidden CRUD PUT changes blueprintType after the name was composed**: Allowed. Parents that already composed this name@version succeed or fail on their **next** publish/instantiate/update according to the new blueprintType (e.g. Blueprint→module makes the child legal; module→Blueprint makes the child illegal).
- **Register / CRUD create of Blueprint without repo, or repo without path**: Invalid (Blueprint requires `descriptorTemplatePath`, which lives on the repo). A Blueprint create must include a repo with a non-blank path. A module create must include a repo whose path is blank/absent (other repo fields remain required as today).
- **Hidden CRUD PUT**: The only path that may persist blueprintType. Public update-documentation-fields must not diverge by offering a blueprintType field.
- **Init-repository content**: Server still receives a file bag from the client. BlueprintType-aware file templates are UI (deferred); the server does not infer blueprintType from init files.

### Technical Risks

- **Behavior change for composition**: Composing a catalog Blueprint (including blank-path) becomes always illegal. Previously a blank-path row could be used as a child. Desired, but a compatibility break.
- **Behavior change for instantiate / update-data-product**: Those use cases currently accept any published version as parent. Refusing Blueprint modules is required. Existing rows are all Blueprints, so they remain eligible as parents; modules only appear after new creates with blueprintType module.
- **Existing Blueprint rows without `descriptorTemplatePath`**: Schema add of blueprintType defaults them to Blueprint without filling the path. They are temporarily inconsistent with “path required” until updated. Writes and (if enforced) instantiate will 400 until a path is set. There is no automated backfill of the path (by decision).
- **Module publish check is new**: Today 1→1 empty composition is enforced only when a **parent** loads a child. Enforcing it on the module’s own publish can reject versions that previously published and were simply never composed.
- **Duplicated blueprintType gates**: Publish (parent children + module self-topology), instantiate, and update-data-product must all use blueprintType. Missing one gate leaves a path on the old heuristic.
- **Blueprint type flip via hidden CRUD with published / composed versions**: Overwrite can reclassify a name that parents already compose. There is no cascade rewrite of parent manifests; enforcement is on the next parent use-case. Operators can also use this path to fix existing rows defaulted to Blueprint (e.g. set blueprintType to module and clear the descriptor path in one PUT).
- **Public vs hidden update divergence**: Generic CRUD overwrite maps the full resource, including blueprintType — that is intended. Update-documentation-fields must not grow a blueprintType field, or the public freeze is lost.
- **Search filter without UI**: Server can expose blueprintType and filter now; Blindata UI remains deferred and will keep using today’s path heuristic until a later change.

### Acceptance Criteria Coverage

The requirement did not list numbered ACs. The following are the testable intents implied by the statement and the locked decisions.

| AC# | Description | Addressable? | Gaps/Notes |
|-----|-------------|--------------|------------|
| 1 | Catalog model distinguishes **Blueprint** (root) from **Blueprint module** (component) with an explicit required blueprintType, not by reading `descriptorTemplatePath` | Yes | Serialized enum tokens chosen in REASONS Canvas |
| 2 | Blueprint type is required on register **and** hidden CRUD create; omission is 400; no inference from path | Yes | — |
| 3 | Blueprint type cannot change on public APIs; hidden CRUD overwrite **may** change it (including after publish/composition) if the payload satisfies blueprintType↔path for the new blueprintType | Yes | Omit blueprintType on PUT keeps stored; public update-documentation-fields has no blueprintType field |
| 4 | A Blueprint must have non-blank `descriptorTemplatePath`; a Blueprint module must not | Yes | Existing blank-path Blueprint rows are not backfilled; they fail later validating writes |
| 5 | A catalog Blueprint must not be composed, including when it has no descriptor path | Yes | Replaces the path heuristic |
| 6 | Composition children must be Blueprint modules; parent instantiate/update-data-product must be Blueprint | Yes | — |
| 7 | A Blueprint module cannot be instantiated (or used as update-data-product parent) alone; only as a child of a Blueprint | Yes | Module register/publish stay allowed |
| 8 | At Blueprint module publish, the version content must be 1→1 with empty composition | Yes | New gate on the module’s own publish, not only when a parent uses it |
| 9 | Existing rows: blueprintType **Blueprint**, no backfill from path, no invented descriptor path | Yes | Temporary inconsistency for blank-path Blueprints until operators set a path |
| 10 | Public read exposes blueprintType; optional search filter by blueprintType | Yes | UI consumption deferred |
| 11 | UI changes | Out of scope | Explicitly deferred |
