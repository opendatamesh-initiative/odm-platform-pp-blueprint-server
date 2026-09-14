# BDMD-5124 Phase 2 implementation report

Complete 1→N / N→N protected-resources publication integrity and Policy V1 reconstruction/mapping on top of Phase 1 (`cf5be2b`, `2a9ddca`). Baseline: `2a9ddca82ce1ec12db6963415899e03c2064a048`.

This phase fills the Phase 1 seam: `EvaluateProtectedResourcesIntegrityCommand.additionalProductRepos` / `additionalRefs` are now populated from Registry JSON. Hashing, Git mutation rules, and target-selection policy remain in the lasting use case.

## Key behavior

- Policy V1 reconstruction retains/defaults `additionalDataProductRepos[]` and `additionalTags[]`. It no longer fails solely because root `tag` or root clone URL is missing.
- The V1 mapper copies every additional locator/ref in encounter order using exact `repositoryKey` (never `manifestKey`). Duplicates, blanks, and incomplete entries are preserved for core.
- Core still derives protection coverage after root fallback, requires/clones only referenced targets, uses independent refs, re-instantiates once, and fails closed on referenced missing/blank/duplicate/conflicting metadata.
- 1→N and N→N Policy evaluate ITs now pass with keyed additional locators/refs on the wire.

## Acceptance-criteria traceability

### Lasting integrity (`BDMD-5124-202608210930`)

| AC / scenario | Evidence |
| --- | --- |
| Empty parent protection is not applicable | Phase 1 `lineageWithEmptyProtectedResourcesReturnsNotApplicable` preserved |
| Optional/blank `repository` → explicit root | Unit: `whenRepositoryOmittedThenResolveExplicitRootEvenIfNotFirst`. Publication-only IT: `whenRepositoryOmittedThenPublishAcceptsExplicitRootShorthand` |
| Module non-empty list rejected | Phase 1 `whenPublishModuleWithProtectedResourcesThenReturn400` preserved |
| Referenced additional target uses own locator/ref | Phase 1 unit test + Phase 2 `whenMultipleTargetsProtectedThenCloneEachRecordedRef` |
| Missing/duplicate referenced metadata fails before Git | Phase 1 unit tests preserved |
| Unreferenced metadata ignored | Phase 1 unit + Phase 2 `whenPolyrepoProtectsOnlyRootThenCloneOnlyRoot` |
| Matching content passes without Git mutation | Phase 1 ITs preserved and still pass |
| Recorded-version policy only | Phase 1 unit test preserved |
| No successful root-only partial polyrepo check | Polyrepo ITs clone/compare every protected key; additional-only does not require root metadata |

### Policy V1 (`BDMD-5124-202608241546`)

| AC / scenario | Evidence |
| --- | --- |
| Full root and additional reconstruction | `v1AfterStateReconstructsFullPublicationContext`, `mapsRootAndAdditionalLocatorRefLists` |
| Missing arrays default empty | `missingAdditionalReposAfterGetProductDefaultsToEmptyArray` asserts both `additionalDataProductRepos` and `additionalTags` |
| Missing root metadata deferred | `missingRootMetadataStillDelegatesForProtectionScopedValidation` |
| Duplicate keyed entries preserved | `duplicateAdditionalEntriesRemainVisibleToIntegrity` |
| V2-shaped pass-through skips Registry | preserved/extended `v2ShapedPayloadSkipsRegistryAndDelegates` |
| Missing identity / Registry lookup fail closed | preserved tests |
| No lineage is not applicable | preserved `v1AfterStateWithReconstructedContentWithoutLineageIsNotApplicable` |
| Timeout seals outcome | `timeoutSealsOutcomeAndReturnsFalse` |
| Registration unchanged | preserved subscriber tests |
| `repositoryKey` not `manifestKey` | reconstruction/mapper/ITs; former `manifestKey` fixture removed |

### Layout / multi-repo (`BDMD-5124-202609031327`)

| AC / scenario | Evidence |
| --- | --- |
| 1→N root-only subset | `whenPolyrepoProtectsOnlyRootThenCloneOnlyRoot` |
| 1→N additional-only subset | `whenPolyrepoProtectsOnlyAdditionalTargetThenRootMetadataNotRequired` |
| Independent refs | `whenMultipleTargetsProtectedThenCloneEachRecordedRef` |
| N→N same-key comparison | `whenPolyrepoWithCompositionMatchesThenPass` |
| Physical aliasing | Phase 1 `whenProtectedKeysShareRemoteThenEvaluateIndependently` |
| Fail-fast infrastructure | Phase 1 `whenProtectedTargetCloneFailsThenRemainingClonesAreOptional` |
| Checkpoint isolation | `whenUnchangedCheckpointIsReusedThenTamperedPublicationStillFailsIntegrity` on both Policy evaluate IT and update IT |
| Local Git multiple targets | `openSourcesClonesTagsAndOpenTargetDoesNotCloneProduct` opens two local targets |
| Every expected tree retained/closed | Phase 1 instantiate adapter test preserved |

