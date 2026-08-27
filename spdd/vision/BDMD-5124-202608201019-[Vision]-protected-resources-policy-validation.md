# SPDD Vision: Protected Resources policy validation at data product version publication

## Original Business Requirement

# [Blueprint 2.0] Support for Protected Resources

## Original business requirement (high level description)

Build a validation process.

There needs to be a point where the constraint is validated during publication of a data product version.
Possible validation:
Blueprint service also becomes a policy adapter (as Observer already does). Clone the repository (given the tag), read the protected resources and generate hashes, and verify they match (for example, that the protected resources have not been modified).

Notes from earlier development:

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

Event `DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED` @odm-platform-pp-registry-server/src/main/java/org/opendatamesh/platform/pp/registry/rest/v2/resources/dataproductversion/events/emitted/EmittedEventDataProductVersionPublicationRequestedRes.java — Policy Service captures the event, dispatches it to policy engines for a validation request, engines run the validation.
Subscribe Blueprint service to Policy Service as an adapter when protected resources is active. It receives the event, clones the repo locally, and checks whether protected resources were modified by computing and comparing hashes.
Observer also acts as a validator; use that as a pattern for the validator package: @odm-platform-adapter-observer-blindata/src/main/java/org/opendatamesh/platform/up/metaservice/blindata/validator → add an equivalent package on Blueprint for event `DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED`.
The event may need to be extended @odm-platform-pp-registry-server/src/main/java/org/opendatamesh/platform/pp/registry/rest/v2/resources/dataproductversion/events/emitted/EmittedEventDataProductVersionPublicationRequestedRes.java @odm-platform-pp-registry-server/src/main/java/org/opendatamesh/platform/pp/registry/dataproductversion/services/usecases/publish/DataProductVersionPublisherNotificationOutboundPortImpl.java to include the repo to clone so Blueprint does not have to contact Registry.

Protected resources can be simple files or folders; compare hashes directly. If they are Velocity templates we may want to verify the templates were not modified: instantiate the blueprint with the data product parameters and compute hashes.

For policy validation implementation: keep use case @odm-platform-pp-blueprint-server/src/main/java/org/opendatamesh/platform/pp/blueprint/blueprintversion/services/usecases/instantiate/InstantiateBlueprintVersion.java and make another identical implementation, changing only `InstantiateBlueprintVersionGitOutboundPort` with a mock (e.g. `InstantiateBlueprintVersionLocalinstantiationGitOutboundPort`), mock all pushes @odm-platform-pp-blueprint-server/src/main/java/org/opendatamesh/platform/pp/blueprint/blueprintversion/services/usecases/instantiate/InstantiateBlueprintVersionGitOutboundPort.java:53-55. Mock `pushTag` and `pushBranch`.

Define two distinct packages/services:

1. Service for validation with hash computation. UseCase interface for protected resources, called by the validator service.
2. Policy subscription and validation: create a custom policy from ValidatorPolicySubscriber events, on publication event `DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED`, blocking policy equivalent to odm-platform-adapter-observer-blindata/src/main/java/org/opendatamesh/platform/up/metaservice/blindata/validator

## Involved Projects

