# Policy V1 compatibility package (`old`)

This package is a **removable band-aid** so Blueprint can evaluate protected-resources integrity while Policy is still on V1.

Policy V1 dispatches `{ currentState, afterState }` on `DATA_PRODUCT_VERSION_CREATION` and does not include the Git tag or product repository. Registry is therefore fetched **only here** to reconstruct a V2 nested version resource, then the lasting `ProtectedResourcesValidatorService` / `EvaluateProtectedResourcesIntegrity` path runs.

## Purpose

Policy V1 reconstruction for protected-resources validation. Intended for deletion when Policy V2 forwards `DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED` with nested tag + product repo.

## Allowed

**This package CAN depend on core.**

It may import:

- `ProtectedResourcesValidatorService` (V2-shaped adapter → integrity command)
- Policy clients and Policy registration resources under `validator.client` / `validator.resources`
- Shared exceptions, `RestUtils`, configuration types

## Forbidden

**Core MUST NOT depend on this package.**

- No package outside `org.opendatamesh.platform.pp.blueprint.old` may import `old`
- No hashing, Git clone, or instantiate logic lives here
- Do not persist reconstructed events

## Removal

When Policy V2 exists:

1. Delete this tree (`org.opendatamesh.platform.pp.blueprint.old`)
2. Add a thin lasting controller on `POST /api/v1/up/validator/evaluate-policy` that calls `ProtectedResourcesValidatorService` directly
3. Register the Policy engine/policy on `DATA_PRODUCT_VERSION_PUBLICATION_REQUESTED` instead of `DATA_PRODUCT_VERSION_CREATION`
4. Drop `odm.product-plane.registry-service.*` (adapter-only configuration)
