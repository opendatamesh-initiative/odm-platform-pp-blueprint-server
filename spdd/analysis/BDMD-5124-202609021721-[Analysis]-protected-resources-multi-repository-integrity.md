# SPDD Analysis: Protected-resources integrity across repository layouts

This analysis extends `BDMD-5124-202608201150-[Analysis]-protected-resources-integrity-policy-adapter.md` across composition and multiple destination repositories. It uses the current Blueprint and Registry contracts as the only authoritative baseline.

## Authoritative baseline

The Blueprint and Registry multi-repository contracts are treated as the final contract for this analysis:

- A parent manifest declares destination keys in top-level `targetRepositories[]`; exactly one entry has `isRoot: true`.
- A parent manifest routes sources through typed `instantiation[]` entries:
  - `type: root` routes the parent Blueprint;
  - `type: module` plus `moduleName` routes a catalog Module;
  - each target uses `sourcePath`, `repo`, and `destinationPath`.
- `composition[]` contains Module identity and parameter mapping only.
- Catalog type is explicit. Only `BLUEPRINT` can own and instantiate/update a data product. Only `MODULE` can be composed.
- Registry stores the root locator in `dataProductRepo` and non-root locators in `additionalDataProductRepos[]`, keyed by `repositoryKey`.
- Registry stores the root version ref in `tag` and non-root refs in `additionalTags[]`, keyed by `repositoryKey`.
- Repository refs are independent values. Registry no longer performs automatic all-repository same-tag fan-out.
- Registry publication validates additional-ref metadata against current additional repository keys, but it does not verify Git refs or require the root tag.

This is a hard cut. The removed object-shaped instantiation model, routes under composition, `manifestKey`, and shared-publication-tag assumptions are not compatibility requirements and do not appear in the target design.

## Original Business Requirement

Protected-resource integrity must work for every Blueprint layout:

- one source Blueprint into one destination repository;
- a parent Blueprint plus composed Modules into one destination repository;
- one source Blueprint split across multiple destination repositories;
- a parent Blueprint plus composed Modules routed across multiple destination repositories.

The business check remains the same in every layout: reconstruct what the recorded parent Blueprint version would have generated with the recorded parameters, then compare each protected destination path with the repository snapshot being published.

The parent Blueprint owns the protected-resource policy. Module policies are not inherited or rewritten. Protected paths refer to final destination coordinates, not source-template coordinates. Evaluation must be complete: it must never approve a polyrepo publication after checking only the root repository.

The final manifest and Registry designs are authoritative. No backward compatibility is required for earlier WIP contracts.

## Domain Concept Identification

### Existing Concepts (from codebase)

- **Catalog Blueprint (`BLUEPRINT`)**: Parent template that owns the data-product descriptor, lineage, composition, target layout, and protected-resource policy.
- **Catalog Module (`MODULE`)**: Published reusable child that may be selected by a parent composition. It contributes source files but does not own the data product, descriptor, or destination layout.
- **Target repository declaration**: A logical destination in `targetRepositories[]`. Its `key` is the stable join identity across routes, runtime target mappings, Registry additional repositories, and integrity.
- **Root target**: The single declared destination with `isRoot: true`. It receives the parent descriptor and lineage and corresponds to Registry `dataProductRepo`.
- **Composition identity**: A parent-owned alias that selects a published Module version and maps parent values or literals to Module parameters.
- **Typed instantiation entry**: A routing group selecting either the parent source or one composed Module source. Its targets place source paths into logical destination keys and destination paths.
- **Instantiation scenario**: Runtime classification from two independent dimensions: destination-key cardinality and composition presence.
- **Production target mapping**: The instantiate/update request maps each manifest target key to a physical Git repository through `targetId`.
- **Rendered target tree**: Final output for one logical destination after routing, parameter rendering, sidecar relocation, and any root-only descriptor/lineage work.
- **Protected resource**: A parent-owned declaration of an immutable post-instantiation path. Multi-destination evaluation requires the declaration to resolve to one target key.
- **Registry root locator**: `dataProductRepo`; it is intentionally not labeled with the manifest root key.
- **Registry additional locator**: An `additionalDataProductRepos[]` entry labeled with `repositoryKey`.
- **Registry root snapshot ref**: The data-product version’s top-level `tag`.
- **Registry additional snapshot ref**: An `additionalTags[]` entry containing `repositoryKey` and its repository-specific tag.
- **Blueprint checkpoint**: A pure render tag used as instantiate/update merge baseline. It is not a data-product publication ref and does not establish publication integrity.
- **Unchanged update result**: A next Blueprint checkpoint may reuse an existing pure-render commit when output is unchanged. This says nothing about the published integration branch.