| Project | Role | In-scope summary | Analysis needed? |
|---------|------|------------------|------------------|
| `odm-platform-pp-blueprint-server` | Product-plane API: blueprints, instantiate, Git | Integrity use case (already implemented). Temporary isolated `old/v1` adapter: subscribe to Policy V1 `DATA_PRODUCT_VERSION_CREATION`, fetch Registry to reconstruct the V2 version resource (tag + product repo), then call the integrity use case. | **Yes** (this remaining adapter slice) |
| `odm-platform-pp-registry-server` | Product-plane API: data products, publish lifecycle, Policy V1 compatibility bridge | V2 `DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED` already nests tag + product repo on the version resource. V1 bridge unchanged: it recaptures that Notification event and calls Policy `validateInput` with `DATA_PRODUCT_VERSION_CREATION` (descriptor-only). Blueprint will **read** existing Registry GET/search APIs; no Registry code change for this band-aid. | **No** (contract already in place; GET APIs exist) |
| `odm-platform-pp-notification-server` | Event bus | Continues delivering `DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED` to Registry’s V1 bridge (and other observers). Blueprint does **not** subscribe to Notification. | **Out of scope** — do not change |
| Policy Service (product-plane, not a workspace root) | Governance gate: registers engines, dispatches evaluation, aggregates blocking results | Unchanged. Blueprint registers as an engine on Policy V1 **`DATA_PRODUCT_VERSION_CREATION`** (same enum Observer uses). Policy already dispatches that name to engines. | **Out of scope** — do not change |
| `odm-platform-adapter-observer-blindata` | Existing observer + policy adapter | Pattern only (engine + blocking policy + evaluate API). Peer engine on the same `DATA_PRODUCT_VERSION_CREATION` event. | **Read-only** |
| Git hosts (GitHub / GitLab / Bitbucket / Azure) | External VCS | Clone product repo at tag and blueprint source for local re-instantiate. | External — not a repo slice |
| `blindata-ui` | Frontend | Lineage/parameters already shown; no publication-gate UI in this requirement. | **Out of scope** |

## System Architecture & Interactions

### System Context

A data-product team publishes a version. Registry stores it as `PENDING` and emits V2 `DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED` (version resource already nested with Git tag and product repo). Policy Service is still V1: it cannot consume that V2 Notification event, and it must not be modified for this feature.

Registry’s existing `old/v1` Policy bridge is the band-aid on the Registry side: it recaptures the V2 Notification event, translates it to Policy V1 `DATA_PRODUCT_VERSION_CREATION`, and calls Policy `validateInput` synchronously with a descriptor-only payload. Policy then dispatches to every engine registered on `DATA_PRODUCT_VERSION_CREATION` (Observer, OPA, and now Blueprint).

Blueprint becomes one of those engines. The V1 evaluation object does **not** carry clone metadata (tag + product repo). A temporary isolated adapter in Blueprint (`old/v1`, same isolation idea as Registry’s `old` package) reconstructs the V2 publication object by fetching the data product version from Registry, then calls the already-implemented integrity use case.

If the blocking policy fails, Policy reports failure; Registry’s V1 bridge emits publication **rejected**. If it passes (or the check is not applicable), other engines still run; the version can be approved.

Actors: data-product owner (publish), platform governance (Policy V1), Registry V1 bridge (sync Policy call + approve/reject), Blueprint Server (integrity engine + temporary reconstruction adapter), Git hosts. End users of Blindata UI are not in this loop.

When Policy V2 can dispatch `DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED` with the nested version resource, the Blueprint `old/v1` package is deleted (reconstruction, CREATION subscription, Registry client, Policy DTOs/clients, and `ProtectedResourcesPolicyValidatorService`). The integrity use case stays. A thin lasting evaluate controller is added in its place. No Notification or Policy changes are required for the current slice.

### Project Boundaries

