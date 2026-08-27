# Policy V1 compatibility (`old/v1`)

Temporary adapter so Blueprint can evaluate protected-resources integrity while Policy is still on V1.

Policy V1 cannot consume `DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED` and does not forward Git tag or product repository. This package reconstructs that publication object from Registry, then calls the lasting integrity check.

When Policy V2 dispatches `DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED` with nested tag + product repo, **delete this package**.

Related lasting docs: [Protected resources](../../../../../../../../../../docs/service/protected-resources.md) · [Configuration](../../../../../../../../../../docs/setup/configuration.md)

---

## Purpose

Keep publication-time protected-resource checks working without changing Policy or Notification.

Registry’s own `old/v1` bridge already translates V2 publication into Policy V1 `DATA_PRODUCT_VERSION_CREATION` (descriptor-only payload). Blueprint registers as a Policy engine on that same event. The evaluate payload has no clone metadata, so this adapter fetches Registry and rebuilds the V2 version resource before running the integrity use case.

## Workflow

1. Registry emits V2 `DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED`.
2. Registry’s Policy V1 bridge calls Policy `validateInput` with `DATA_PRODUCT_VERSION_CREATION` and `{ currentState, afterState }`.
3. Policy dispatches to engines registered on `DATA_PRODUCT_VERSION_CREATION`, including Blueprint.
4. Blueprint `POST /api/v1/up/validator/evaluate-policy` receives the V1 object.
5. This package reads FQN + version from `afterState`, fetches Registry (`/api/v2/pp/registry/products`, `/products-versions`), and rebuilds a nested version resource (descriptor + tag + product repo).
6. `ProtectedResourcesPolicyValidatorService` maps the reconstructed object to `EvaluateProtectedResourcesIntegrity` and returns a Policy result.

If reconstruction fails (missing identity, Registry down, no tag/repo), evaluation **fails closed**.

## Components

| Class | Role |
|:------|:-----|
| `ProtectedResourcesValidatorPolicySubscriber` | Registers engine + policy on `DATA_PRODUCT_VERSION_CREATION` |
| `ProtectedResourcesValidatorController` | Policy evaluate endpoint |
| `ReconstructPublicationRequestedService` | V1 payload → Registry fetch → Policy adapter |
| `ProtectedResourcesPolicyValidatorService` | Policy evaluate contract → integrity use case (and back) |
| `PolicyClient` / `PolicyEngineClient` / `PolicyClientsConfiguration` | Policy V1 HTTP registration |
| `PolicyEvaluationRequestRes` / `PolicyEvaluationResultRes` | Policy evaluate DTOs |
| `RegistryClient` / `RegistryClientImpl` | Registry V2 HTTP, used only here |
| `RegistryClientsConfiguration` | Adapter-only Registry client beans |

Integrity hashing, Git clone, and instantiate live **outside** this package.

## Configuration

This adapter needs Registry in addition to the lasting validator properties (`blueprint.validator.*` and `odm.product-plane.policy-service.*` in [Configuration](../../../../../../../../../../docs/setup/configuration.md)).

```yaml
odm:
  product-plane:
    registry-service:
      active: true
      address: http://localhost:8086
```

| Property | Description |
|----------|-------------|
| `odm.product-plane.registry-service.active` | If `true`, enable the Registry client used to reconstruct publication metadata |
| `odm.product-plane.registry-service.address` | Base URL of Registry V2 |

The Policy engine/policy created by this package is bound to **`DATA_PRODUCT_VERSION_CREATION`**. After removal, register on `DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED` instead.

Enable the Blueprint validator only when Policy is active, Registry’s Policy V1 bridge is on (`policy-service.version: 1` on Registry), and this Registry address is set.

## Allowed / forbidden

**This package CAN depend on core.** It may import the integrity factory/use case, Git/validator configuration (`blueprint.validator.*`), shared exceptions, and `RestUtils`.

**Core MUST NOT depend on this package.**

- No package outside `org.opendatamesh.platform.pp.blueprint.old` may import `old`
- No hashing, Git clone, or instantiate logic lives here
- Do not persist reconstructed events

## Removal

When Policy V2 exists:

1. Delete this tree (`org.opendatamesh.platform.pp.blueprint.old`)
2. Add a thin lasting controller on `POST /api/v1/up/validator/evaluate-policy` that maps Policy V2 evaluate payloads to `EvaluateProtectedResourcesIntegrity`
3. Register the Policy engine/policy on `DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED` instead of `DATA_PRODUCT_VERSION_CREATION`
4. Drop `odm.product-plane.registry-service.*`
