# SPDD Analysis: Protected-resources integrity policy adapter (Blueprint)

Source vision: `odm-platform-pp-blueprint-server/spdd/vision/BDMD-5124-202608201019-[Vision]-protected-resources-policy-validation.md` (Policy and Notification untouched; Blueprint `old/v1` subscribes to Policy V1 `DATA_PRODUCT_VERSION_CREATION`, fetches Registry to reconstruct the V2 version resource, then calls the existing integrity use case).

This analysis is scoped to **`odm-platform-pp-blueprint-server`**. Registry’s V2 publication event and V1 Policy bridge are consumed as-is. Notification and Policy Service are **out of scope** (no changes). Observer Blindata is a read-only pattern reference. The integrity use case is **already implemented**; this slice is the removable V1 adapter around it.

## Original Business Requirement

# [Blueprint 2.0] Supporto a feature Protected Resources

## Original business requirement (high level description)

Sviluppo processo di validazione

Serve un punto dove venga validato il constraint durante la pubblicazione di una data product version.
Posibile validazione:
Blueprint service diventa anche un policy adapter (come fa ora anche l’Observer). Fare il clone della repository (dato il tag), leggere le protected resources e generare gli hash, e verficare che coincidano (e.g. che non siano state modificate le protected resources)

NOTE venute fuori da sviluppi precedenti:

Protected Resource Policy Extensions

The protected resource section in the blueprint manifest must be extended to support two additional policy types:

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



## Initial analysis (high level)

Evento DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED @odm-platform-pp-registry-server/src/main/java/org/opendatamesh/platform/pp/registry/rest/v2/resources/dataproductversion/events/emitted/EmittedEventDataProductVersionPublicationRequestedRes.java , policy service cattura evento, dispatch dell'evento ai policy engine per richiesta validazine, eseguono la validazione dell'evento
Sottoscrivere blueprint service a policy service come adapter se protected resources e' attivo. Riceve evento, clona repo in locale, vede se protected resource modificate calcolando hashes e confrontando.
Observer fa anche da validator, prendere spunto da li per implementazione validator package: @odm-platform-adapter-observer-blindata/src/main/java/org/opendatamesh/platform/up/metaservice/blindata/validator -> aggiungere package equivalente sul blueprint con evento DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED.
Probabile sia necessario modificare evento @odm-platform-pp-registry-server/src/main/java/org/opendatamesh/platform/pp/registry/rest/v2/resources/dataproductversion/events/emitted/EmittedEventDataProductVersionPublicationRequestedRes.java @odm-platform-pp-registry-server/src/main/java/org/opendatamesh/platform/pp/registry/dataproductversion/services/usecases/publish/DataProductVersionPublisherNotificationOutboundPortImpl.java e aggiungere repo da clonare per evitare che blueprint contatti registry

Protected resources possono essere semplici file o folders, confronto hash direttamente. Se sono template velocity potrei voler verificare che template non siano stati modificati: instanziare blueprint con params del data product e calcolo hash

Per implementazione validazione policy: tengo use case @odm-platform-pp-blueprint-server/src/main/java/org/opendatamesh/platform/pp/blueprint/blueprintversion/services/usecases/instantiate/InstantiateBlueprintVersion.java e fare un'altra implementazione identica, cambiare solo InstantiateBlueprintVersionGitOutboundPort e fare mock (e.g. instanzio InstantiateBlueprintVersionLocalinstantiationGitOutboundPort), mock di tutti push @odm-platform-pp-blueprint-server/src/main/java/org/opendatamesh/platform/pp/blueprint/blueprintversion/services/usecases/instantiate/InstantiateBlueprintVersionGitOutboundPort.java:53-55. Fare mock metodi pushTag e pushBranch

Definire due packages/servizi distinti:

1. Servizio per validazione con calcolo hash. interfaccia useCase per protected resources, chiamato dal servizio di validator.
2. Policy sottoscrizione e validazione, creare policy custom da eventi ValidatorPolicySubscriber, su evento di pubblicazione DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED, policy bloccante equivalente di odm-platform-adapter-observer-blindata/src/main/java/org/opendatamesh/platform/up/metaservice/blindata/validator