Phase 1 parent-only policy, Module rejection, exact joins, read-only Git, deterministic cleanup, and keyed domain resolution were not changed in this commit.

## Files changed

### Main

- `ReconstructPublicationRequestedService.java` — default `additionalTags[]`; GET product when nested product/root repo/additional array is absent; no unconditional root-metadata rejection
- `ProtectedResourcesPolicyValidatorService.java` — map `additionalDataProductRepos[]` and `additionalTags[]` into domain lists with exact `repositoryKey`
- `old/v1/README.md` — reconstruction now carries additional locators/refs; missing root metadata is protection-scoped
- `manifest/README.md` — 2.3/2.4 examples show destination-scoped `protectedResources`

### Tests

- `ReconstructPublicationRequestedServiceTest.java` — full context, empty `additionalTags`, deferred root metadata, `repositoryKey`
- `ProtectedResourcesPolicyValidatorServiceTest.java` — new mapper/timeout coverage
- `ProtectedResourcesValidatorControllerIT.java` — polyrepo ITs; `manifestKey` replaced
- `BlueprintVersionsUseCaseControllerIT.java` — omitted-repository publish on a non-first root
- `BlueprintUpdateDataProductControllerIT.java` — checkpoint reuse then tampered publication still fails integrity
- `InstantiateBlueprintVersionLocalGitOutboundPortTest.java` — two local expected targets

### Report

- `agent-reports/implementation-protected-resources-phase2-multirepo.md` (this file)

### Not committed (pre-existing unrelated local changes)

- `mvnw` mode change
- `src/main/resources/application-localpostgres.yml`
- existing preliminary reports (`impact-summary-*`, `change-analysis-*`)

## Commands and results

| Command | Result |
| --- | --- |
| `./mvnw clean compile` | **pass** |
| `./mvnw -DskipTests test-compile` | **pass** (after restoring a javadoc fence in the update IT) |
| `git diff --check` | **pass** |
| Unit tests listed below | **pass** |
| Targeted ITs listed below | **pass** |
| `./mvnw test` | **pass** (Surefire unit tests; ITs are Failsafe/`*IT` and were run separately via `-Dtest=`) |

Targeted unit tests — **pass**:

- `EvaluateProtectedResourcesIntegrityTest`
- `EvaluateProtectedResourcesIntegrityDigestOutboundPortImplTest`
- `EvaluateProtectedResourcesIntegrityMessageTest`
- `EvaluateProtectedResourcesIntegrityInstantiateOutboundPortImplTest`
- `EvaluateProtectedResourcesIntegrityGitOutboundPortImplTest`
- `TargetWorkingTreesTest`
- `InstantiateBlueprintVersionLocalGitOutboundPortTest`
- `InstantiateBlueprintVersionOdmBlueprintManifestOutboundPortTest`
- `ProtectedResourcesValidatorPolicySubscriberTest`
- `ReconstructPublicationRequestedServiceTest`
- `ProtectedResourcesPolicyValidatorServiceTest`

Targeted ITs — **pass**:

- `ProtectedResourcesValidatorControllerIT` (including new polyrepo methods)
- `OldV1ProtectedResourcesValidatorControllerIT`
- `BlueprintVersionsUseCaseControllerIT`
- `BlueprintUpdateDataProductControllerIT#whenUnchangedCheckpointIsReusedThenTamperedPublicationStillFailsIntegrity`

`./mvnw verify` (Failsafe full IT suite) was not run; named IT classes above were executed through Surefire `-Dtest=`.

## Deviations

1. **No prompt file edits.** Assigned Phase 2 matches the three REASONS contracts. The layout prompt’s “implement the companion V1 prompt in the same delivery” is satisfied by this phase after the intentional Phase 1/2 split.
2. **Local Registry workspace is not the assigned contract.** `/home/nicolapavin/git/odm-platform-pp-registry-server` still has `manifestKey` and no `additionalTags`. Implementation follows the user-assigned authoritative fields: root `dataProductRepo`/`tag`, additional `repositoryKey` locators, and `additionalTags[].repositoryKey/tag`.
3. **Checkpoint isolation is API-level, not a combined update+Git-state proof of tag reuse.** The update IT runs a `contentUnchanged` update, then evaluates a tampered published snapshot at `publication-v1`. Integrity clones that publication ref, not `blueprint-v*`. `UpdateDataProductFromBlueprintVersion` / `contentUnchanged` were not modified.
4. **1→N IT fixtures rewrite example-2.3 routes** to `infrastructure/` and `docs/` so they match the existing source-repo test files. Production example-2.3 still documents `terraform/` / `application/`.
5. **Stale Javadocs** for deleted `instantiation.repositories` / `instantiation.root.*` fields in instantiate/update ITs and `UpdateDataProductCommandRes` were rewritten in the review-fix commit (see addendum).

