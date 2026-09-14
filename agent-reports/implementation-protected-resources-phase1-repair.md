# BDMD-5124 Phase 1 implementation report

Repair of protected-resources integrity against the final Blueprint manifest (`targetRepositories[]`, explicit `isRoot`, typed `instantiation[]`, route `repo`). Baseline: `374a449a3fec42a1da0598d7cd2ca72b04b5c53b`.

This phase is a compile-green, 1→1 / N→1 handoff. Complete 1→N / N→N publication evaluation and full Policy V1 locator/ref reconstruction are deferred to Implementer 2 (Slice 2).

## Completed operations

### Lasting integrity prompt (`BDMD-5124-202608210930`)

1. **Preserve existing integrity primitives** — `IntegrityOutcome`, mismatch vocabulary, factory composition, credential configuration, and presenter methods kept. Policy/Registry types stay out of the use-case package.
2. **Update domain publication context** — added `KeyedProductRepoLocator` and `KeyedProductRepoRef`. Replaced the root-only command with `rootPublicationRef`, `rootProductRepo`, raw `additionalProductRepos`, and `additionalRefs`. Null lists/parameters become empty immutable collections; lists are not collapsed to maps before duplicate checks.
3. **Enforce parent-only authoring at Blueprint-version publication** — `PublishBlueprintVersionManifestOutboundPort.hasProtectedResources(JsonNode)` via `ManifestParserFactory`. Catalog `MODULE` with a non-empty list is rejected. Empty/omitted lists remain valid. Parent composition validation aggregates an issue when a persisted Module version has a non-empty list (defense in depth). Integrity still reads only the parent list.
4. **Update `EvaluateProtectedResourcesIntegrity`** — removed `ManifestInstantiationRepository` and object-shaped instantiation access. Root comes from the sole `isRoot: true` target. Blank/omitted `repository` resolves to that root; non-blank values use existing trim canonicalization then exact Registry-key joins. Polyrepo is no longer “not applicable”. Protection-scoped locator/ref resolution fails closed before Git. Re-instantiation runs once; each protected target is cloned at its own ref.
5. **Update expected-tree boundary** — `TargetWorkingTrees` with `get`, `keys`, and deterministic close. Instantiate port returns keyed trees. Missing expected trees name the logical key, not filesystem paths.
6. **Preserve validator configuration** — unchanged `BlueprintValidatorProperties` / `ValidatorGitCredentialHeaders`.
7. **Preserve and harden product Git cloning** — one clone per protected target at the supplied ref; `.git`-free snapshots preserve symlink entries without following them; cleanup on failure and close.
8. **Preserve and harden digest** — SHA-256 / lowercase hex kept. Absolute declared paths are rejected instead of stripping the leading separator. Traversal still fails as `INVALID_PATH`. Symlinks still fail as `SYMLINK`. Evaluation still ignores `integrity.value`.

### Layout prompt (`BDMD-5124-202609031327`) — Phase 1 slice

1. **Preserve manifest field and structural validation** — `ManifestProtectedResource.repository` and existing visitors/validators kept. Stale `instantiation.repositories` / `instantiation.root.repository` wording updated in the tests this slice owns.
2. **Repair final-manifest use** — as above; `POLYREPO_NOT_APPLICABLE_MESSAGE` removed.
3. **Update local re-instantiation adapter** — parses `targetRepositories[]`, requires explicit `isRoot`, builds a synthetic `TargetRepositoryDto` for every declared key, runs `buildInstantiateBlueprintVersionForLocalValidation`, and adapts every `RenderedTreeSnapshot` entry into `TargetWorkingTrees`.
4. **Preserve Slice 1 local Git** — `openSources` / `openTarget`, no-op push, origin-free orphan checkout, and snapshot cleanup kept. Snapshots now preserve symlinks.
5. **V1 mapper compile-green handoff** — `ProtectedResourcesPolicyValidatorService` constructs the new command with empty additional locator/ref lists. Full additional reconstruction is Slice 2.
6. **1→1 and N→1 tests repaired** — fixtures use explicit `BLUEPRINT` parents and `MODULE` children. Module manifests used for successful parent publish no longer carry `protectedResources`.

## Deferred Slice 2 operations

From the layout prompt and lasting-integrity Gherkin, **not implemented in this commit**:

| Operation / scenario | Owner |
| --- | --- |
| Complete published 1→N / N→N evaluation through Policy V1 (independent additional locators/refs on the wire) | Implementer 2 |
| Align Policy V1 reconstruction: retain/fetch `additionalDataProductRepos[]`, retain/default `additionalTags[]`, map `repositoryKey` without deduplication, remove unconditional root-metadata rejection | Implementer 2 |
| IT `whenPolyrepoProtectsOnlyRootThenCloneOnlyRoot` | Implementer 2 |
| IT `whenPolyrepoProtectsOnlyAdditionalTargetThenRootMetadataNotRequired` | Implementer 2 |
| IT `whenMultipleTargetsProtectedThenCloneEachRecordedRef` | Implementer 2 |
| IT `whenPolyrepoWithCompositionMatchesThenPass` | Implementer 2 |
| Update checkpoint isolation: `whenUnchangedCheckpointIsReusedThenTamperedPublicationStillFailsIntegrity` | Implementer 2 |
| Do not modify `UpdateDataProductFromBlueprintVersion` / `contentUnchanged` (already untouched; Slice 2 must keep it that way and add the regression IT) | Implementer 2 |
| Full additional locator/ref reconstruction and Slice 2 mapper behavior in `old/v1` | Implementer 2 |

Domain unit tests already cover additional-target metadata, independent refs, duplicate locators, unreferenced extras, physical aliasing, and fail-fast clone behavior **when the command is populated directly**. They do not prove V1 JSON reconstruction.

Architectural seam for Phase 2: `EvaluateProtectedResourcesIntegrityCommand.additionalProductRepos` / `additionalRefs` plus `ProtectedResourcesPolicyValidatorService.mapToIntegrityCommand`. Phase 2 should fill those lists from Registry JSON without changing hashing, Git, or target-selection policy.

## Files changed

### Main

- `EvaluateProtectedResourcesIntegrity.java` — target-keyed use case; final manifest; no polyrepo skip
- `EvaluateProtectedResourcesIntegrityCommand.java` — keyed command shape
- `KeyedProductRepoLocator.java`, `KeyedProductRepoRef.java` — new domain records
- `TargetWorkingTrees.java`, `CloseableWorkingTree.java`, `WorkingTreeSnapshotCopier.java` — keyed expected/published tree lifetime and symlink-preserving copy
- `EvaluateProtectedResourcesIntegrityInstantiateOutboundPort.java` / `*Impl.java` — all declared target DTOs, synthetic local remotes
- `EvaluateProtectedResourcesIntegrityGitOutboundPortImpl.java` — symlink-preserving published snapshots
- `EvaluateProtectedResourcesIntegrityDigestOutboundPortImpl.java` — reject absolute paths
- `PublishBlueprintVersion.java` + manifest port/impl — Module list rejection and composition defense in depth
- `ProtectedResourcesPolicyValidatorService.java` — compile-green command constructor
- `InstantiationScenario.java` — javadoc now names `targetRepositories[]`

### Tests / fixtures

- `EvaluateProtectedResourcesIntegrityTest.java` — new unit coverage for coverage resolution and recorded-version policy
- `EvaluateProtectedResourcesIntegrityInstantiateOutboundPortImplTest.java`
- `EvaluateProtectedResourcesIntegrityGitOutboundPortImplTest.java`
- `EvaluateProtectedResourcesIntegrityDigestOutboundPortImplTest.java` — `absoluteAndTraversalPathsAreInvalid`
- `InstantiateBlueprintVersionLocalGitOutboundPortTest.java` — symlink preservation and multiple local targets
- `InstantiateBlueprintVersionOdmBlueprintManifestOutboundPortTest.java` — final-manifest wording
- `BlueprintVersionsUseCaseControllerIT.java` — Module rejection, incomplete integrity object, composition defense in depth, root-key publish
- `ProtectedResourcesValidatorControllerIT.java` — explicit `BLUEPRINT`/`MODULE` fixtures; removed polyrepo-not-applicable and Module-list-ignored tests
- `src/test/resources/manifest/invalid/unknown-protected-resource-repository.yaml` — final manifest shape

### Not committed (pre-existing unrelated local changes)

- `mvnw` mode change
- `src/main/resources/application-localpostgres.yml`

## Tests and results

`./mvnw clean compile` — **pass**

`git diff --check` — **pass** (no whitespace errors in this change set)

Targeted tests — **pass**:

| Command | Result |
| --- | --- |
| `EvaluateProtectedResourcesIntegrityTest` | pass |
| `EvaluateProtectedResourcesIntegrityDigestOutboundPortImplTest` | pass |
| `EvaluateProtectedResourcesIntegrityMessageTest` | pass |
| `EvaluateProtectedResourcesIntegrityInstantiateOutboundPortImplTest` | pass |
| `EvaluateProtectedResourcesIntegrityGitOutboundPortImplTest` | pass |
| `InstantiateBlueprintVersionLocalGitOutboundPortTest` | pass |
| `InstantiateBlueprintVersionOdmBlueprintManifestOutboundPortTest` | pass |
| `BlueprintVersionsUseCaseControllerIT` | pass |
| `ProtectedResourcesValidatorControllerIT` | pass (1→1 matching/mismatch, N→1 match/mismatch/empty protection) |
| `OldV1ProtectedResourcesValidatorControllerIT` | pass |
| `ProtectedResourcesValidatorPolicySubscriberTest` | pass |