| Boundary | From | To | Contract | Owner | Existing/New |
|----------|------|-----|----------|-------|--------------|
| Publish requested | Registry | Notification | Lifecycle event `DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED` (nested tag + product repo already on the version resource) | Registry | Existing |
| Event delivery | Notification | Registry V1 Policy bridge (and other observers) | Observer dispatch of `DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED` | Notification | Existing — **Blueprint is not a subscriber** |
| Evaluate publication (Policy V1) | Registry `old/v1` bridge | Policy Service | `validateInput` with event **`DATA_PRODUCT_VERSION_CREATION`**, descriptor-only `afterState` | Registry `old` / Policy | Existing — **unchanged** |
| Engine evaluation | Policy Service | Blueprint `old/v1` adapter (and Observer / OPA) | Policy-adapter evaluate API; event name **`DATA_PRODUCT_VERSION_CREATION`**; payload is `{ currentState, afterState }` (no tag, no product repo) | Blueprint adapter URL; Policy engine registry | **New Blueprint engine + policy on the existing V1 event** |
| Reconstruct V2 object | Blueprint `old/v1` | Registry V2 API | Fetch data product + version so the adapter can rebuild the nested version resource (tag + product repo + descriptor) | Blueprint (temporary client); Registry (existing GET/search) | **New caller of existing APIs** |
| Integrity check | Blueprint `old/v1` | Blueprint integrity use case | Reconstructed V2 version resource (same shape Policy V2 would have forwarded) | Blueprint | Existing use case; **new caller** |
| Clone product tree | Blueprint Server | Git host | Git clone at tag (service credentials) | Blueprint config; Git host | Existing Git integration, async policy path |
| Re-instantiate expected tree | Blueprint Server | Git host (blueprint source) + local working copy | Existing instantiate, **no push** | Blueprint | Existing |
| Decision | Registry V1 bridge (from Policy aggregation) | Notification | `DATA_PRODUCT_VERSION_PUBLICATION_APPROVED` / `REJECTED` | Registry catalog | Existing |
| Apply decision | Notification | Registry Observer | Observer dispatch | Notification | Existing |

### Cross-Cutting Concepts

- **Data product version publication**: authoritative in Registry (lifecycle state). Policy V1 decides via the Registry `old/v1` bridge. Blueprint only evaluates integrity.
- **Two event catalogs must not be mixed**:
  - **Notification / Registry V2**: publication is `DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED`. Registry emits it; Notification delivers it; Registry’s V1 bridge recaptures it. Blueprint does **not** bind to this name today.
  - **Policy Service V1 evaluation enum**: `DATA_PRODUCT_VERSION_CREATION` is what Registry’s bridge sends to Policy and what Observer and Blueprint register on **for this slice**.
- **Temporary reconstruction**: Policy V1’s evaluate payload is descriptor-only. Product Git repository + tag remain authoritative in Registry. The Blueprint `old/v1` adapter fetches them and reconstructs the V2 version resource. The integrity use case still does not call Registry.
- **Protected resources**: authoritative in the blueprint manifest; evaluated against the product repo at tag vs a local re-instantiate.
- **Git credentials**: never on events. Authoritative as Blueprint Server configuration (service credentials for the policy path).
- **Blueprint validator configuration**: `active` + `blocking` (Observer-like) stay. Registry address used by the fetch is **V1-adapter-only** config and becomes unused when `old/v1` is removed.
- **Isolation / removal**: Blueprint `old/v1` may depend on core (integrity use case, `blueprint.validator` Git/config). Core must not depend on `old/v1`. Deleting the package is the Policy V2 migration.

### Interaction Flow

```mermaid
sequenceDiagram
    participant User
    participant Registry
    participant Notification
    participant Bridge as Registry old/v1 bridge
    participant Policy as Policy Service V1
    participant Adapter as Blueprint old/v1
    participant Integrity as Integrity use case
    participant Git as Git host

    User->>Registry: Publish data product version
    Registry->>Registry: Persist version PENDING
    Registry->>Notification: DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED
    Notification->>Bridge: Dispatch PUBLICATION_REQUESTED
    Bridge->>Policy: validateInput(DATA_PRODUCT_VERSION_CREATION, descriptor-only)
    Policy->>Adapter: Evaluate policy bound to CREATION
    Adapter->>Registry: Fetch product version (tag + product repo + descriptor)
    Adapter->>Integrity: Reconstructed V2 version resource
    alt Not from a blueprint, or no protected resources
        Integrity-->>Adapter: Pass (not applicable)
    else Monorepo, protected resources present
        Integrity->>Git: Clone product repo at tag
        Integrity->>Git: Clone blueprint source; re-instantiate locally (no push)
        Integrity->>Integrity: Hash protected paths on both trees and compare
        Integrity-->>Adapter: Pass or fail (blocking)
    end
    Adapter-->>Policy: Evaluation result
    Policy-->>Bridge: Aggregated validation result
    alt Blocking failure
        Bridge->>Notification: PUBLICATION_REJECTED
    else All blocking policies pass
        Bridge->>Notification: PUBLICATION_APPROVED
        Notification->>Registry: Apply APPROVED + PUBLISHED
    end
```

