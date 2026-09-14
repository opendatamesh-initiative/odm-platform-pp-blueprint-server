# SPDD Analysis: Protected-resources publication integrity and Policy adapter

This analysis defines the lasting publication-integrity capability and its temporary Policy V1 integration. The companion analysis `BDMD-5124-202609021721-[Analysis]-protected-resources-multi-repository-integrity.md` specializes the design for composed and multi-repository layouts.

## Authoritative baseline

The feature is still under development and has not been released. The current Blueprint and Registry designs are therefore a hard baseline, not a compatibility target:

- Blueprint manifests declare logical destinations in top-level `targetRepositories[]`, with exactly one destination marked `isRoot: true`.
- Routing is expressed by typed `instantiation[]` entries. `type: root` routes the parent Blueprint; `type: module` routes a composed catalog Module. Route destinations use `repo` and `destinationPath`.
- `composition[]` identifies catalog Modules and maps parameters; it does not contain routes.
- Catalog type is explicit: a `BLUEPRINT` owns the data product and descriptor, while a `MODULE` is a reusable child that cannot instantiate or update a product independently.
- Registry additional repository locators use `additionalDataProductRepos[].repositoryKey`.
- A Registry data-product version records the root ref in `tag` and independent non-root refs in `additionalTags[]`, keyed by `repositoryKey`. Tag names may differ across repositories.
- Registry publication validates snapshot metadata completeness but does not create or verify Git refs.

Superseded manifest shapes, `manifestKey`, and the reverted same-tag fan-out design are out of scope. No parser, payload, or migration compatibility is required for them.

## Original Business Requirement

# [Blueprint 2.0] Support for Protected Resources

## Original business requirement (high level description)

Build a validation process.

There needs to be a point where the constraint is validated during publication of a data product version.
Possible validation:
Blueprint service also becomes a policy adapter (as Observer already does). Clone the repository (given the tag), read the protected resources and generate hashes, and verify they match (for example, that the protected resources have not been modified).

Notes from earlier development:

Protected Resource Policy Extensions

A. File Immutability Policy

Objective: Ensure that generated data products are consistent with the original blueprint artifacts.

Description:

- At data product level: Compute hashes on the corresponding files/folders for protected resources using the repository state at the tagged version and the blueprint.
- Verification: Ensure that both hashes match for each protected resource.

Outcome:

- Guarantees that no unintended modifications occurred between blueprint definition and data product realization.

B. Parameter Sanity Check Policy

Objective: Validate that the data product is correctly derived from the declared parameters.

Description:

1. Compute hashes of selected files/folders from the data product repository at a given version tag.
2. Re-instantiate the blueprint using:
  - The recorded blueprint version
  - The stored parameter values
3. Compute hashes on the re-instantiated output.
4. Compare hashes for consistency.

Outcome:

Ensures that:

- The parameters declared in the metadata are correct.
- The data product faithfully reflects the blueprint instantiation.

Current scope clarification: the check must be blocking when configured as blocking, must not mutate Git repositories, and must return useful path-level failures. The current Policy V1 publication bridge must be supported without changing Notification or Policy Service. Composition and multi-repository layouts use only the final Blueprint manifest and Registry snapshot contracts described above.

## Domain Concept Identification

### Existing Concepts (from codebase)

- **Catalog Blueprint**: The data-product-owning template identity. It has catalog type `BLUEPRINT`, owns the descriptor template, and may be instantiated or used to update a product.
- **Catalog Module**: A reusable composition child with catalog type `MODULE`. It has no descriptor template, publishes only a one-destination/no-composition manifest, and cannot instantiate or update a product independently.
- **Blueprint version**: A published source snapshot containing the authoritative manifest for that release. Data-product lineage identifies the parent Blueprint version and resolved parent parameters used for generation.
- **Blueprint manifest**: Declares parameters, parent-owned protected resources, composition identities and parameter mappings, logical target repositories, the explicit root target, and typed source-to-destination routes.
- **Protected-resource policy**: A list owned by the parent Blueprint manifest. Each item identifies a post-instantiation destination path that must match the Blueprint-generated result at publication.
- **Instantiation layout**: Derived from target-repository cardinality and composition presence: one or many source components rendered into one or many destination repositories.
- **Instantiate pipeline**: Resolves parent and Module sources, maps parameters, applies typed routes, relocates Module sidecars, writes parent lineage and the descriptor only on the root destination, and produces one rendered result per target key.
- **Registry product repository aggregate**: `dataProductRepo` is the unkeyed root locator; `additionalDataProductRepos[]` contains non-root locators keyed by `repositoryKey`.
- **Registry version snapshot**: `tag` identifies the root repository snapshot; `additionalTags[]` identifies each non-root snapshot by `repositoryKey`. These are metadata assertions and are not proof that Git refs exist.
- **Policy V1 publication bridge**: Dispatches `DATA_PRODUCT_VERSION_CREATION` with `{currentState, afterState}` rather than the complete Registry V2 version resource.
- **Policy evaluation outcome**: A blocking or non-blocking pass/fail result returned to Policy. “Not applicable” is a successful result with an explicit reason.
- **Service Git credentials**: Blueprint-managed credentials used for source and published-target reads. Credentials do not travel in events or Policy payloads.

