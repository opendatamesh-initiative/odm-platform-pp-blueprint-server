# SPDD Analysis: Protected-resources integrity across multi-repository layouts

Companion to `BDMD-5124-202608201150-[Analysis]-protected-resources-integrity-policy-adapter.md` (Policy adapter + monorepo-only integrity) and to the BDMD-4820 multi-repository work (`BDMD-4820-202608251014` schema alignment, `BDMD-4820-202608251703` instantiate, `BDMD-4820-202608271040` update, `BDMD-4820-202609031130` tag all data-product repositories). Service guides: `docs/service/repositories-and-composition.md`, `docs/service/protected-resources.md`.

This analysis is scoped to **`odm-platform-pp-blueprint-server`**. Registry already stores additional keyed remotes (`DataProductAdditionalRepo` / `additionalDataProductRepos` on the product, including on the nested product of a version GET). Registry also applies the **same publication tag name** to the root remote and every additional remote (`POST .../repository/tags` / `tagAllDataProductRepositories`). Notification and Policy Service stay consumed contracts. UI can be updated in this ticket if the protected-resource destination key needs a form/SDK change; it is not on the evaluation path.

**Locator gap (closed):** Registry commit `115170f9` landed additional remotes keyed by `manifestKey` with the same Git clone metadata as the root pointer. `old/v1` already GETs the product when nesting clone metadata. Once reconstruction **keeps** `additionalDataProductRepos` on that nested product, integrity can see every remote without a new Registry API. Instantiate still does not write those rows — clients must persist them — but the fetch path exists.

**Clone-ref gap (closed):** Registry analysis `BDMD-4820-202609031130` and UI commit `d73bc5a98d435c4059020733ca1883e37e719b2d` make the publication tag **name** identical on root and additional remotes. Integrity clones each locator at the version resource’s single `tag`. No per-remote tag field and no checkpoint-tag hybrid are required.

---

## Original Business Requirement

The Blueprint Server already evaluates **protected resources** at data-product version publication: clone the published product at its tag, **locally re-instantiate** the recorded blueprint (same parameters, no Git push), hash listed paths, and fail if anything is missing or differs. That slice was deliberately limited to **monorepo, no composition**. Other layouts pass as not applicable so they are not blocked by a check the service cannot run.

Multi-repository support is now in the service. A data product may be one Git remote or several; a parent blueprint may compose published modules. Topology is derived (one vs many logical repository keys × composition present or not):

| | No composition | With composition |
|---|---|---|
| **1 key** | 1→1 monorepo | N→1 monorepo + modules |
| **≥2 keys** | 1→N polyrepo | N→N polyrepo + modules |

Protected-resource integrity must follow that model. The check remains **rebuild what instantiate would have written, then compare**. Paths stay **post-instantiation** (relative to a destination repository root after routes, module sidecar relocate, and parent lineage on the designated root).

**Parent-only (architectural decision):** The **parent** manifest owns the protected list; module manifests are not inherited or rewritten when that blueprint is used as a composition child. This is the chosen design because it is simpler and matches “parent owns the product.” It is **flagged** so a later change (for example inheritance of a module’s list through `composition[].targets`) can be considered without treating today’s parent-only rule as an accident. The service guide `docs/service/protected-resources.md` must document this as a product feature, together with the rest of the multi-repository protected-resources contract.

The manifest adds a **`repository` logical key** on each `protectedResources[]` item (same vocabulary as routes). When the key is **missing**, evaluation **falls back to current monorepo behaviour**: the path is on the designated root (`instantiation.root.repository`), which is the sole destination for 1→1 and N→1. When the key is **present**, it must be a declared `instantiation.repositories[].key`. Polyrepo authors who protect a non-root remote must set the key; omitting it still means the root. A breaking schema change is acceptable if it simplifies the contract — the service has not been released — but this fallback keeps existing 1→1 YAML valid.

**Clone coordinates:** Registry persists the root pointer (`dataProductRepo`, no manifest key — it *is* the designated root) plus `additionalDataProductRepos[]` (`manifestKey` = `instantiation.repositories[].key`, plus clone URL, provider, branch, owner). Reconstruction can build a **keyed locator map** from that nested product joined to the parent’s `instantiation.root.repository`. **Clone ref:** the version still has a **single** `tag`; publication applies that **same tag name** to every remote (root + additional). Integrity clones each locator at that tag.