End-to-end narrative:

1. Publish → Registry emits V2 `DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED` (nested tag + product repo already present).
2. Notification delivers that event to Registry’s `old/v1` Policy bridge (not to Blueprint, not to Policy).
3. The bridge calls Policy V1 `validateInput` with `DATA_PRODUCT_VERSION_CREATION` and a descriptor-only payload.
4. Policy dispatches to engines registered on `DATA_PRODUCT_VERSION_CREATION`, including Blueprint.
5. Blueprint `old/v1` fetches the version from Registry, reconstructs the V2 version resource, and calls the integrity use case.
6. Blocking fail → Registry bridge rejects publication; otherwise the rest of the gate proceeds as today.

## Strategic System Approach

### Solution Direction

Keep publication governance on the **existing Policy V1 gate** and the **existing Registry `old/v1` bridge**. Do not invent a second approval bus. Do **not** change Notification. Do **not** extend Policy to consume `DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED`.

Blueprint Server becomes a **policy adapter** in the same way Observer already is: register a policy engine + a policy (blocking flag from config), expose an evaluate endpoint. For this slice the registered evaluation event is Policy V1 **`DATA_PRODUCT_VERSION_CREATION`**.

Because that V1 payload cannot clone a Git tag, introduce an **isolated Blueprint `old/v1` package** (Registry `old` pattern): reconstruct the V2 publication object by fetching Registry, then call the **already implemented** integrity use case. That use case stays V2-shaped: clone product repo at tag, re-instantiate the blueprint locally without pushing, hash protected paths, compare.

Treat File Immutability (A) and Parameter Sanity (B) as **one check**. First delivery remains **monorepo, no composition**.

The V2 target is unchanged: Policy V2 will forward `DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED` with the nested version resource; Blueprint will subscribe to that name and call the integrity use case directly. The only deletion required is `old/v1` (reconstruction + CREATION subscription + Registry client + Policy adapter types). Then add a thin lasting evaluate controller.

### Key System-Level Design Decisions

- **Do not use Notification as Blueprint’s trigger**: Subscribing Blueprint (or Policy) to `DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED` would require Policy to handle V2 events. Policy and Notification stay untouched. → Registry’s existing V1 bridge remains the Policy entry; Blueprint registers on `DATA_PRODUCT_VERSION_CREATION`.
- **Isolated `old/v1` adapter in Blueprint**: Same band-aid idea as Registry `old`. Reconstruction, CREATION subscription, Registry fetch, Policy clients/DTOs, and `ProtectedResourcesPolicyValidatorService` live only there. Core integrity and Git/validator config stay. Core must not import `old/v1`. The package is deleted when Policy V2 exists.
- **Fetch Registry from Blueprint (temporary)**: The V1 evaluate object has no tag and no product repo. → `old/v1` fetches Registry V2 to reconstruct the nested version resource. The integrity use case still does not call Registry. This hop goes away with Policy V2.
- **Reconstruct, then reuse the existing use case**: `old/v1` only simulates “Policy V2 called us with the V2 object.” It does not reimplement hashing or instantiate.
- **Policy adapter vs Notification observer**: A Notification-only observer cannot block publish. → **Policy adapter**. The V1 event name is the temporary subscription; the V2 name remains the target.
- **One combined check vs two policies**: A and B share clone + hash. → **One** policy, one integrity use case.
- **V1 payloads stay untouched**: Do not project locator/tag onto Policy `validateInput`. Observer/OPA keep their descriptor-only `afterState`.
- **Secrets on the event**: No Git tokens on the bus. Credentials stay in Blueprint configuration.
- **Blueprint validator config** (Observer-like): Git credentials, validator **active**, policy **blocking**. Registry base URL is additional **adapter-only** config, unused after `old/v1` is removed.
- **Hashes at validation time**: Compute both sides at publication validation. Reuse instantiate with no-op push.
- **Rollout**: Register the engine only when the Blueprint validator is active. Policy inactive on Registry still auto-approves. Enable the Blueprint validator only when Policy V1 + Registry V1 bridge + Registry address are configured.