## Unresolved risks

- Until Registry is on the `repositoryKey` + `additionalTags` contract, a live V1 reconstruction against the current local Registry tree would not supply additional refs. Core would fail closed for protected non-root targets (correct), but 1→N would not pass in that environment.
- Reconstruction still GETs the product when the nested additional-repos array is absent, even for root-only protection. A product GET failure then fails closed before core can ignore unreferenced targets. This matches the V1 prompt’s “GET when additional array is absent” rule.
- `Files.createSymbolicLink` remains required; Windows without symlink privilege is outside the current Linux/WSL test environment.

## Questions requiring human judgment

1. Should Blueprint fail closed (or warn) if a reconstructed additional entry still uses legacy `manifestKey` with no `repositoryKey`, or is silent omission the intended hard cut? This phase implements the hard cut: `manifestKey` is ignored.
2. When should Registry’s V2 publication event be extended for a future direct Policy V2 path? Not done here, per prompt.
3. Is `./mvnw verify` (full Failsafe IT suite) required before merge, given `./mvnw test` only runs Surefire unit tests in this POM?

## Prompt / code gaps

None that required a REASONS change. Reality that differed from the prompt was the Phase 1/2 delivery split (already documented) and the local Registry checkout lagging the assigned `repositoryKey`/`additionalTags` contract.

## Review-fix addendum

Addresses remaining confirmed findings from `agent-reports/review-protected-resources-final.md` without changing lasting integrity rules. Does not undo Phase 1 review-fix `61a8475`.

### MEDIUM-1 — Distinct N→N Module sources

`whenPolyrepoWithCompositionMatchesThenPass` now uses separate ingest/consume source trees (`ingest-only.txt` / `consume-only.txt`). Each Module clone URL fragment (`module-storage` / `module-serving`) resolves to its own tree. Protected paths are `pipelines/batch/ingest-only.txt` (root) and `services/consumer/consume-only.txt` (`api-repo`). Swapping Module sources or destination trees fails the comparison.

### MEDIUM-2 — Ref-aware published Git fixture

`stubPublishedGit` validates `RepositoryPointerTag` against an expected product ref when one is configured. A mismatched ref serves an empty tree. Applied to additional-only (`infra-v9`), root+additional (`root-v3` / `infra-v9`), and N→N (`publication-v1` / `api-v4`). Parent/Module source clones are still served by URL fragment only, so valid source tags keep working. 1→1 and N→1 helpers still omit expected product refs.

### LOW-2 — Honest publication-only omitted-repository IT

Renamed `whenRepositoryOmittedThenUseIsRootTarget` → `whenRepositoryOmittedThenPublishAcceptsExplicitRootShorthand`. Javadoc states publication-only 201 coverage and points to `EvaluateProtectedResourcesIntegrityTest.whenRepositoryOmittedThenResolveExplicitRootEvenIfNotFirst` as the explicit-root integrity proof.

### LOW-4 — Stale final-manifest comments

Rewrote remaining `instantiation.repositories` / `instantiation.root.repository` / `instantiation.root.targets` Javadocs in `BlueprintUpdateDataProductControllerIT`, `BlueprintInstantiationControllerIT`, and the `UpdateDataProductCommandRes` schema description. `BlueprintVersionsUseCaseControllerIT` / `InstantiateBlueprintVersion` wording from `61a8475` was left as-is.

### Documentation polish

`old/v1/README.md` intro now says Policy V2 removal requires nested root tag + product repo **and** keyed additional locators/refs.

### Commands and results (this commit)

| Command | Result |
| --- | --- |
| `./mvnw -DskipTests test-compile` | **pass** |
| `git diff --check` | **pass** |
| `./mvnw -Dtest=ProtectedResourcesValidatorControllerIT,EvaluateProtectedResourcesIntegrityTest,BlueprintVersionsUseCaseControllerIT#whenRepositoryOmittedThenPublishAcceptsExplicitRootShorthand test` | **pass** (34 tests, 0 failures) |

`./mvnw verify` (full Failsafe IT suite) was not run.

### Not fully resolved

None of the assigned review findings. Intentionally unchanged: 1→1 / N→1 / root-only polyrepo stubs still omit expected product refs; `EvaluateProtectedResourcesIntegrityTest.whenRepositoryOmittedThenUseIsRootTarget` remains a one-line alias of the explicit-root unit test.