### New Concepts Required

- **Destination-scoped protection**: Every protected path resolves to exactly one logical target repository from the parent manifest. Source repository identity and Module identity are not destination scope.
- **Published target snapshot set**: The logical-keyed set of published repository trees selected by combining Registry product locators with Registry version refs.
- **Expected target snapshot set**: One locally rendered tree per logical target key, produced through the same semantics as production instantiate without using or mutating live product targets.
- **Complete evaluation context**: Parent Blueprint identity/version and parameters, the full parent manifest, and the logical-keyed published locator/ref information required by the protected paths.
- **Registry reconstruction adapter**: Temporary Policy V1 boundary that reconstructs the complete evaluation context from Registry. It performs Registry I/O but no hashing, rendering, or Git mutation.

### Key Business Rules

- Publication integrity is based on **re-instantiation and comparison**, not on stored manifest digest values.
- The parent catalog `BLUEPRINT` owns the policy. Protected-resource declarations from catalog `MODULE` versions are not inherited into a parent.
- Catalog `MODULE` versions must reject a non-empty `protectedResources` list. Accepting declarations that can never govern publication would be misleading.
- `protectedResources[].repository` is optional. A present value selects a logical `targetRepositories[].key`; omission or blank selects the sole target marked `isRoot: true`. Root shorthand is intentional because root protection is expected to be the common case.
- Protected paths use **post-instantiation destination coordinates**, after route placement, Module sidecar relocation, and root-only descriptor/lineage generation.
- The root target is the `targetRepositories[]` entry marked `isRoot: true`; it is never inferred from ordering or a reserved name.
- Each publication is governed only by the protected-resource list in its recorded parent Blueprint version. Later Blueprint versions may add, remove, or retarget protection without an additional cross-version invariant.
- Registry locators and refs form separate mappings:
  - root target key → `dataProductRepo` + version `tag`;
  - non-root target key → matching `additionalDataProductRepos[].repositoryKey` + matching `additionalTags[].repositoryKey`.
- Non-root refs must never fall back to the root `tag`. Each repository is cloned at its own recorded ref.
- Only target keys referenced by the effective protected-resource list must have complete Registry locator/ref mappings, and only those targets are cloned. Unreferenced manifest targets and Registry entries do not affect evaluation.
- Registry publication success is not evidence that a ref exists. Missing metadata, blank refs, clone failures, authentication failures, rendering failures, and timeouts fail closed whenever the policy is applicable.
- Integrity core must not call Registry. Registry lookup and Policy V1 payload reconstruction remain isolated in `old/v1`.
- Git validation is read-only. Local expected targets are disposable and no branches, commits, tags, or pull requests are pushed.
- The same hashing and path-safety semantics apply to published and expected trees. Failures distinguish missing published content, content not produced by the Blueprint, differing content, invalid paths, symlinks, and unsupported algorithms.
- No Blueprint lineage or an empty parent protected-resource list is not applicable. A declared policy that cannot be fully evaluated is a failure, not a silent pass.
- Current contracts are a hard cut. Superseded manifest fields, Registry `manifestKey`, and shared-tag behavior receive no compatibility handling.

## Strategic Approach

### Solution Direction

Keep one lasting integrity capability behind a removable Policy V1 adapter:

1. Policy invokes the Blueprint validator on the publication governance path.
2. The V1 adapter resolves product/version identity from the descriptor payload and retrieves the full Registry version and product information.
3. The adapter constructs a complete, logical-target-keyed evaluation context and invokes core integrity.
4. Core loads the recorded parent Blueprint version and validates whether the policy applies.
5. Core obtains published target snapshots only for target keys selected by the protected list.
6. Core locally re-instantiates the parent Blueprint and its referenced Modules into disposable target trees.
7. Each protected path is compared only within its resolved target key.
8. Core returns a domain outcome that the adapter maps to the Policy protocol.