### Implementation Sequence

1. **`odm-platform-pp-registry-server`**: V2 publication contract (nested tag + product repo) is **already done**. V1 bridge stays. No further Registry work for this band-aid.
2. **Policy Service / Notification**: **No changes.**
3. **`odm-platform-pp-blueprint-server`**: Add isolated `old/v1` (CREATION subscription, Registry fetch, reconstruct V2 object, call existing integrity use case). Keep Git credentials / active / blocking config. Integrity use case already implemented.
4. **Later (Policy V2)**: Delete Blueprint `old/v1`. Subscribe the remaining adapter to `DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED`. No Registry fetch.

Rationale: avoid changing Policy and Notification; keep V2 integrity logic; isolate the compatibility hop so it can be thrown away.

### Alternatives Considered

- **Blueprint as Notification observer only**: Can clone, cannot block publication. Rejected.
- **Extend Policy to consume `DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED`**: Would make Policy V2-capable now; team agreed not to touch Policy. Rejected for this slice.
- **Leave Registry V1 bridge on and also subscribe Policy/Blueprint to the V2 Notification event**: Race: the bridge can approve before the V2 path returns. Rejected.
- **Project clone metadata onto Policy V1 `validateInput`**: Would change V1 payloads and risk Observer/OPA. Rejected.
- **Git credentials on the event**: Secrets on the bus. Rejected.
- **Hash at blueprint publish time only**: Fails for Velocity. Rejected.
- **Two separate Policy policies for A and B**: Premature. Rejected.
- **Polyrepo/composition in v1**: Out of first slice.
- **UI or Builder changes**: Not in the business requirement. Rejected.

## System Risk & Gap Analysis

### Requirement Ambiguities

- **How to identify the Registry version from the V1 evaluate payload (open)**: Policy forwards `{ currentState, afterState }` to engines, **not** `dataProductId` / `dataProductVersion`. Reconstruction must read identity from the descriptor in `afterState` (FQN + version) and resolve it via Registry search/GET. Confirm descriptor always has those fields on publication. Analysis must pick the exact lookup path.
- **Skip vs fail**: Unspecified originally for: no blueprint lineage; empty protected resources; missing tag/repo; polyrepo/composition; clone failure vs hash mismatch. Shared rule: adapter off → not registered; no lineage / empty protected resources / unsupported strategy → **pass (not applicable)**; applicable but cannot reconstruct or clone → **fail closed**; hash mismatch → **fail** with path-level reasons.
- **Feature flag**: Blueprint configuration: validator **active**; **blocking** flag. Registry address is required when the V1 adapter is used.
- **Folder/glob hashing**: Canonical SHA-256 rule is defined in the Blueprint analysis (unchanged).
- **Non-deterministic protected files**: Protected paths must be deterministic, or they must be excluded.
- **Git service identity**: Blueprint service configuration, not the last publisher’s token.

### Integration Risks

- **V1 payload drop**: Policy V1 never forwards tag/repo. Mitigation: Blueprint `old/v1` fetches Registry; fail closed if lookup fails.
- **Nested repo missing on GET**: Event emit may populate associations that a GET omits. Mitigation: analysis must use APIs that return tag + product repo (full version GET, or product GET + version search). Tests assert reconstruction.
- **Synchronous gate latency**: Registry bridge → Policy → Blueprint → Registry GET → two Git clones. May exceed Policy/HTTP timeouts. Mitigation: monorepo-only; evaluation timeout includes the fetch; fail closed on timeout.
- **Version skew of engines**: Observer and OPA stay on `DATA_PRODUCT_VERSION_CREATION` and descriptor-only `afterState`. Blueprint is an additional engine on the same event; it must not require V1 payload changes.
- **Git auth in async path**: Blueprint configuration holds service Git credentials; fail closed if clone is unauthorized.
- **False rejects**: Re-instantiate must match production instantiate. Mitigation: reuse instantiate; no-op push only.
- **Policy inactive / Blueprint validator inactive / Registry address missing**: Feature does nothing or fails closed. Document operational dependency on Policy active + Registry V1 bridge (`policy-service.version=1`) + Blueprint validator active + Registry address.