Delivery stays phased so the local-instantiate rewrite is testable before multi-remote hashing:

1. **N→1 first** (composition, still one product Git clone). Forces the local-instantiate Git adapter onto `openSources` / `openTarget`, and hashing under composition destinations (and `.odm/<module>/` sidecars). 1→1 must keep working, including manifests that omit `repository` on protected resources. Reconstruction already starts passing through additional remotes on the nested product (even if this slice only clones the root). Docs cover destination paths, the `repository` key and monorepo fallback, and parent-only as an architectural decision.
2. **1→N / N→N next**: clone each locator at the **same publication tag**, compare per key. Locators and clone-ref are both closed; this slice is implementation of multi-clone hashing, not a further Registry product question.

The skip/fail matrix from the first protected-resources analysis must be updated for the new layouts and for missing additional remotes or tags.

---

## Domain Concept Identification

### Existing Concepts (from codebase)

- **Blueprint Manifest (`protectedResources`)**: Optional list of immutable paths/globs after instantiate. Each item today is only a `path` (plus optional unused `integrity` digest). Relationship: source of what to hash; currently interpreted as relative to **one** data-product repository root (the designated root). The new optional `repository` key scopes a path to a logical destination; omission preserves this one-repo meaning.
- **Logical repository key**: `instantiation.repositories[].key`, mapped at apply time by `targetRepositories[].targetId`. Relationship: already the vocabulary for routing; protected paths join this vocabulary via `protectedResources[].repository` so a path is not ambiguous across remotes. Additional Registry rows use the same key as `manifestKey`.
- **Designated root (`instantiation.root.repository`)**: The key that receives parent lineage, the data-product descriptor, and Registry’s `dataProductRepo`. Relationship: the root pointer has **no** `manifestKey`; integrity joins it to this declared key. Other keys live on `additionalDataProductRepos`. **Fallback** when a protected resource omits `repository`.
- **Route (`sourcePath` → `repository` + `path`)**: Flattened from parent `root.targets` and `composition[].targets`. Relationship: defines where files land; protected paths must be expressed in **destination** coordinates, not source-blueprint coordinates.
- **Instantiation scenario**: Derived (not stored) as 1→1 / N→1 / 1→N / N→N. Relationship: integrity today treats anything other than 1→1 as not applicable, using the **removed** `instantiation.strategy` field — it must switch to the same derivation instantiate/update already use.
- **Instantiate pipeline**: One use case for all four topologies: validate → flatten routes → `openSources` (parent + modules at release tags) → per key `openTarget` → orphan checkpoint → apply routes → module file relocate under `.odm/<alias>/` → parent lineage only on the root key → commit/tag/merge/push. Relationship: the **expected tree** for integrity must be this pipeline with pushes no-op’d and live product remotes not cloned.
- **Local instantiate Git adapter (integrity)**: Throwaway target, snapshot of the rendered tree, no push. Relationship: still written against the **pre-multi-repo** Git lifecycle (single source×target clone). Production Git port is already `openSources` + `openTarget`. This adapter is the merge/rewrite seam for N→1 (and the snapshot-per-key seam for 1→N).
- **Protected-resources integrity evaluation**: Combined file immutability + parameter sanity. Loads the stored blueprint version, reads the parent manifest, clones **one** published product tree at the publication tag, re-instantiates locally, compares digests per path. Input is tag + one product-repo locator + blueprint identity + lineage parameters — **not** a Policy DTO. Relationship: lasting core; must not call Registry. Slice 2 widens the locator to a keyed map consumed from the reconstructed object, cloning each remote at the **same** publication tag.
- **Policy V1 reconstruction (`old/v1`)**: Fetches Registry (search product → search version → GET version; GET product if nested clone metadata is incomplete), rebuilds a nested version resource, then maps that to the integrity command. Relationship: the only place Registry is called. Today it treats “has `dataProductRepo`” as complete and maps **only** that pointer. Registry product GET/version nested product now also carry `additionalDataProductRepos`. Reconstruction must **not drop** that list; the Policy mapper later turns root + extras into locators. Search DTOs in `old/v1` remain thin (uuid + fqn); the full product body comes from GET.
- **Working tree pair**: Published snapshot vs expected snapshot, hashed with the existing SHA-256 file/folder/glob rule. Relationship: N→1 still one pair; 1→N/N→N need **one pair per destination key** that has protected paths.
- **Module sidecar (`.odm/<alias>/`)**: Module README/manifest relocated on the target that received that module. Relationship: post-instantiation paths a parent may protect; never under `.odm/blueprint/` (parent lineage only, root key only).
- **Checkpoint tag (`blueprint-v{version}`)**: Pure render on **every** remote that received instantiate, same tag **name** per remote. Relationship: distinct from Registry’s data-product **publication tag**. Integrity clones the **publication** tag, not the checkpoint tag.
- **Publication tag (same name on every remote, landed)**: One `tag` on the version resource. Registry `POST /tags` (`tagAllDataProductRepositories`) creates or verifies that name on the root and on every additional remote (see `BDMD-4820-202609031130`; UI commit `d73bc5a`). Relationship: the clone ref for every locator in the keyed map. Missing tag on an additional remote already fails **publish**; if integrity still cannot clone that ref, fail closed.
- **Keyed additional data-product repos (Registry, landed)**: `DataProduct.additionalDataProductRepos` / `DataProductRes.additionalDataProductRepos` — `manifestKey` plus Git clone metadata (HTTP URL, provider, base URL, default branch, owner, external id). Root `dataProductRepo` unchanged (descriptor-bearing; no key field). Relationship: the store for non-root clone coordinates. Instantiate does **not** write these rows; clients (UI/orchestrator) persist them. Empty list is valid for 1→1 / N→1.