### Repo Slice: odm-platform-pp-blueprint-server

**Repository**: `odm-platform-pp-blueprint-server`  
**Architectural role**: API (product-plane Blueprint Server)  
**In scope**:
- Isolated `old/v1` package (Registry `old` pattern): subscribe to Policy V1 `DATA_PRODUCT_VERSION_CREATION`, fetch Registry, reconstruct the V2 version resource, expose/adapt the evaluate endpoint so it can call the existing integrity path
- Existing integrity use case (already implemented; not redesigned): clone **product** repo at tag, clone **blueprint** source, re-instantiate locally, hash protected files/folders, compare
- Reuse existing instantiate; isolate/no-op Git push
- Monorepo, no composition only
- Service Git credentials, validator **active**, and policy **blocking** in Blueprint configuration
- Adapter-only Registry client configuration

**Out of scope**:
- Polyrepo and composition validation
- Changing Registry, Notification, or Policy Service
- Writing integrity hashes into the source blueprint manifest
- UI
- Lasting subscription to `DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED` (Policy V2 migration after deleting `old/v1`)

**Upstream dependencies**:
- Registry `old/v1` bridge calling Policy `validateInput` with `DATA_PRODUCT_VERSION_CREATION` (descriptor-only `afterState`)
- Policy Service engine registration and evaluate protocol (Observer shape), already able to dispatch **`DATA_PRODUCT_VERSION_CREATION`**
- Registry V2 product/version GET and search (tag, nested product repo, descriptor)
- Blueprint version + manifest + blueprint repo pointers already stored in this service
- Git hosts reachable with configured service credentials

**Downstream consumers**:
- Policy Service (evaluation result, including blocking failure message)
- Indirectly Registry (approve/reject via the V1 bridge)