### Acceptance Criteria Coverage (System View)

The source document has no numbered ACs. The following are the testable intents extracted from it (plus the agreed first-slice constraint and the V1 band-aid).

| AC# | Description | Owner Project(s) | Cross-Boundary? | Gaps/Notes |
|-----|-------------|------------------|-----------------|------------|
| 1 | Protected-resource constraints are evaluated during data product **version publication** | Registry (trigger + V1 bridge), Policy Service (dispatch), Blueprint (evaluate) | Yes | Happy path is the full V1 gate, not a Blueprint-only API |
| 2 | Blueprint Server acts as a **policy adapter** comparable to Observer’s validator | Blueprint | No (pattern from Observer) | Subscription + evaluate API; config: active + blocking |
| 3 | Adapter clones the **data product repository at the version tag** | Blueprint (clone), Registry (source of tag + repo via fetch in `old/v1`) | Yes | V1 payload lacks clone metadata; reconstruction fetch is temporary |
| 4 | Adapter reads **protected resources**, computes hashes, verifies they still match | Blueprint | No | Canonical file/folder hash rules in Blueprint analysis |
| 5 | **File immutability**: product-tag hashes match blueprint-derived artifacts | Blueprint | No | Combined re-instantiate + compare |
| 6 | **Parameter sanity**: re-instantiate with recorded blueprint version + stored parameters; hashes match | Blueprint | No | Combined with AC5 |
| 7 | Integrity use case does **not** call Registry; `old/v1` **does** (temporary) | Blueprint `old/v1` (fetch), Registry (existing GET/search) | Yes | Removed when Policy V2 forwards the nested version resource |
| 8 | Custom **blocking** policy on the publication evaluation path | Blueprint (register on `DATA_PRODUCT_VERSION_CREATION`; blocking flag in config), Policy Service (honor blocking; already dispatches CREATION) | Yes | V2 name is the future subscription, not this slice |
| 9 | Validator **subscription / reconstruction** is separate from **hash/integrity** logic | Blueprint | No | `old/v1` vs integrity use case; deleting `old/v1` must not rewrite hashing |
| 10 | Reuse instantiate; **do not push** branch or tag during validation | Blueprint | No | Local Git port / no-op push |
| 11 | Enable adapter when protected-resources validation is **active** | Blueprint | No | Blueprint config: validator active + blocking; Registry address for `old/v1` |
| 12 | First slice: **monorepo, no composition** only | Blueprint | No | Other strategies: not-applicable, not silent success that implies a check |
| 13 | Do **not** change Notification or Policy Service for this slice | Notification, Policy | Yes | Registry V1 bridge already exists |
| 14 | `old/v1` is removable without rewriting integrity | Blueprint | No | Core must not depend on `old/v1` |

## Per-Project Requirement Slices

### Repo Slice: odm-platform-pp-blueprint-server

**Repository**: `odm-platform-pp-blueprint-server`  
**Architectural role**: API (product-plane Blueprint Server)  
**In scope**:
- Isolated `old/v1` package: Policy subscription to `DATA_PRODUCT_VERSION_CREATION`, Registry fetch, reconstruction of the V2 version resource, evaluate HTTP adapter that calls the existing integrity path
- Existing integrity use case (unchanged intent): clone product repo at tag, re-instantiate blueprint locally, hash protected files/folders, compare
- Reuse existing instantiate; isolate/no-op Git push
- Monorepo, no composition only
- Service Git credentials, validator **active**, and policy **blocking** in Blueprint configuration
- Adapter-only Registry client configuration (unused after `old/v1` is removed)