### New Concepts Required

- **Destination-scoped protected resource**: A parent-manifest entry with a **post-instantiation path** and an optional **logical repository key**. Relationship: replaces “path on the one product repo”; 1→1 / N→1 omit the key and keep today’s meaning (designated root). Polyrepo names a non-root key when the path is not on the root.
- **Parent-only protection policy (architectural decision)**: Only the parent’s `protectedResources` are evaluated. A module’s own list is ignored when that blueprint is used as a composition child (and is only meaningful if that module is instantiated standalone as 1→1). Relationship: avoids rewriting child paths through `composition[].targets`; matches “parent owns the product.” **Flagged** for a possible later alternative (inheritance). Must be documented in `docs/service/protected-resources.md`.
- **Expected trees keyed by destination**: Local instantiate produces a rendered tree **per logical key** that receives routes (one tree for N→1; several for 1→N/N→N). Relationship: hashing looks up the tree for the resource’s `repository` key (or the root key after fallback).
- **Keyed published locators**: Logical key → Git locator, built in `old/v1` / the Policy mapper from nested `dataProductRepo` (root key from the parent manifest) plus `additionalDataProductRepos[].manifestKey`. Relationship: integrity consumes the map and does not call Registry. Slice 1 may still clone only the root entry; slice 2 clones every key that has protected paths, each at the version `tag`. The map is no longer a missing Registry concept.

### Key Business Rules