**Boundary contracts** (this repo's perspective):
- **Policy evaluate API**: inbound from Policy Service — existing protocol; V1 object is `{ currentState, afterState }`
- **Policy engine/policy registration**: outbound at startup — event **`DATA_PRODUCT_VERSION_CREATION`**
- **Registry fetch**: outbound from `old/v1` only
- **Git clone/render**: existing capability, no-push validation path

**Resolved system decisions**:
- Combined A+B check: re-instantiate then hash
- Policy adapter, not a Notification-only observer; blocking flag is Blueprint config
- Current evaluation event = Policy V1 `DATA_PRODUCT_VERSION_CREATION`
- `old/v1` fetches Registry and reconstructs the V2 nested version resource; integrity does not call Registry
- No Git secrets on events; Git credentials, validator active, and blocking flag are Blueprint configuration
- Two clones when applicable: product repo at publication tag (from reconstructed Registry resource) and blueprint repo (from this service’s stored pointers)
- Two layers: removable `old/v1` vs lasting integrity use case
- Pass (not applicable) when there is no lineage or no protected resources, once the adapter is on
- Monorepo / no composition only
- Notification and Policy Service unchanged
- Core must not depend on `old/v1`

**Open questions** (resolved in this analysis where possible; remainder in Risk & Gap):
- Canonical hash algorithm — already defined for the integrity use case
- Skip vs fail vs error — matrix under Key Business Rules
- Registry lookup sequence from V1 `afterState` — decided below (FQN + version → V2 search/GET)

**Scoped business requirement**:

Finish the publication gate inside Blueprint Server for **today’s Policy V1**. This service already has the integrity use case. What is missing is a removable adapter that:

- Registers as a Policy engine on **`DATA_PRODUCT_VERSION_CREATION`** when the validator is active.
- On evaluate: reconstructs the V2 data product version resource (descriptor with blueprint lineage, Git **tag**, nested product **repository**) by fetching Registry, because the V1 payload does not contain clone metadata.
- Then runs the existing integrity process (not-applicable short-circuit or clone both repos, re-instantiate without push, hash, compare, path-level failure messages).

Do not change Notification or Policy. Do not implement polyrepo/composition. Deleting `old/v1` must be sufficient for the Policy V2 migration.

## Domain Concept Identification

### Existing Concepts (from codebase)

- **Blueprint / Blueprint version**: Platform record of a template repository and a released snapshot. Authoritative for `protectedResources` and instantiation strategy. Blueprint clone coordinates live here, not on the publication event.
- **Blueprint manifest (`protectedResources`)**: Optional repository-relative paths or globs marked immutable after generation. Source manifests omit integrity values. This slice does not persist hashes.
- **Instantiation scenario**: Only **monorepo, no composition** is implemented. Other strategies remain not-applicable for validation.
- **Instantiate (production)**: Interactive use case. Clones blueprint source and product integration branch, renders Velocity, enriches lineage, commits, tags a checkpoint, merges, **pushes**. Git auth from caller headers.
- **Protected-resources integrity evaluation** (already implemented): Combined File Immutability + Parameter Sanity. Clones the **published product tree** at the publication tag; runs `InstantiateBlueprintVersion` with a local/no-push Git port for the **expected tree**; SHA-256 of each protected path; path-level mismatch messages. Input command is tag + product repo locator + blueprint identity + lineage parameters — **not** a Policy DTO.
- **V2-shaped validator service** (already implemented): Maps a Policy evaluate request into that command by reading a **nested version resource** (tag, `dataProduct.dataProductRepo`, descriptor `blueprint` lineage). Today it is wired directly to the evaluate controller and therefore cannot work on a V1 `{ currentState, afterState }` payload.
- **Policy subscriber** (already implemented, wrong event for this slice): Startup create-if-absent of engine + policy, gated by validator `active` and Policy Service address. Currently binds to `DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED`, which Policy V1 never dispatches. This slice changes the registered event to `DATA_PRODUCT_VERSION_CREATION`. Nothing has been released; create-if-absent is enough (no Policy-row migration).
- **Observer Blindata validator (external pattern)**: Same evaluate API family; registers on `DATA_PRODUCT_VERSION_CREATION` / `DATA_PRODUCT_CREATION`. Blueprint copies the shape; for this slice it also copies the **CREATION** event name.
- **Registry `old/v1` Policy bridge (external, consumed)**: Recaptures Notification `DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED`, calls Policy `validateInput` with `DATA_PRODUCT_VERSION_CREATION`. `afterState` is the descriptor wrapped as `{ dataProductVersion: <content> }`. `dataProductId` is the FQN-derived legacy id; `dataProductVersion` is the version number. Policy **does not forward those two fields** to the engine: `objectToEvaluate` is only `{ currentState, afterState }`.
- **Registry V2 APIs (external, consumed by `old/v1`)**: Search products by FQN (returns uuid + nested product repo). Search versions by data-product uuid + version number (returns uuid + tag; version number is unique per product). GET version by uuid (full version resource, including nested product and descriptor content). Registry V1 product GET accepts FQN-derived ids but returns a V1 DTO without repo/tag — **not** sufficient alone.
- **Descriptor lineage**: `content.blueprint` on the stored version (blueprint name, version, resolved parameters). Written at instantiate; present on the Registry version and inside V1 `afterState` content (when parser passes content through).
- **Git provider factory / Blueprint validator configuration**: Service Git credentials, `active`, `blocking`, evaluation timeout. No Registry address today.
- **Use-case / factory / outbound-port pattern**: Integrity stays a `UseCase`. `old/v1` is an adapter, not a second integrity implementation.
- **Isolation pattern to copy**: Registry `old` README — the compatibility package may depend on core; **core must not depend on it**; intended to be deleted.

### New Concepts Required

- **Blueprint `old/v1` Policy reconstruction adapter**: Temporary, isolated package. Responsibilities only: (1) register the policy on `DATA_PRODUCT_VERSION_CREATION`; (2) accept the V1 evaluate payload; (3) fetch Registry; (4) rebuild the V2 nested version resource; (5) call the existing V2-shaped validator / integrity use case; (6) return the Policy result. No hashing, no Git clone, no instantiate.
- **Reconstructed V2 evaluation object**: The object the lasting validator already understands: a data product version resource with `tag`, nested `dataProduct.dataProductRepo`, and `content` (descriptor + lineage). This is what Policy V2 would have forwarded. `old/v1`’s job is to make it look as if that had happened.
- **Adapter-only Registry client**: Outbound port used solely by `old/v1`, configured with a Registry base URL that will not be used after the package is removed.

### Key Business Rules

- Publication governance stays on Policy V1 + Registry’s V1 bridge. Blueprint only **evaluates** integrity. Blocking vs not is Blueprint config, honored by Policy aggregation.
- **This slice’s subscription is `DATA_PRODUCT_VERSION_CREATION`.** Blueprint does not subscribe to Notification and does not register on `DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED` until Policy V2 exists.
- **`old/v1` may call Registry. The integrity use case must not.** Product clone coordinates come from the reconstructed version resource. Blueprint clone coordinates come from this service’s store.
- **Two clones** when the check is applicable: product repo at publication tag; blueprint source at recorded version tag.
- No Git secrets on events. Policy-path clones use **service credentials from Blueprint configuration**.
- File Immutability and Parameter Sanity remain **one** policy. **`InstantiateBlueprintVersion`** with the local Git port still produces the expected tree.
- Hashes are computed **at evaluation time on both trees** (SHA-256 canonical rule already implemented). Failure messages still list each affected path and why (missing on published tree, missing on re-instantiated tree, content differs).
- First slice: **monorepo, no composition** only.
- Protected paths must be **deterministic**.
- Skip vs fail:
  - Validator **off** → engine/policy not registered.
  - Validator **on**, no blueprint lineage → **pass (not applicable)**.
  - Validator **on**, lineage present, no / empty `protectedResources` → **pass (not applicable)**.
  - Validator **on**, lineage present, strategy is not monorepo-without-composition → **pass (not applicable)** with an explicit unsupported message.
  - Validator **on**, check is applicable, but reconstruction cannot obtain product repo or publication tag → **fail closed**.
  - Applicable, but recorded blueprint version is unknown to this service, or blueprint repo pointers are missing → **fail**.
  - Applicable, clone / auth / timeout / render / Registry fetch error → **fail closed**.
  - Malformed V1 payload that cannot even be read → transport/error, not a silent pass.
  - Applicable, any protected path missing on either tree, or hashes differ → **fail** with path-level reasons.
- `.odm/blueprint/` and the descriptor **may** appear in `protectedResources`; expected tree must apply the same lineage enrichment and relocation.
- Git mutations during validation are forbidden.
- **Isolation**: `old/v1` may depend on core validator/integrity. Core must not import `old/v1`. Deleting the package is the V2 migration, plus pointing the lasting subscriber at `DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED`.

## Strategic Approach

### Solution Direction

Keep the implemented integrity use case and V2-shaped validator service. Insert a **removable `old/v1` adapter** in front of them, modeled on Registry `old`.

Policy V1 calls the evaluate API with `{ currentState, afterState }`. The adapter extracts identity from the descriptor in `afterState`, fetches Registry V2 until it has a full version resource (tag + nested product repo + content), and invokes the existing validator as if Policy V2 had sent that object.

Subscription moves to `DATA_PRODUCT_VERSION_CREATION`. Git credentials / active / blocking stay in lasting config. Registry address is adapter-only config.

Do not change Notification or Policy. Do not reimplement hashing or instantiate in `old/v1`.

### Key Design Decisions

- **Event name for this slice**: Policy V1 only selects engines by `DATA_PRODUCT_VERSION_CREATION` on the publication path (Registry bridge). → Register that name. The V2 Notification name is not dispatched to engines today.

- **Where reconstruction lives**: Entire V1-specific behaviour (CREATION subscription, Registry client, payload mapping, and the evaluate controller **if** it must understand V1 JSON) lives in `old/v1`. The lasting validator continues to accept a V2 nested version resource. If the HTTP path must stay stable (`/api/v1/up/validator/evaluate-policy`), the controller in `old/v1` is the V1 implementation of that path; the V2 migration adds a thin controller in core and deletes `old/v1`. Core still must not import `old/v1`.

- **Registry lookup sequence (resolved)**: Policy does not forward `dataProductId`. Do not require the old FQN-derived identifier or IdentifierStrategy in Blueprint. From `afterState.dataProductVersion` (descriptor content): read FQN and version number from descriptor `info`. Then:
  1. Search Registry V2 products by FQN → product uuid + nested product repo.
  2. Search Registry V2 versions by product uuid + version number → version uuid + tag (version number is unique per product).
  3. GET Registry V2 version by uuid → full version resource used as the reconstructed object (authoritative tag, nested product including repo, stored descriptor/lineage).
  If FQN/version cannot be read, or any step returns nothing / multiple products, **fail closed**. Do not use Registry V1 version GET (descriptor string only, no tag/repo). Prefer the GET in step 3 as the object passed into the existing validator so lineage is the stored descriptor, not a possibly 1.x-rewritten `afterState`.

- **What `old/v1` must not do**: Hash, clone Git, instantiate, push, or interpret `protectedResources`. That remains the integrity use case.

- **Policy registration (resolved)**: Create-if-absent of engine + policy with **one** evaluation event `DATA_PRODUCT_VERSION_CREATION`. Do not overwrite `blockingFlag` on restart (same rule as today). Nothing has been released, so there is no existing Policy-row migration to design.

- **Timeouts**: The existing evaluation timeout wraps the whole Policy HTTP call, including Registry fetch + clones + render. Fail closed on timeout. No separate fetch timeout unless operations later prove it necessary.

- **Registry client configuration**: New `odm.product-plane.registry-service` active/address (same style as Policy client). Used only by `old/v1`. When the package is deleted, this config is unused — that is intended.

- **Canonical digest / skip-fail / local instantiate / Git credentials**: Unchanged from the implemented integrity slice (SHA-256 file/folder/glob rule; skip/fail matrix above; `InstantiateBlueprintVersion` + local Git port; service credentials from config).

- **V2 removal path**: Delete `old/v1`. Lasting subscriber registers `DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED`. Lasting evaluate controller passes Policy’s object through to the existing validator (nested version resource). No Registry client.

### Alternatives Considered

- **Subscribe Blueprint to Notification `DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED`**: Requires Policy to consume V2 events to close the approve/reject loop, or races the V1 bridge. Rejected; Policy and Notification stay untouched.
- **Extend Policy to dispatch the V2 event name**: Explicitly out of scope for this discussion. Rejected for this slice.
- **Leave the V1 bridge on and also run a V2 Notification path**: Race: bridge can approve first. Rejected.
- **Project clone metadata onto Policy V1 `afterState`**: Changes V1 payloads; risks Observer/OPA. Rejected.
- **Call Registry from the integrity use case**: Would leak the band-aid into lasting domain logic. Rejected; fetch stays in `old/v1`.
- **Use Registry V1 version GET only**: No tag, no product repo. Rejected as the sole lookup.
- **Reimplement hashing/instantiate inside `old/v1`**: Defeats removal. Rejected.
- **Two Policy policies for A and B / polyrepo in this slice / hash Velocity sources**: Still rejected.
- **Fail unsupported strategies**: Would block polyrepo/composition publications this slice cannot check. Still not-applicable pass with an explicit message.

## Risk & Gap Analysis

### Requirement Ambiguities

- **Descriptor `info` field names in `afterState`**: Lookup uses FQN + version from descriptor `info` only to find the Registry row; the reconstructed object is the V2 GET body. If `afterState` was rewritten by the old 1.x parser, FQN/version should still be present (`fullyQualifiedName` / `version`). If they are missing, fail closed — do not invent a second identifier strategy unless a later test proves it necessary (Registry V1 product GET can resolve FQN-derived ids, but that is a fallback, not the primary path).
- **Nested repo on version GET**: Assumed MapStruct maps the eager product association including `dataProductRepo`. If GET omits the repo, reconstruction must GET the product by uuid as well and nest it. Verify in implementation tests; if omitted, fail closed rather than clone nothing.
- **Exact Registry client DTO field names**: `providerType` vs `dataProductRepoProviderType` already handled in the integrity mapper. Reconstruction should pass the GET JSON through to that mapper rather than invent a third DTO family in core.
- **Evaluate URL**: Remains the Observer-compatible validator path. Exact path stays as implemented; only the package that owns the controller may move into `old/v1`.
- **Glob dialect**: Unchanged; same matcher on both trees.

### Edge Cases

- **Product never from a blueprint**: No lineage on reconstructed content → not applicable pass.
- **Blueprint lineage present but this Blueprint Server does not store that version**: fail.
- **Registry fetch 404 / timeout / multiple FQN matches**: fail closed (applicable check cannot run).
- **Version is PENDING**: Expected; publication requested persists the version before the bridge calls Policy. GET must still see it.
- **Observer also on CREATION**: Policy fans out per matching policy. Blueprint must not alter V1 `afterState`. Harmless peer.
- **Validator on before Registry address is configured**: fail closed / do not register — same idea as Policy address missing today.
- **Product and blueprint on different Git hosts**: two credential sets from Blueprint config. Missing creds → fail closed.
- **Non-deterministic protected files / path traversal / symlinks**: same integrity rules as already implemented.
- **Policy inactive on Registry / V1 bridge off (`policy-service.version` not 1)**: this adapter never runs. Operational dependency.

### Technical Risks

- **False rejects from render drift**: Still mitigated by reusing `InstantiateBlueprintVersion` with the local Git port.
- **Duration vs Policy HTTP timeout**: Extra Registry RTT on a path that already clones twice. Mitigation: monorepo-only; one evaluation timeout; fail closed.
- **Working-copy leaks**: Unchanged; temp clones cleaned on all paths.
- **Core accidentally importing `old/v1`**: Would make deletion a refactor. Mitigation: package rule matching Registry `old` README; tests/architecture check if the repo has one, otherwise review.
- **GET omits nested repo**: Reconstruction would fail closed until the client nests product GET. Verify against a real local Registry before calling the slice done.

### Acceptance Criteria Coverage

System ACs from the vision, assessed for **this repository**.

| AC# | Description | Addressable? | Gaps/Notes |
|-----|-------------|--------------|------------|
| 1 | Protected-resource constraints are evaluated during data product **version publication** | Partial | Blueprint evaluates when Policy dispatches `DATA_PRODUCT_VERSION_CREATION` via the Registry V1 bridge. Trigger and aggregation stay Registry/Policy. |
| 2 | Blueprint Server acts as a **policy adapter** comparable to Observer’s validator | Yes | Engine + policy + evaluate API; this slice also uses Observer’s event name. |
| 3 | Adapter clones the **data product repository at the version tag** | Yes | Tag + repo come from Registry fetch in `old/v1`, then the existing integrity clone. Missing after reconstruction → fail. |
| 4 | Adapter reads **protected resources**, computes hashes, verifies they still match | Yes | Already implemented in the integrity use case. |
| 5 | **File immutability** | Yes | Already implemented; requires both clones. |
| 6 | **Parameter sanity** | Yes | Already implemented via `InstantiateBlueprintVersion` + local Git port. |
| 7 | Integrity does **not** call Registry; `old/v1` **does** (temporary) | Yes | Reconstruction only. Removed with the package. |
| 8 | Custom **blocking** policy on the publication evaluation path | Yes | Register on `DATA_PRODUCT_VERSION_CREATION`; blocking from config. Policy already dispatches that name. |
| 9 | Subscription / reconstruction separate from hash/integrity | Yes | `old/v1` vs existing integrity package. |
| 10 | Reuse instantiate; **do not push** | Yes | Already implemented. |
| 11 | Enable adapter when validation is **active** | Yes | Lasting config: active + blocking. Adapter-only: Registry address. |
| 12 | First slice: **monorepo, no composition** only | Yes | Other strategies: not-applicable with explicit message. |
| 13 | Do **not** change Notification or Policy | Yes | Consumed contracts only. |
| 14 | `old/v1` is removable without rewriting integrity | Yes | Isolation rule; V2 migration is delete + rebind event name. |