**Out of scope**:
- Polyrepo and composition validation
- Changing Registry, Notification, or Policy Service implementations
- Writing integrity hashes into the source blueprint manifest
- UI
- Binding the lasting adapter to `DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED` (that is the Policy V2 migration, after deleting `old/v1`)

**Upstream dependencies**:
- Registry V1 bridge calling Policy `validateInput` with `DATA_PRODUCT_VERSION_CREATION` (already exists)
- Policy Service engine registration and evaluate protocol (Observer shape), bound to **`DATA_PRODUCT_VERSION_CREATION`**
- Registry V2 GET/search that return tag + nested product repo + descriptor
- Blueprint version + manifest already stored in this service
- Git hosts reachable with configured service credentials

**Downstream consumers**:
- Policy Service (evaluation result, including blocking failure message)
- Indirectly Registry (approve/reject via the V1 bridge’s Policy aggregation)

**Boundary contracts** (this repo's perspective):
- **Policy evaluate API**: inbound from Policy Service — existing protocol; V1 payload today
- **Policy engine/policy registration**: outbound to Policy Service at startup — event name **`DATA_PRODUCT_VERSION_CREATION`**
- **Registry fetch**: outbound from `old/v1` only — existing Registry V2 APIs
- **Git clone/render**: outbound to Git hosts — existing capability, no-push validation path

**Resolved system decisions** (from Steps 3–5 that constrain this repo):
- Combined A+B check: re-instantiate then hash
- Blocking policy adapter, not a Notification-only observer
- Current subscription = Policy V1 `DATA_PRODUCT_VERSION_CREATION`
- Temporary Registry fetch in `old/v1` to reconstruct the V2 version resource
- Integrity use case consumes the reconstructed object and does not call Registry
- No Git secrets on the event; Git credentials, validator active, and blocking flag are Blueprint configuration
- Two internal layers: removable `old/v1` vs lasting integrity use case
- Pass (not applicable) when there is no lineage or no protected resources, once the adapter is on
- Monorepo / no composition only
- Do not change Notification or Policy

**Open questions** (repo-specific, to resolve in `/spdd-analysis`):
- Lookup keys and Registry API sequence from V1 `afterState` (FQN + version → tag + repo)
- Whether GET of a version returns nested `dataProduct.dataProductRepo`
- Timeouts: include Registry fetch in the existing evaluation timeout

**Scoped business requirement**:

Implement the remaining **V1 compatibility adapter** inside Blueprint Server so protected-resources validation actually runs on today’s Policy V1 publication gate.

When Policy asks this service to evaluate `DATA_PRODUCT_VERSION_CREATION`:

- If the adapter is inactive, this service does not register the policy.
- Reconstruct the V2 data product version resource (descriptor with blueprint lineage, Git **tag**, nested product **repository**) by fetching Registry. Do not expect those fields on the V1 evaluate payload.
- Then run the existing integrity process: not applicable if no lineage / no protected resources / unsupported strategy; otherwise clone the product repository at the tag, re-instantiate locally without pushing, hash protected paths, fail if any path differs.

Keep reconstruction isolated in `old/v1` so Policy V2 is a deletion plus re-binding the subscription to `DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED`. Do not implement polyrepo/composition in this slice. Do not change Notification or Policy.

---

### Repo Slice: odm-platform-pp-registry-server

**Repository**: `odm-platform-pp-registry-server`  
**Architectural role**: API (product-plane Registry)  
**In scope**: none for this band-aid (V2 nested tag + product repo already on `DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED`; V1 bridge already maps that event to Policy `DATA_PRODUCT_VERSION_CREATION`)  
**Out of scope**: hashing, re-instantiate, Blueprint adapter, new event types, Git credentials on events, changing `validateInput`, changing the V1 bridge  
**Upstream dependencies**: data product has a Git repository; version has a tag; descriptor may contain blueprint lineage  
**Downstream consumers**: Notification (V2 event); Policy V1 via the `old` bridge; Blueprint `old/v1` via existing GET/search  
**Boundary contracts**:
- **`DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED`**: existing V2 event; nested tag + product repo — **already the stored V2 contract**
- **Policy V1 `validateInput`**: existing, unchanged, descriptor-only
- **GET/search of product and version**: existing; Blueprint `old/v1` will call them  
**Resolved system decisions**:
- No Registry code change for the Blueprint V1 adapter
- V1 payloads stay descriptor-only
- Still emit when repo or tag is absent; skip/fail is Blueprint’s  
**Open questions**: none remaining at this vision level for Registry code  
**Scoped business requirement**:

No further Registry implementation for this feature. Keep emitting V2 `DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED` with nested tag + product repo. Keep the Policy V1 bridge as-is. Existing product/version GET and search APIs must remain usable so Blueprint can reconstruct clone metadata.

---

### Repo Slice: odm-platform-pp-notification-server

**Repository**: `odm-platform-pp-notification-server`  
**Architectural role**: other (event bus)  
**In scope**: none  
**Out of scope**: all implementation  
**Upstream dependencies**: Registry continues to emit the same event type  
**Downstream consumers**: Registry Policy V1 bridge (and any other observers). **Not** Blueprint. **Not** Policy Service.  
**Boundary contracts**:
- Notification dispatch of `DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED` — **existing**  
**Resolved system decisions**:
- No new event type
- Blueprint does not subscribe to Notification  
**Open questions**: none  
**Scoped business requirement**:

No Notification Server change. Continue delivering `DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED` to existing subscribers (including Registry’s V1 bridge).

---

### Repo Slice: odm-platform-adapter-observer-blindata

**Repository**: `odm-platform-adapter-observer-blindata`  
**Architectural role**: other (observer + existing policy adapter)  
**In scope**: none (read-only reference)  
**Out of scope**: all implementation for protected resources  
**Upstream dependencies**: Policy V1 `afterState` is unchanged  
**Downstream consumers**: none for this feature  
**Boundary contracts**:
- Existing validator evaluate API and policy subscription — **pattern to copy**, not to modify  
**Resolved system decisions**:
- Blueprint copies Observer validator **shape** (engine + blocking policy + evaluate API) **and**, for this slice, Observer’s **event name** `DATA_PRODUCT_VERSION_CREATION`
- Observer behaviour stays as-is  
**Open questions**:
- Confirm Blueprint as a second engine on the same event does not affect Observer (Policy already fans out per matching policy)  
**Scoped business requirement**:

Do not change Observer. Blueprint follows this adapter’s validator package, engine registration, blocking policy, and evaluate API. For the V1 band-aid, Blueprint also binds to `DATA_PRODUCT_VERSION_CREATION`. Observer must keep evaluating as today.

---

### Repo Slice: Policy Service (external product-plane)

**Repository**: not present as a workspace root (`odm-platform` is not in scope for this vision)  
**Architectural role**: other (governance gate)  
**In scope**: none (already dispatches `DATA_PRODUCT_VERSION_CREATION` to registered engines)  
**Out of scope**: hashing; Registry lifecycle; adding `DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED` to the V1 enum; Notification subscription  
**Upstream dependencies**: Registry V1 bridge `validateInput` requests  
**Downstream consumers**: Registry V1 bridge (aggregated result via `*_APPROVED` / `*_REJECTED`)  
**Boundary contracts**:
- Engine + policy registration — **existing**
- Dispatch evaluate to engines — **existing**; Blueprint uses **`DATA_PRODUCT_VERSION_CREATION`**
- Blocking flag — **existing**  
**Resolved system decisions**:
- Blueprint is one more engine; no second gate
- Do not modify Policy Service for this slice
- Policy V2 dispatch of `DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED` is a **later** migration, not this work  
**Open questions**: none remaining at this vision level  

**Scoped business requirement**:

Do not change Policy Service. Blueprint registers a policy on `DATA_PRODUCT_VERSION_CREATION` (blocking flag from Blueprint config). Policy already dispatches that event to engines and forwards `{ currentState, afterState }`. Clone metadata will not be on that payload; Blueprint reconstructs it from Registry.