- Combined A+B check unchanged: re-instantiate with the recorded parent version and parent lineage parameters, then compare hashes. Do not hash Velocity sources; do not persist digests into the source manifest.
- Protected paths are **post-instantiation**, relative to the **named destination’s repository root** (or the designated root when `repository` is omitted). Do not protect source `README.md` / `manifest.yaml` at blueprint-repo locations. To protect parent lineage, declare `.odm/blueprint/...` on the **root** key only. To protect a module’s relocated README/manifest, declare `.odm/<alias>/...` on the key that received that module.
- **Parent-only (architectural):** never merge or rewrite a child’s `protectedResources`. Authors list destination paths on the parent (including files that originated in a module). Document this; do not treat it as an implicit implementation detail.
- **`repository` on a protected resource:** optional. Omitted → **fallback to monorepo / designated root** (`instantiation.root.repository`), which is today’s one-repo behaviour. Present → must be a declared `instantiation.repositories[].key`. Unknown key → validation failure, not a silent skip. Evaluate applies this fallback (does not require the field on stored 1→1 YAML).
- Local expected trees must use the **same** instantiate pipeline as production (routes, module parameter mapping, module sidecar relocate, platform-owned descriptor on root, parent lineage on root). Git mutations against live product remotes remain forbidden.
- Integrity does **not** call Registry. Product clone coordinates come from the reconstructed (or V2) evaluation object: root pointer + additional keyed remotes. Blueprint clone coordinates stay in this service’s store.
- Join rule: `dataProductRepo` maps to `instantiation.root.repository`; each additional row maps to its `manifestKey`. Duplicate keys (root key also listed as additional, or two additional rows with the same key) → fail closed. Extra additional rows whose keys are not in the parent manifest are ignored for hashing (or fail closed — prefer **fail closed** so a stale Registry key cannot hide a layout mismatch).
- N→1 (and 1→1) remain one published clone + one expected tree. Composition adds **source** clones (modules), not extra product remotes. Additional remotes should be empty; if present, ignore them for cloning in slice 1 (do not fail 1→1/N→1 solely because extras exist).
- 1→N/N→N: file copy follows every route; lineage still only on `instantiation.root.repository`. A protected path on a secondary key is compared only to that remote’s trees — never against the root tree. Clone **every** required locator at the **same publication tag name**.
- Until slice 2 implements multi-remote hashing, 1→N/N→N stay **not applicable** with an explicit message (do not fail closed merely for being polyrepo). Once slice 2 evaluates them, missing locator for a key that has protected paths → **fail closed**. Empty `additionalDataProductRepos` on a polyrepo product is the same miss (clients did not persist extras). Missing publication tag on a remote → **fail closed**.
- Validator off → no registration (unchanged). No lineage / empty parent `protectedResources` → pass not applicable (unchanged).
- Applicable check, clone/auth/timeout/render/reconstruction error → fail closed (unchanged).
- Git credentials remain service configuration, not event payload.
- Service unreleased: adding `repository` with a monorepo fallback is allowed; existing 1→1 manifests without the field remain valid.

---

## Strategic Approach

### Solution Direction

Keep the lasting integrity use case and the removable `old/v1` Policy adapter. Extend **what** is compared (destination-scoped parent list) and **how the expected tree is built** (current instantiate pipeline). Use Registry additional remotes as the locator source and the version `tag` as the clone ref on every remote; do not add a second Registry client in core.

Two slices in one analysis:

**Slice 1 — Manifest + N→1 (and keep 1→1); reconstruction already carries extras**

- Extend the parent manifest so each protected resource **may** name a logical `repository` key as well as a path. Omitted key → designated root (monorepo fallback).
- Validate a present key at publish (and on the integrity path when reading the stored parent). Do not reject 1→1/N→1 manifests that omit the key.
- Stop treating “not 1→1” as a blanket not-applicable. Evaluate **1→1 and N→1**. Leave **1→N and N→N** as not applicable with a message that names polyrepo hashing not yet applied — not “composition unsupported”, not “Registry cannot see extra remotes”, and not “clone ref unknown.”
- Re-ground scenario detection on repository-key cardinality × composition (same derivation instantiate already uses).
- Rewrite the local-instantiate Git adapter to the current workspace lifecycle: open all required **sources** at release tags; for each destination key, open a **throwaway** target (do not clone the live product integration branch); snapshot the rendered tree after routes / module relocate / root lineage; no-op push. For N→1 that is one throwaway target and several sources. Snapshot **per key** so slice 2 does not rewrite this adapter again.
- Integrity still clones the **one** published **root** product at the Registry version tag and compares each parent protected path against the matching expected tree (the sole key, after fallback).
- Reconstruction: treat nested product as complete only when root `dataProductRepo` is present **and** `additionalDataProductRepos` is present as an array (empty array counts). If version GET omitted extras, GET product and nest it (same fallback as missing root repo today). Do not map extras into the integrity command yet; pass them through on the reconstructed object so slice 2 is a mapper/use-case change, not another fetch design.
- Update docs (`docs/service/protected-resources.md`, manifest README examples 2.1–2.2 at least, and related index/process links) so authors list destination paths (`data-plane/storage/**`, `.odm/storage/...`) with the optional destination key, the monorepo fallback, **and parent-only as a documented architectural decision** (module lists are ignored when composed; inheritance is a possible future change, not current behaviour).

