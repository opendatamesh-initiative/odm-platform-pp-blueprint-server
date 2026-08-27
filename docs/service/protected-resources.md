# Protected resources and integrity check

How the Blueprint Server keeps declared files immutable after instantiate, and how it evaluates them when a data product version is published.

Related:

- [Blueprint manifest](../../src/main/java/org/opendatamesh/platform/pp/blueprint/manifest/README.md) — `protectedResources` schema
- [Blueprint process](blueprint-process.md) — instantiate layout, including `.odm/blueprint/`
- [Configuration](../setup/configuration.md) — validator and Git credentials
- [Git providers](git-providers.md) — clones during evaluation

---

## What a protected resource is

The blueprint **manifest** lists paths that must stay as the blueprint produced them. Typical examples: infrastructure-as-code, locked docs, generated scaffolding that teams must not rewrite.

On **publication**, Policy asks this service to compare those paths in the published data-product repository with a **local re-instantiation** of the same blueprint version (same parameters, no Git push). If a listed file is missing or its contents differ, evaluation **fails** and the message names the path.

This slice supports **monorepo, no composition** only. Other strategies pass as not applicable.

---

## Paths are post-instantiation

Each `protectedResources[].path` is relative to the **data-product repository root after instantiate**, not the source blueprint tree.

Instantiate relocates two files:

| Source (blueprint repo) | After instantiate (data-product repo) |
|:------------------------|:--------------------------------------|
| README at `BlueprintRepo.readmePath` (often `README.md`) | **Moved** to `.odm/blueprint/<filename>` |
| Manifest at `BlueprintRepo.manifestRootPath` (often `manifest.yaml`) | **Deleted**; snapshot written as `.odm/blueprint/blueprint-manifest.yaml` |

Everything else (for example `infrastructure/`, `docs/`, rendered templates, the data-product descriptor) stays where it was written. The descriptor is enriched **in place** with blueprint lineage; it is not moved.

**Do not** protect `README.md` or `manifest.yaml` at those source paths. After instantiate they are gone from both the published tree and the re-instantiated tree, so the check fails even when nothing was tampered with. A product team adding their own root `README.md` would also fail.

The checker **does not** rewrite source paths to `.odm/blueprint/`. To protect lineage, declare the destination, for example `.odm/blueprint/README.md` or `.odm/blueprint/**`. Lineage is optional to protect; it is platform provenance, not product scaffolding.

The spec example (manifest §2.1) protects files that stay in place **and exist in that example repo**:

```yaml
protectedResources:
  - path: infrastructure/core/**
  - path: docs/architecture.md
```

The Blindata **starter blueprint** (new repo from registration) ships only the manifest, README, and descriptor template. Those first two are relocated, and it does not create `infrastructure/`. Its default is therefore an **empty** `protectedResources` list so publication is not applicable until authors add real files and matching paths.

---

## How evaluation works

When a data product version is published and the validator is **active**:

1. Policy calls Blueprint (`POST /api/v1/up/validator/evaluate-policy`) with the published version, including its Git repository and tag.
2. If the version has **no blueprint lineage**, evaluation **passes** (not applicable).
3. If the recorded blueprint has **no** `protectedResources`, evaluation **passes** (not applicable).
4. Otherwise the service clones the **product** repo at the publication tag, clones the **blueprint** source, re-instantiates locally (same render as instantiate, **no push**), and compares each protected path.

A mismatch fails with a business-facing message (file missing from the data product version, not produced by the blueprint, or contents differ). Clone, auth, timeout, and render errors **fail closed**.

---

## Configuration

Off by default. Enable with `blueprint.validator.active: true` and point at Policy. Service-level Git credentials are required for clones on this path — they are **not** taken from the event.

See [Configuration](../setup/configuration.md).

---

↑ Back to [docs index](../README.md)
