# Protected resources and integrity check

How the Blueprint Server keeps declared files immutable after instantiate, and how it evaluates them when a data product version is published.

Related:

- [Blueprint manifest](../../src/main/java/org/opendatamesh/platform/pp/blueprint/manifest/README.md) — `protectedResources` schema
- [Multi-repository & composition](repositories-and-composition.md) — destination keys, routes, and layouts
- [Blueprint process](blueprint-process.md) — instantiate layout, including `.odm/blueprint/`
- [Configuration](../setup/configuration.md) — validator and Git credentials
- [Git providers](git-providers.md) — clones during evaluation

---

## What a protected resource is

The blueprint **manifest** lists paths that must stay as the blueprint produced them. Typical examples: infrastructure-as-code, locked docs, generated scaffolding that teams must not rewrite.

On **publication**, Policy asks this service to **SHA-256-hash** those paths in the published data-product repository and compare them with a **local re-instantiation** of the same blueprint version (same parameters, no Git push). If a listed file is missing or its contents differ, evaluation **fails** and the message names the path.

Digests are computed at evaluation time. They are **not** stored on `protectedResources[].integrity`. That optional object is leftover schema: **omit it**. Evaluation **ignores** `integrity.value`. If `integrity.algorithm` is present and is not `sha256`, that path fails.

**1→1** (monorepo, no composition) and **N→1** (monorepo with composition) are evaluated. **Polyrepo** layouts (more than one destination repository) are **not applicable yet** — hashing more than one published remote is a later slice.

---

## Paths are post-instantiation and destination-scoped

Each `protectedResources[].path` is relative to a **destination repository root after instantiate**, not the source blueprint tree.

Optional `protectedResources[].repository` names an `instantiation.repositories[].key`. When the field is **omitted**, evaluation falls back to `instantiation.root.repository` — today’s monorepo behaviour (the sole destination for 1→1 and N→1). When the field is **present**, it must be a declared repository key.

Instantiate relocates two **parent** files onto the **root** destination only:

| Source (blueprint repo) | After instantiate (root data-product repo) |
|:------------------------|:-------------------------------------------|
| README at `BlueprintRepo.readmePath` (often `README.md`) | **Moved** to `.odm/blueprint/<filename>` |
| Manifest at `BlueprintRepo.manifestRootPath` (often `manifest.yaml`) | **Deleted**; snapshot written as `.odm/blueprint/blueprint-manifest.yaml` |

Everything else stays where routes wrote it. The descriptor is enriched **in place** on the root with blueprint lineage; it is not moved.

**Do not** protect `README.md` or `manifest.yaml` at those source paths. After instantiate they are gone from both the published tree and the re-instantiated tree, so the check fails even when nothing was tampered with.

The checker **does not** rewrite source paths to `.odm/blueprint/`. To protect parent lineage, declare the destination on the **root** key, for example `.odm/blueprint/README.md` or `.odm/blueprint/**`. Lineage is optional to protect; it is platform provenance, not product scaffolding.

On **N→1**, parent routes and module destinations share one repository. Composition destinations (for example `data-plane/storage/**`) and module sidecars (`.odm/<alias>/`) are valid protected paths. `.odm/blueprint/` exists only on the root.

The spec example (manifest §2.1) protects files that stay in place **and exist in that example repo**:

```yaml
protectedResources:
  - path: infrastructure/core/**
  - path: docs/architecture.md
```

The Blindata **starter blueprint** (new repo from registration) ships only the manifest, README, and descriptor template. Those first two are relocated, and it does not create `infrastructure/`. Its default is therefore an **empty** `protectedResources` list so publication is not applicable until authors add real files and matching paths.

---

## Parent-only (architectural decision)

Only the **parent** blueprint’s `protectedResources` list is evaluated.

A module’s own list is **ignored** when that blueprint is composed into a parent. Standalone instantiate of the module as 1→1 still uses the module’s list.

This is a deliberate product rule: the parent owns the data product, and inheriting a child’s list would require rewriting paths through `composition[].targets` and `.odm/<alias>/` (and would break if the same module is placed at different destinations). Inheritance through `composition[].targets` is a possible future change, not current behaviour. Authors who want a module file protected on the product list that destination path on the parent.

---

## How evaluation works

When a data product version is published and the validator is **active**:

1. Policy calls Blueprint (`POST /api/v1/up/validator/evaluate-policy`) with the published version, including its Git repository and tag.
2. If the version has **no blueprint lineage**, evaluation **passes** (not applicable).
3. If the recorded **parent** blueprint has **no** `protectedResources`, evaluation **passes** (not applicable) — including composed parents.
4. If the layout is **polyrepo** (more than one destination repository), evaluation **passes** (not applicable) until polyrepo hashing is applied. The message names that gap; it does not say composition is unsupported.
5. Otherwise the service clones the **one published root** product repo at the publication tag, clones the **blueprint** source (and composed **module** sources for N→1), re-instantiates locally (same render as instantiate, **no push**, no live product-branch clone), and SHA-256-compares each parent protected path. Stored `integrity.value` is not part of that comparison.

N→1 adds **source** clones (modules), not extra product remotes. Extra `additionalDataProductRepos` on a monorepo product are ignored for cloning in this slice and do not fail the check merely by existing.

A mismatch fails with a business-facing message (file missing from the data product version, not produced by the blueprint, or contents differ). Clone, auth, timeout, and render errors **fail closed**.

---

## Configuration

Off by default. Enable with `blueprint.validator.active: true` and point at Policy. Service-level Git credentials are required for clones on this path — they are **not** taken from the event.

See [Configuration](../setup/configuration.md).

---

↑ Back to [docs index](../README.md)