**Slice 2 — 1→N / N→N (multi-clone; locators and clone-ref closed)**

- Same hashing and parent-only list. Local instantiate already snapshots **one expected tree per key** once slice 1’s adapter exists; polyrepo is “compare each protected path on its key’s published clone vs that key’s expected tree.”
- Policy mapper builds the keyed locator map from the reconstructed nested product + parent root key. Integrity clones each required locator **at the version `tag`** (same name on every remote). Integrity still does not call Registry.
- Missing additional row for a protected non-root key → fail closed (clients must persist extras; instantiate will not do it).
- Missing tag on a remote → fail closed (publication is already supposed to have applied or verified that name everywhere).

High-level flow after both slices: Policy evaluate → (`old/v1` reconstruct if V1, nested product including additional remotes) → integrity loads parent manifest → skip if not applicable → join locators (root key + extras) → clone published remotes at the publication tag → local instantiate expected trees per key → compare destination-scoped paths → Policy result.

### Key Design Decisions

- **Parent-only vs inherit module `protectedResources` (architectural decision):** Inheritance would rewrite child paths through composition routes and `.odm/<alias>/`, and would break when the same module is placed at different destinations. → **Parent-only.** Authors declare destination paths on the parent. Child lists are ignored when composed. **Flag:** this is a deliberate product rule, not an implementation shortcut. A later story may introduce inheritance; until then, docs must state parent-only explicitly (`docs/service/protected-resources.md`). Breaking vs a future “inherit” feature is acceptable.
- **Optional destination key with monorepo fallback vs hard-require:** Hard-require matches routes and avoids inference on polyrepo, but breaks current 1→1 YAML and forces autofill. Defaulting to the designated root keeps today’s authoring and evaluate path for monorepo. → **Add `repository`; when missing, fall back to `instantiation.root.repository` (current monorepo implementation).** Evaluate **does** apply this fallback. A present key must be a declared key. Polyrepo authors protecting a non-root remote must set the key.
- **One generalized expected-tree pipeline vs a composition-specific hasher**: A second render path would drift from instantiate (false rejects), the same reason integrity already reuses instantiate. → **Reuse `InstantiateBlueprintVersion`** with a local Git adapter aligned to `openSources` / `openTarget`. Scenario enum is taxonomy, not four hash scripts.
- **N→1 now vs wait for polyrepo hashing**: Locators and the shared publication tag are available, but N→1 still does not need multi-clone. It does need the local Git rewrite and destination path semantics. Waiting would leave composition publications unchecked. → **Slice 1 evaluates N→1; polyrepo stays not applicable until slice 2 (multi-clone).** Reconstruction in slice 1 still starts carrying extras.
- **How to join root vs additional remotes**: Root pointer has no key. → **Parent `instantiation.root.repository` names the root locator; `manifestKey` names each extra.** Reconstruction does not guess a reserved key such as `"main"`.
- **Integrity still must not call Registry**: Leaking keyed-repo fetch into the lasting use case would make `old/v1` deletion a domain rewrite. → Reconstruction fetches and nests the product (including extras); the Policy mapper / integrity command consume locators from that object. Policy V2 can forward the same nested product and delete `old/v1`.
- **Throwaway targets vs cloning live product remotes for expected trees**: Production `openTarget` clones the integration branch. Integrity must not do that (mutations, extra auth, polluted trees). → Local adapter keeps throwaway empty Git repos per key, snapshots after render, no-op push — same intent as today, new lifecycle.
- **What to do with child `protectedResources` at parent publish**: Failing parent publish if a module declares a list would surprise module authors who also use that blueprint standalone. → **Ignore.** Documented under parent-only. Optional later: inheritance.
- **Polyrepo skip vs fail until multi-clone exists**: Failing all polyrepo publications would block layouts instantiate already supports. Locators and clone-ref are closed; hashing more than one remote is still slice 2 work. → **Not applicable with an explicit message** until slice 2. After slice 2, missing locators or missing publication tag for a key that has protected paths → **fail closed**.
- **Clone ref for every remote**: Checkpoint tag would check the last instantiate, not the commit being published. A hybrid (publication on root, checkpoint on extras) would be asymmetric. A per-remote Registry tag field is unnecessary now that publication applies one name everywhere. → **Same publication tag name on every locator** (`BDMD-4820-202609031130`, UI `d73bc5a`).