### New Concepts Required

- **Protected target key**: The logical `targetRepositories[].key` selected by one protected-resource declaration. This is destination identity, never parent/Module source identity.
- **Published locator map**: Target key → physical Git locator. The root entry is created by joining the manifest’s explicit root key to Registry `dataProductRepo`; non-root entries come from `additionalDataProductRepos[].repositoryKey`.
- **Published ref map**: Target key → version ref. The root entry uses Registry version `tag`; non-root entries come from `additionalTags[].repositoryKey`.
- **Published snapshot map**: Target key → cloned published tree, produced by joining the locator and ref maps.
- **Expected snapshot map**: Target key → locally rendered tree, produced by applying current instantiate semantics to disposable targets.
- **Protection coverage set**: The target keys referenced by the parent protected-resource policy after omitted/blank `repository` values resolve to the explicit root key. It is the only target-key set for which Registry mappings and published clones are required.

### Conceptual Relationships

- Composition determines **which source components participate**; `targetRepositories[]` determines **how many destination repositories exist**. These counts are independent.
- A route joins one source component to one target key and destination path.
- A protected-resource declaration joins a final path to one target key.
- The expected and published trees are paired only by the same target key.
- Registry product data supplies current repository locations; Registry version data supplies the refs selected for that version.
- The root mapping is asymmetric by design: the manifest labels the root key, while Registry stores the root locator/ref in dedicated unkeyed fields.
- Module files become parent-owned product output after routing. Under the parent-only policy, the parent protects those files by naming their final path and target.

### Key Business Rules

- **Parent-only is an architectural rule**: only the parent `BLUEPRINT` list governs product publication. Module lists are never inherited or path-rewritten.
- **Module policy declarations are invalid**: publishing a catalog `MODULE` with a non-empty `protectedResources` list is rejected because the list could never govern a product publication.
- **Paths are destination-relative**: protected paths are evaluated after routing and rendering.
- **Every protected item resolves to exactly one target key**. A non-blank `repository` must match `targetRepositories[].key`; omission or blank resolves to the sole `isRoot: true` target. Root shorthand is intentional because it is expected to be the common case.
- **The root key is explicit**: use the sole `isRoot: true` declaration, never list position, route order, or a reserved name.
- **All layouts use the same evaluation model**:
  - one destination, no composition: one parent source and one tree pair;
  - one destination with composition: parent plus Module sources and one tree pair;
  - multiple destinations, no composition: one parent source and one tree pair per protected target;
  - multiple destinations with composition: parent plus Module sources and one tree pair per protected target.
- **Composition changes source cloning, not publication ref semantics**. Each used parent/Module source is read at its Blueprint release tag.
- **Polyrepo evaluation is complete or fails**. Checking only the root is never a successful integrity result when protected paths target additional repositories.
- **Completeness is protection-scoped**. No protected resources means not applicable. If protection references only a subset of manifest targets, only that subset requires Registry metadata, cloning, and comparison; unrelated targets and extra Registry entries are ignored.
- **Published target mapping**:
  - explicit root target key → `dataProductRepo` + version `tag`;
  - each non-root target key → same-key `additionalDataProductRepos[].repositoryKey` + same-key `additionalTags[].repositoryKey`.
- **Refs may differ across repositories**. The root tag must not be reused as a non-root fallback.
- **Registry publication is metadata validation, not Git verification**. Empty refs, unresolved locators, missing remote tags, and clone errors for a referenced target fail closed during applicable integrity evaluation.
- **Root-only generated artifacts stay root-only**: parent descriptor and `.odm/blueprint/` lineage can only be protected on the root target.
- **Module sidecars follow Module placement**: `.odm/<moduleAlias>/` is protected on the destination that receives that Module’s relevant output.
- **Expected output comes from current instantiate semantics**. Integrity does not maintain a parallel routing or rendering model.
- **Evaluation is read-only**. Expected targets are disposable; published targets are cloned snapshots; no Git writes occur.
- **No compatibility behavior** exists for old manifest paths, `manifestKey`, or shared tags.
- **Policy is version-local**. Each publication uses only its recorded parent Blueprint version’s protected list. Later versions may add, remove, or retarget entries without a cross-version check.
- **The guarantee is immediate**. Current product locators are used for publication-time evaluation; repeatable historical evaluation after locator changes is not guaranteed.