The design supports all four layouts through one target-keyed model. Delivery may be incremental, but no phase may introduce a contract that assumes one product repository, one source clone, or one shared publication tag.

### Key Design Decisions

- **Current designs are authoritative**: Use only the final Blueprint manifest/catalog contracts and Registry `repositoryKey` plus per-version `additionalTags`. No compatibility code or analysis for superseded WIP shapes.
- **One combined integrity policy**: File immutability and parameter sanity are two outcomes of the same reconstruction-and-compare process.
- **Parent-owned policy**: Only the parent `BLUEPRINT` policy controls the data product. Module-originated files are protected by declaring their final destination paths on the parent.
- **Same rendering semantics as instantiate**: Reuse the production instantiation behavior through a read-only local target mechanism so parameter mapping, routing, relocations, descriptor rendering, and lineage cannot drift.
- **Target-keyed core boundary**: Core consumes logical-keyed published snapshot information and produces/uses logical-keyed expected trees. Policy and Registry DTO shapes remain outside core.
- **Separate locator and ref authority**: Product repository records answer “where”; version snapshot fields answer “which ref.” Joining them is explicit and exact.
- **Policy V1 isolation**: Registry searches/GETs and V1 payload adaptation stay under `old/v1`. Removal for Policy V2 must not change hashing or reconstruction semantics.
- **Service credentials**: Continue using Blueprint validator configuration rather than propagating secrets through Registry or Policy events.
- **Fail closed on incomplete applicable checks**: Registry metadata-only validation does not weaken the integrity gate; cloneability is established by the evaluator.
- **Recorded-version policy**: Evaluate exactly the recorded parent Blueprint version, including its protected list. Do not compare protection declarations across Blueprint versions.
- **Referenced-target coverage**: Require locator/ref completeness only for effective protected target keys and clone only those published targets.
- **Publication-time guarantee**: Current product locators are sufficient for the immediate publication gate. Repeatable historical re-evaluation after locator changes is not part of this feature.
- **Policy V2 deferred**: The current V1 adapter performs any required Registry enrichment. No lasting V2 enrichment abstraction is required now; if a future direct V2 event lacks required locator/ref data, augment that Registry event contract then.

### Resolved Decisions for REASONS

- **Optional target with root shorthand**: Keep the existing `repository` property. A non-blank value must match `targetRepositories[].key`; omission or blank resolves to the explicit `isRoot: true` target. This favors the common root-only declaration without weakening root semantics.
- **Reject Module declarations**: Publishing a catalog `MODULE` with non-empty `protectedResources` is invalid. Modules cannot own product publication policy; the parent must declare final paths for any Module-originated output it wants to protect.
- **Recorded-version policy only**: Evaluate the list from the recorded parent Blueprint version. A subsequent Blueprint version may remove or retarget protection; no monotonic-protection check is added.
- **Referenced keys only**: Require complete locator/ref data only for target keys selected by protected declarations and clone only those targets. Empty protection is not applicable; a subset protects and evaluates only that subset.
- **Immediate publication only**: Current Registry product locators are authoritative for the publication being evaluated. Locator snapshots and repeatable later evaluation are deferred.
- **No Policy V2 requirement now**: Keep Registry enrichment in the Policy V1 adapter. If direct V2 delivery later lacks complete locator/ref information, extend `EmittedEventDataProductVersionPublicationRequestedRes` (or its then-current successor) rather than adding speculative infrastructure now.

### Alternatives Considered

- **Persist hashes in `protectedResources[].integrity`**: Rejected. Dynamic reconstruction verifies both source version and parameters and avoids stale embedded digests.
- **Hash source templates directly**: Rejected. Protected paths describe generated destination content, not Velocity source files.
- **Use Blueprint checkpoint tags as publication refs**: Rejected. Checkpoints represent pure generation baselines, not necessarily the product commits being published.
- **Infer non-root refs from the root tag**: Rejected. Final Registry explicitly records independent per-repository refs.
- **Clone only the root in a polyrepo layout**: Rejected. Partial evaluation could approve tampering in a protected secondary repository.
- **Call Registry from integrity core**: Rejected. It couples lasting domain behavior to the temporary Policy V1 transport gap.
- **Create a separate renderer for integrity**: Rejected. Rendering drift would create false passes or false failures.
- **Carry Git credentials in events**: Rejected for security and ownership reasons.
- **Require every target to be protected**: Rejected. A Blueprint may intentionally protect no targets or only a subset; unrelated locator/ref entries are outside the check.
- **Enforce monotonic protection across versions**: Rejected for current scope. The recorded Blueprint version is the complete policy authority for its publication.
- **Snapshot repository locators per version now**: Rejected as unnecessary for the immediate publication gate. Revisit if historical re-evaluation becomes a requirement.