### Alternatives Considered

- **Keep a single path with no destination key (infer from routes)**: Rejected as the only mechanism. Ambiguous when the same relative path exists on two remotes. The optional key plus root fallback is enough for monorepo and explicit for polyrepo.
- **Require `repository` on every protected resource (no evaluate fallback)**: Rejected. Breaks current monorepo YAML; publish autofill would still be inference, just earlier. Fallback to the designated root preserves today’s behaviour.
- **Inherit and rewrite module protected lists**: Rejected for this work (harder, destination-dependent). Parent-only is enough and is a clean break. **Not discarded forever** — flagged as a possible later architectural change.
- **Fail unsupported topologies (including N→1) until polyrepo is done**: Rejected. N→1 is evaluable with one product clone; failing it would block valid composed monorepos.
- **Evaluate 1→N by cloning only the root remote**: Rejected. Silent partial check would pass publications whose secondary remotes were tampered with.
- **Hash inside `old/v1`**: Rejected (same as the first analysis). Deletion of the adapter must not remove the check.
- **Call instantiate’s use case from update or share a validator class**: Out of scope / already rejected by BDMD-4820. Integrity only *invokes* instantiate as the expected-tree engine, as today.
- **Wait for Policy V2 / the publication event to carry keyed remotes**: Rejected as a **locator** strategy. The nested product already can carry `additionalDataProductRepos`.
- **Change Policy or Notification just to attach remotes**: Rejected for locators. Registry product/version GET is enough.
- **Clone the instantiate checkpoint tag (`blueprint-v{version}`) on each remote**: Rejected. Weaker than 1→1 (publication may be a later commit than the last checkpoint).
- **Publication tag on the root; checkpoint tag on additional remotes**: Rejected. Asymmetric; easy to false-pass secondaries.
- **Per-remote tag field on Registry**: Rejected. The same publication tag name is already applied to all remotes.

### Slice 2 — clone-ref (decided)

Keyed remotes are available on the nested product. The publication tag **name** is the same on root and additional remotes.

**Chosen:** clone each locator at the version `tag`. Matches today’s 1→1 semantics (the commit being published). Registry create-tag fans out; selecting an existing tag verifies every additional remote already has that name. If a secondary remote still cannot be cloned at that ref, fail closed.

Rejected (kept for history): checkpoint tag on every remote; hybrid publication/checkpoint; new per-remote tag field on Registry.

Shared constraints unchanged: instantiate does not write additional remotes; extras may be empty until the client persists them; mixed Git hosts remain unsupported (same as instantiate); same physical URL mapped to two keys remains undefined; root pointer is never folded into the additional list.

---

## Risk & Gap Analysis

### Requirement Ambiguities

- **Slice 2 clone ref** — **Closed.** Same publication tag name on every remote (`BDMD-4820-202609031130`, UI `d73bc5a`). Slice 2 REASONS Canvas can proceed on that convention; it is still a **later** canvas than slice 1.
- **Must every declared key be in the locator map, or only keys that appear on `protectedResources`?** Complete map matches instantiate validation. Sparse map is enough if authors only protect root files. Recommendation for slice 2: require locators for every key that has at least one protected path (after fallback); missing locator for such a key → fail closed. Unused keys with no protected paths need not be cloned.
- **Stale extra remotes** whose `manifestKey` is not in the parent manifest: fail closed vs ignore. Recommendation: **fail closed** (layout and Registry disagree).
- **`repository` omitted** — **Closed.** Fallback to designated root / current monorepo behaviour at evaluate (and at publish validation: a missing key is valid). Incomplete stored versions without the field remain evaluable as 1→1/N→1.
- **Blindata UI Manifest SDK / protected-resource destination key**: UI is already being updated for multi-repo. Runtime evaluation does not depend on the UI. Adding optional `repository` on `protectedResources` **may** need a small UI/SDK follow-up in this ticket; it is no longer a hard out-of-scope wall.
- **Does version GET always nest `additionalDataProductRepos`?** Version maps the nested `DataProduct`. Reconstruction today skips GET product once `dataProductRepo` is present, which could omit extras if the association was not loaded. Mitigation (slice 1): treat extras as required on the nested product (empty array OK) and GET product when the field is missing.
- **Parent-only vs a later inheritance model** — **Closed for this work (architectural decision).** Parent-only. Flagged so a future story can introduce inheritance without rediscovering why it was deferred. Docs must state the current rule.