## Strategic Approach

### Solution Direction

Adopt a single target-keyed integrity model from manifest authoring through comparison:

1. The parent `BLUEPRINT` declares protected destination paths and their target scope.
2. Publication validation resolves omitted/blank `repository` to the explicit root and ensures each non-blank value is a declared `targetRepositories[].key`.
3. Policy V1 reconstruction obtains a full Registry version and product so locator and ref information are available.
4. Integrity loads the recorded parent manifest and derives the explicit root and layout.
5. Registry data is joined into separate locator and ref maps for the protection coverage set, keyed by manifest target identity.
6. Integrity validates that every referenced target has one unambiguous, non-blank locator/ref pair before cloning; unrelated keys are ignored.
7. Each published target required by the protection policy is cloned at its own recorded ref.
8. The current instantiate pipeline renders parent and Module sources into disposable targets, producing expected trees by key.
9. Each protected path is hashed only against the published and expected trees for its target key.
10. Any referenced mapping gap, clone failure, missing path, or content difference fails the overall policy.

The end state supports all four layouts. Work can be delivered in coherent phases, but all phases share the final keyed contracts:

- **Foundation**: implement optional `repository`, explicit root fallback, Module-list rejection, and protection-scoped Registry reconstruction.
- **One-destination verification**: validate 1→1 and composed N→1 using multiple source clones and one target pair.
- **Multi-destination verification**: validate 1→N and N→N using independent locator/ref/tree pairs.
- **Policy and update hardening**: preserve recorded-version policy and verify that checkpoint optimizations never bypass publication comparison.

### Locked Design Decisions

- **Hard cut to current Blueprint and Registry contracts**: no dual-read, migration, or fallback for superseded WIP designs.
- **Parent-owned policy**: Module policies are not inherited.
- **Module authoring rejection**: A catalog `MODULE` cannot publish a non-empty protected-resource list.
- **Post-instantiation coordinates**: protection identifies final destination paths.
- **Optional target scope**: `protectedResources[].repository` selects a target; omission or blank means the explicit root.
- **Explicit root semantics**: root comes from `targetRepositories[].isRoot`.
- **Target-keyed expected and published trees**: one comparison namespace per logical destination.
- **Separate locators and refs**: current Registry product rows locate repositories; version fields select repository-specific snapshots.
- **Independent non-root refs**: no same-tag assumption.
- **No partial polyrepo approval**: all protected destinations must be evaluated.
- **Instantiate semantic reuse**: no integrity-specific renderer.
- **Core/adapter boundary**: integrity core does not call Registry; the Policy V1 adapter reconstructs external context.
- **Read-only Git behavior**: no mutation during evaluation.
- **Fail-closed applicable evaluation**: incomplete metadata or infrastructure failure is a policy failure.
- **Protection-scoped coverage**: only referenced target keys need complete Registry mappings and published clones.
- **Registry-consistent key matching**: new integrity joins use Registry’s exact key semantics; this feature does not broaden into normalization changes elsewhere.
- **Version-local policy**: no comparison or monotonicity rule across Blueprint versions.
- **Immediate publication guarantee**: historical locator snapshots are out of scope.
- **No physical-alias validation**: logical targets are evaluated independently even when locators happen to identify the same remote.
- **Path mismatches are reported together; infrastructure errors fail immediately**: comparison collects every protected-path mismatch into one policy failure. Clone, auth, timeout, and render errors stop evaluation at once.

### Resolved Decisions for REASONS

1. **Final protected-target field**
   Keep `protectedResources[].repository` optional. A non-blank value must exactly match `targetRepositories[].key`; omission or blank means the sole target with `isRoot: true`. The shorthand is a deliberate convenience for the most common root-protection case.

2. **Module protected-resource authoring**
   Reject publication of a catalog `MODULE` whose manifest has a non-empty protected-resource list. This avoids preserving inert policy metadata and directs authors to declare final protected paths on the parent `BLUEPRINT`.