Full `./mvnw test` was not run; Slice 2 ITs were not added.

## Deviations

1. **V1 mapper is compile-green only.** Additional locator/ref lists are `List.of()`. 1→1 / N→1 ITs only need root metadata. This is the assigned Phase 1 seam, not a prompt rewrite.
2. **`missingBlueprintRepositoryFails` IT removed.** After explicit `BlueprintType`, REST cannot persist a Blueprint without a Git repository (`HTTP remote URL` is required). The same scenario is covered by unit test `whenSourceBlueprintRepositoryMissingThenInfrastructureFailure`.
3. **No prompt file edits.** The layout prompt still says to implement the companion V1 prompt “in the same delivery” and lists Slice 2 ITs. Delivery was split by this assignment; the prompt was left intact so Implementer 2 still has the full contract.
4. **Stale Javadocs outside this slice** (for example some instantiate/update ITs still mention `instantiation.repositories`) were not rewritten except where this phase already touched the test.

## Risks

- Phase 2 must populate additional command lists. Until then, a Policy V1 payload that protects a non-root target fails closed on missing metadata (correct fail-closed, but not a successful 1→N check).
- Local re-instantiation already builds disposable trees for **every** declared target key. Polyrepo evaluation cost starts in Phase 1 for 1→1/N→1 manifests that happen to declare extra keys; production 1→1/N→1 fixtures declare one destination.
- Composition defense in depth now flags persisted Module versions that still have `protectedResources` (including older CRUD-created fixtures). Successful parent-publish tests were updated; other 400 tests may show extra aggregated issues but still assert their original fragments.
- Symlink copy uses `Files.createSymbolicLink`. This is required by the REASONS; Windows without symlink privilege is outside the current Linux/WSL test environment.

## Prompt / code gaps

- The layout prompt’s “implement the companion V1 prompt in the same delivery” conflicts with the assigned Phase 1/Phase 2 split. No prompt edit was made; Implementer 2 should treat the V1 prompt as still authoritative for reconstruction.
- Catalog REST cannot represent “Blueprint with no repository” after `BlueprintType` became required. The lasting prompt’s IT mapping for that scenario is no longer executable as an HTTP fixture.
- `unknown-protected-resource-repository.yaml` still used the deleted object-shaped instantiation model; it was rewritten to the final manifest. Other invalid fixtures in this repo were already final-shaped.
- No code/prompt conflict required a REASONS change. Absolute-path digest rejection and symlink preservation were implemented as specified.

## Gate correction (close order)

Phase 1 gate: `TargetWorkingTrees` promised deterministic close, but `Map.copyOf(trees)` does not preserve `LinkedHashMap` encounter order.

**Fix:** constructor now stores `Collections.unmodifiableMap(new LinkedHashMap<>(trees))`, so `close()` and `keys()` follow insertion order. Every tree is still attempted; the first `RuntimeException` is rethrown after the remaining closes.

**Test:** `TargetWorkingTreesTest.closeFollowsInsertionOrderAndContinuesAfterFailure` — non-sorted insertion (`gamma`, `alpha`, `beta`); middle close throws; all three close in insertion order; first failure is rethrown.

Targeted tests — **pass**:

| Command | Result |
| --- | --- |
| `TargetWorkingTreesTest` | pass |
| `EvaluateProtectedResourcesIntegrityInstantiateOutboundPortImplTest` | pass |

## Review-fix addendum (LOW-1, LOW-3, Phase 1 LOW-4)

Independent review `agent-reports/review-protected-resources-final.md` confirmed three Phase 1-owned leftovers. Docs-only plus a stronger recorded-version test; no production behavior change.

1. **LOW-1.** `whenLaterBlueprintChangesProtectionThenRecordedVersionListIsUsed` now stores materially different v1 (`v1-only.txt` on the explicit root) and v2 (`v2-only.txt` on `infra-repo`) policies. The persistency fake throws if version `2.0.0` is loaded or its manifest is read. Full Gherkin Javadoc is unchanged.
2. **LOW-3.** `InstantiateBlueprintVersion.renderDescriptorAndLineageOnRootRepository` Javadoc now names `targetRepositories[].isRoot: true`.
3. **LOW-4 (Phase 1-owned).** Final-manifest Javadocs in `InstantiateBlueprintVersionOdmBlueprintManifestOutboundPortTest` and the matching empty-root-targets block in `BlueprintVersionsUseCaseControllerIT` now describe typed `instantiation[]` / `targets[]`, route `repo`, and `targetRepositories[]`. Instantiate/update ITs listed in the review remain out of this slice.

`./mvnw -q -DskipTests compile test-compile` — **pass**

| Command | Result |
| --- | --- |
| `EvaluateProtectedResourcesIntegrityTest` | pass |
| `InstantiateBlueprintVersionOdmBlueprintManifestOutboundPortTest` | pass |