### Edge Cases

- **1→1 with omitted `repository`**: Fallback to `instantiation.root.repository` (the sole key). Same hashes as today.
- **1→1 with `repository` set**: Must match the sole/root key. Typo key → publish 400 / evaluate fail closed.
- **N→1 parent `path: ./` plus module subpaths**: Already 400 at publish/instantiate (nested destinations). Integrity never sees overlapping trees.
- **N→1 with leftover additional remotes in Registry**: Slice 1 clones only the root; extras unused. Do not fail the check solely for extras existing.
- **Protecting `.odm/blueprint/**` on a non-root key (polyrepo)**: Lineage is not written there; check would fail “not produced by the blueprint.” Slice 2 may optionally reject this at publish; slice 1 has only one key so it does not arise.
- **Protecting `.odm/<alias>/` on N→1**: Valid if that module was routed into the sole key; expected tree must include relocate. Parent lists the path; the module’s own `protectedResources` are ignored.
- **Protecting a path the routes never emit**: Fail with “not produced by the blueprint” / missing on published — same as today for a bad glob.
- **Empty `protectedResources` on a composed parent**: Not applicable (unchanged).
- **Module declares `protectedResources`, parent does not**: Not applicable for the product (parent-only). Standalone instantiate of that module as 1→1 still uses its own list.
- **Local instantiate validation requires a complete `targetId` map**: For N→1, integrity must pass the sole key as `targetId` (the removed request `type: root` must not return). Wrong key → instantiate 400 surfaced as infrastructure/fail closed. For slice 2, local instantiate needs a throwaway target per key (dummy Git metadata is enough; locators are for the **published** clones, not for expected trees).
- **Timeout**: N→1 clones parent + modules + product. Polyrepo adds one published clone per key. Existing evaluation timeout may be tight; fail closed on timeout remains correct.
- **Polyrepo product, extras never persisted**: Slice 2 fail closed for protected non-root keys. Slice 1 does not evaluate polyrepo.
- **Partial polyrepo Git failure (slice 2)**: Same as instantiate — not transactional. Integrity should not push; clone/hash can still fail on the first remote. Prefer collecting mismatch/clone errors per key when cheap; fail closed overall if any key cannot be checked.

### Technical Risks

- **Local Git adapter does not implement the current instantiate Git port** — Production uses `openSources` / `openTarget`; the integrity adapter still speaks the old single-clone API and still snapshots one tree. Until rewritten, local re-instantiate cannot compile/run against current instantiate. Mitigation: slice 1’s first technical obligation is that adapter + a snapshot **per key** (N→1 uses one entry).
- **Integrity still reads `instantiation.strategy`** — Field removed. Mitigation: derive scenario the same way instantiate does; do not keep a parallel strategy enum.
- **Reconstruction dropping extras** — If version GET nested product has root repo only, today’s `hasNestedRepo` short-circuit never GET-products. Mitigation: slice 1 completeness check includes `additionalDataProductRepos` (array present, possibly empty).
- **False rejects from render drift** — Mitigated by reusing instantiate; the adapter must not skip module relocate, descriptor-on-root, or lineage. Tests should assert N→1 expected trees contain composition destinations and `.odm/<alias>/`, not only parent `core/`.
- **Working-copy leaks** — More temp dirs (several sources + throwaway target + published clone(s)). Mitigation: existing close/cleanup on all paths; snapshot must outlive git-utils deleting clones (already the reason for `RenderedTreeSnapshot`).
- **`old/v1` accidentally growing hash logic** — Mitigation: reconstruction remains fetch+nest; destination keys and hashing stay in integrity after the parent manifest is loaded from this service. Mapper only builds locators.
- **Docs merge conflicts** (`README.md`, `docs/README.md`, `blueprint-process.md`, manifest README `protectedResources` wording) — Two stories collided in docs. Mitigation: this work owns protected-resources path+destination wording and **parent-only**; process docs keep multi-repo instantiate/update as source of truth; indexes keep **both** guides. Conflicts are resolved as part of this analysis follow-up, before slice 1 code.
- **Duration vs Policy HTTP timeout** — Extra module clones on N→1; extra product clones on polyrepo. Mitigation: keep one evaluation timeout; fail closed; do not add a second timeout policy in this ticket.