3. **Cross-service key-set rule**
   Require Registry locator/ref completeness only for the protection coverage set and clone only those targets. An empty list is not applicable; a subset protects only that subset. Missing or extra mappings for unreferenced manifest targets do not affect integrity.

4. **Key normalization**
   New integrity mapping code follows Registry’s exact-string key semantics and does not introduce trimming or case folding. Existing normalization behavior outside the files changed for this feature is left unchanged rather than expanding scope.

5. **Stale and duplicate Registry data**
   Fail closed before Git access when a referenced target has missing, blank, duplicate, or conflicting locator/ref data. Ignore unknown, stale, or incomplete entries for unreferenced targets. Root protection always uses dedicated `dataProductRepo` and `tag`; non-root protection requires a same-key additional locator and ref.

6. **Protection evolution on update**
   Each publication is governed only by its recorded parent Blueprint version. A next version may add, remove, or retarget protected resources without additional update checks.

7. **Historical locator drift**
   Guarantee immediate publication-time evaluation only. Current product locators are authoritative for that check; version-owned locator snapshots and repeatable later evaluation are deferred.

8. **Physical remote aliasing**
   Add no alias detection or special handling. Each protected logical target is cloned and digested independently using its own locator/ref mapping, even when two locators happen to resolve to the same physical remote.

9. **Failure reporting**
   Collect every protected-path mismatch and report them together so authors can fix them in one pass. Infrastructure errors (clone, auth, timeout, render, or incomplete locators for a referenced target) fail immediately: any one of those rejects the policy, and remaining remotes need not be cloned.

### Alternatives Considered

- **Keep protected paths unscoped and infer destination from routes**: Rejected. A path can be produced on several targets, by several routes, or outside routed files as a root-only artifact.
- **Use source/Module identity as protection scope**: Rejected. Protection applies to final product repositories, and one source can be split across several targets.
- **Require an explicit target on every item**: Rejected. Omission or blank intentionally means the explicit root, reducing noise for the common case without relying on target order or naming.
- **Use one shared publication tag for every locator**: Rejected. It contradicts the final Registry version model.
- **Use Blueprint checkpoint tags for non-root repositories**: Rejected. Checkpoints are generation baselines, not publication snapshots.
- **Validate only the root until polyrepo support is complete**: Rejected. Multi-target delivery is implemented; a polyrepo publication is never approved after a root-only partial check, and polyrepo is never reported as not-applicable solely because there are multiple destinations.
- **Clone or validate all declared published repositories unconditionally**: Rejected. Targets outside the protection coverage set do not affect this policy.
- **Read Module protected-resource lists and rewrite them through routes**: Rejected for this feature. It changes ownership and creates ambiguous results when a Module is routed more than once.
- **Store per-target hashes in the manifest**: Rejected. Re-instantiation remains the source of expected content.
- **Freeze or compare protection declarations across versions**: Rejected. The recorded parent Blueprint version is the complete policy authority for each publication.
- **Snapshot repository locators per version now**: Rejected. It is unnecessary for the immediate publication gate and can be introduced if historical evaluation becomes a requirement.
- **Continue cloning after an infrastructure error**: Rejected. One clone, auth, timeout, or render failure already rejects the policy; later remotes need not be cloned. Path mismatches are still collected and reported together.

## Risk & Gap Analysis

### Accepted Boundaries and Deferred Concerns

- **Root shorthand is intentional**: omitted/blank `repository` always means the explicit root and never a positional or reserved-name fallback.
- **Partial protection is valid**: targets without protected paths may have absent or stale Registry mappings without affecting this policy.
- **Key cleanup is not cross-cutting**: new joins are exact; normalization differences in existing Blueprint or Registry code remain outside this feature.
- **Protection can weaken or move**: no cross-version invariant is imposed.
- **Historical locator drift is accepted**: current locators may change after the immediate publication decision.
- **Physical aliases are opaque**: no attempt is made to prove whether logical locators identify the same remote.
- **Failure reporting**: path mismatches are all returned in one policy failure; evaluation stops only when infrastructure cannot continue.

### Edge Cases