## Risk & Gap Analysis

### Accepted Boundaries and Deferred Concerns

- **Root shorthand is implicit by design**: An omitted `repository` always means the explicit `isRoot: true` target; it must never be inferred from order or name.
- **Protection may evolve**: A later Blueprint version may weaken or retarget protection. This is intentional unless a separate future update invariant is introduced.
- **Historical locator drift is accepted**: Product locators may change after publication. This feature guarantees the immediate publication decision, not repeatable historical evaluation.
- **Policy V2 transport is deferred**: The V1 adapter supplies complete context today. Extend the Registry V2 publication event only if a future direct integration needs more fields.

### Edge Cases

- Root target is not the first declared target.
- Root `tag` is blank even though Registry accepts the publication.
- A non-root locator exists without a matching version ref, or vice versa.
- A referenced Registry key is missing or duplicated, while unrelated extra or missing target mappings exist.
- Two logical target keys resolve to the same physical remote.
- A protected path exists under the same relative name on multiple targets.
- A Module is routed to multiple destinations or aliases.
- A protected glob matches nothing on one or both trees.
- A published ref exists but points to unintended content; Registry does not verify provenance.
- Mixed providers require different configured service credentials.
- Evaluation times out after some source or target clones; all temporary trees still require cleanup.

### Technical Risks

- **Cross-service join drift**: Blueprint code outside this feature may trim logical keys while Registry uses exact strings. New integrity joins must follow Registry’s exact semantics without expanding into cross-service normalization cleanup.
- **Metadata-only Registry snapshots**: Clone failures remain possible after successful publication. Preserve fail-closed behavior and actionable messages.
- **Source/target confusion**: Composition adds source repositories independently of destination repository count. Keep these dimensions separate in contracts and tests.
- **Render-semantic drift**: Any integrity-only copy/relocation logic can diverge from instantiate. Maintain one render path.
- **Temporary-resource growth**: Composed polyrepo evaluation may clone several sources and published targets. Ensure bounded timeout and deterministic cleanup.
- **Policy V1 latency**: Registry round trips plus Git operations occur on a blocking governance path. Keep one explicit evaluation timeout and report infrastructure failures clearly.
- **Repository locator drift**: Immediate publication validation is reliable against current locators; delayed re-evaluation may not be.

### Acceptance Criteria Coverage

| AC# | Description | Addressable? | Gaps/Notes |
|-----|-------------|--------------|------------|
| 1 | Protected resources are evaluated during data-product-version publication | Yes | Policy V1 continues through its existing bridge |
| 2 | Blueprint acts as a blocking Policy adapter when configured | Yes | Blocking remains Policy configuration |
| 3 | Evaluation reconstructs the recorded Blueprint output with recorded parameters | Yes | Uses current instantiate semantics |
| 4 | Published and expected protected paths are compared with SHA-256 and path-level outcomes | Yes | Existing digest policy remains valid |
| 5 | Parent `BLUEPRINT` owns protection; Module lists are not inherited | Yes | Non-empty Module lists are rejected |
| 6 | All manifest layouts use destination-keyed evaluation | Yes | Optional `repository`; omission or blank means explicit root |
| 7 | Root and non-root repositories use their own Registry-recorded refs | Yes | Root blank-tag validation must be enforced by Blueprint |
| 8 | Referenced Registry metadata gaps and Git failures fail closed | Yes | Unreferenced targets do not affect evaluation |
| 9 | Integrity core remains independent of Registry and Policy V1 DTOs | Yes | V1 reconstruction stays in `old/v1` |
| 10 | Validation performs no Git mutations | Yes | Disposable expected targets and read-only published clones |
| 11 | Notification and Policy Service require no changes for Policy V1 | Yes | Extend a future V2 Registry event only if direct delivery lacks required context |
| 12 | Superseded WIP schemas and payloads receive no compatibility handling | Yes | Explicit hard-cut decision |