### Acceptance Criteria Coverage

No numbered ACs were supplied. Implied criteria from the discussion:

| AC# | Description | Addressable? | Gaps/Notes |
|-----|-------------|--------------|------------|
| 1 | Parent `protectedResources` may name a logical destination key plus a post-instantiation path; omitted key falls back to designated root (monorepo) | Yes | Slice 1; existing 1→1 YAML without `repository` stays valid |
| 2 | Parent-only: module `protectedResources` are not inherited or rewritten | Yes | **Architectural decision** (flagged for possible later inheritance). Must be documented in `docs/service/protected-resources.md` |
| 3 | 1→1 integrity still passes/fails as today (omitted or root destination key) | Yes | Merge fix: scenario derivation + `targetId` on local instantiate |
| 4 | N→1 integrity evaluates (not not-applicable); expected tree includes parent routes, module destinations, `.odm/<alias>/`, parent lineage on the sole key | Yes | Slice 1; still one published **root** clone |
| 5 | Expected trees come from the same instantiate pipeline (local Git, no push, no live product clone) | Yes | Forces `openSources` / `openTarget` on the local adapter; snapshot per key |
| 6 | Unknown protected `repository` key fails validation (publish / evaluate), not a silent skip; omitted key is not unknown | Yes | Same declared-key rule as routes when the field is present |
| 7 | Skip/fail matrix updated: empty list / no lineage still not applicable; N→1 applicable; 1→N/N→N not applicable until slice 2; missing locator/tag on an applicable check fail closed | Yes | Slice 1 implements the N→1 row; polyrepo row stays skip until multi-clone |
| 8 | Integrity does not call Registry; reconstruction nests full product including `additionalDataProductRepos` | Yes | Slice 1: pass-through extras (empty array OK). Slice 2: mapper consumes them. GET product if version omitted extras |
| 9 | 1→N/N→N comparison model documented (per-key published vs expected); locators from Registry extras + root pointer; clone ref = version `tag` on every remote | Yes | Implementation is slice 2; **not** gated on a missing Registry model or tag convention |
| 10 | Manifest README + protected-resources docs describe destination-scoped paths, optional `repository` + monorepo fallback, composition destinations, and parent-only | Yes | Resolve existing doc merge conflicts; slice 1 owns the protected-resources guide rewrite |
| 11 | Policy/Notification unchanged; UI may follow the destination-key schema if needed | Yes | Locators and tags do not require a Policy change |
| 12 | Slice 2 clone-**ref** is the same publication tag name on all remotes | Yes | **Decided** (`BDMD-4820-202609031130`, UI `d73bc5a`). Checkpoint / hybrid / per-remote field rejected |

---

## Delivery boundary (for later REASONS Canvas)

**In a first REASONS Canvas / implementation (slice 1):** optional manifest destination key with monorepo/root fallback; publish validation of a present key; integrity scenario + N→1 evaluate; local instantiate Git adapter on the current workspace lifecycle with per-key snapshot; 1→1 regression (including omitted `repository`); reconstruction completeness so nested product includes `additionalDataProductRepos` (GET product if missing); polyrepo still explicit not-applicable; docs including **parent-only as an architectural decision**.

**Explicitly not in that first canvas:** mapping extras into the integrity command; hashing more than one published remote.

**Next canvas (slice 2):** map root + `additionalDataProductRepos` into keyed locators, clone each protected key at the version `tag`, compare per destination. No new Registry **store** and no new tag convention.