- The root target is declared after one or more non-root targets.
- A one-destination manifest uses composition with several Module sources.
- One source is split across multiple target repositories.
- One Module alias routes different paths into multiple targets.
- The same protected relative path exists on root and a secondary target.
- A protected item names a declared but unused target; manifest validation should already reject unused target declarations.
- A protected root-only artifact is assigned to a non-root target.
- A Module sidecar is protected on a target that never receives that Module.
- Registry additional locators and additional refs contain different key sets, including gaps that do not concern protected targets.
- Registry contains an additional key equal to the manifest root key.
- The Registry root tag is blank; this fails integrity only when the root target is protected.
- Additional tag values differ from the root tag or are reused by another version; both are valid final Registry behavior.
- Registry publication succeeds but a recorded remote tag does not exist.
- Product repository coordinates change between publication and later evaluation.
- A protected glob matches no files on both trees.
- A later Blueprint version removes a previously protected declaration; this is valid and that version’s publication uses the new list.
- A no-op Blueprint update reuses a checkpoint commit while the live product branch contains tampering.

### Technical Risks

- **Wrong-tree comparison**: Resolving a target key without using it for both published and expected lookup can silently compare against root. Keep keyed trees through the complete call chain.
- **Source/destination cardinality confusion**: N→1 means several source components and one destination, not several product repositories. Test dimensions independently.
- **Metadata/remote divergence**: Registry validates fields, not Git state. Cloneability remains an integrity responsibility.
- **Historical locator drift**: Current locators combined with old refs may target the wrong physical repository during later re-evaluation. That use case is outside the immediate-publication guarantee.
- **Temporary resource scale**: N→N requires multiple source and published clones plus disposable expected targets. Bound evaluation and clean every workspace deterministically.
- **Mixed Git providers**: Composition currently restricts parent/Modules to compatible providers, while product targets may still require provider-specific credentials.
- **Raw manifest lineage copy**: The root sidecar contains stored manifest content, not injected digests. Expected-tree comparison must mirror actual instantiate behavior.
- **Update semantics**: `contentUnchanged` compares pure Blueprint checkpoints and must never short-circuit publication integrity.
- **Policy transport completeness**: The current V1 adapter enriches from Registry. If a future direct V2 event omits data required by a protected target, extend the Registry publication event rather than silently accepting incomplete context.

### Acceptance Criteria Coverage

| AC# | Description | Addressable? | Gaps/Notes |
|-----|-------------|--------------|------------|
| 1 | Protected resources are scoped to final logical destination repositories | Yes | Optional `repository`; omission or blank means explicit root |
| 2 | Root scope uses the explicit `isRoot: true` destination | Yes | No positional or reserved-name inference |
| 3 | Parent-only policy applies to composed products | Yes | Non-empty Module lists are rejected |
| 4 | 1→1 publication integrity remains supported on the final manifest model | Yes | Uses one source and one target pair |
| 5 | N→1 publication integrity supports parent plus Module sources | Yes | Uses one target pair and current composition semantics |
| 6 | 1→N publication integrity compares every protected destination independently | Yes | Requires per-key Registry joins |
| 7 | N→N publication integrity supports composition and independent targets | Yes | Highest clone/render cardinality |
| 8 | Registry root and additional locators join to manifest target keys | Yes | Exact matching; completeness required only for protected targets |
| 9 | Each repository is cloned at its own version ref | Yes | Root `tag`; non-root `additionalTags[]` |
| 10 | Registry publication success is not treated as proof of Git ref existence | Yes | Clone failures fail closed |
| 11 | Expected trees use production instantiate semantics without Git writes | Yes | Disposable target per key |
| 12 | Polyrepo is never approved after a root-only partial check | Yes | Every target in the protection coverage set is evaluated |
| 13 | Blueprint checkpoint reuse does not replace publication comparison | Yes | Update optimization remains separate |
| 14 | Superseded WIP manifest/Registry contracts are discarded | Yes | No compatibility layer |

## Implemented REASONS contract

The REASONS canvases document the implemented end state. The following decisions are fixed and landed:

1. optional `repository` with explicit-root fallback and rejection of non-empty Module lists;
2. manifest validation and documentation aligned with the current Blueprint model;
3. complete Policy V1 reconstruction for Registry locators and per-version refs;
4. target-keyed published and expected snapshot boundaries;
5. 1→1 and N→1 evaluation on current composition semantics;
6. 1→N and N→N without shared-tag or root-only assumptions;
7. version-local policy and the immediate-publication-only guarantee;
8. verification of all layouts, referenced mapping failures, subset protection, clone failures, and no-op update behavior.
