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

On **publication**, Policy asks this service to **SHA-256-hash** those paths in the published data-product repository and compare them with a **local re-instantiation** of the same blueprint version (same parameters, no Git push). If a listed file, directory, or glob is missing, produces a different file set, or has different contents, evaluation **fails** and the message names the path.

Digests are computed at evaluation time. They are **not** stored on `protectedResources[].integrity`. That optional object is leftover schema: **omit it**. If it is present, manifest publication still requires non-empty `algorithm` and `value`; evaluation **ignores** `value`. An algorithm other than `sha256` (case-insensitive) makes that path fail.

The same model applies to **1→1**, composed **N→1**, split **1→N**, and composed **N→N** layouts. Composition changes the source trees used for re-instantiation; multiple destination repositories change the published and expected tree pairs. Each protected destination is still compared independently.

---

## Paths are post-instantiation and destination-scoped

Each `protectedResources[].path` is relative to a **destination repository root after instantiate**, not the source blueprint tree.

Optional `protectedResources[].repository` names a `targetRepositories[].key`. When the field is omitted or blank, it means the single target marked `isRoot: true`. This root shorthand is intentional because protecting the primary repository is expected to be the most common case. When the field is non-blank, it must exactly match a declared target key.

Instantiate relocates two **parent** files onto the **root** destination only:

| Source (blueprint repo) | After instantiate (root data-product repo) |
|:------------------------|:-------------------------------------------|
| README at `BlueprintRepo.readmePath` (often `README.md`) | **Moved** to `.odm/blueprint/<filename>` |
| Manifest at `BlueprintRepo.manifestRootPath` (often `manifest.yaml`) | **Deleted**; snapshot written as `.odm/blueprint/blueprint-manifest.yaml` |

Everything else stays where routes wrote it. The descriptor is enriched **in place** on the root with blueprint lineage; it is not moved.

**Do not** protect `README.md` or `manifest.yaml` at those source paths. After instantiate they are gone from both the published tree and the re-instantiated tree, so the check fails even when nothing was tampered with.

The checker **does not** rewrite source paths to `.odm/blueprint/`. To protect parent lineage, declare the destination on the **root** key, for example `.odm/blueprint/README.md` or `.odm/blueprint/**`. Lineage is optional to protect; it is platform provenance, not product scaffolding.

On **N→1**, parent routes and module destinations share one repository. Composition destinations (for example `data-plane/storage/**`) and module sidecars (`.odm/<alias>/`) are valid protected paths. `.odm/blueprint/` exists only on the root.

The monorepo spec example (manifest §2.1) uses root shorthand for files that stay in place **and exist in that example repo**:

```yaml
protectedResources:
  - path: infrastructure/core/**
  - path: docs/architecture.md
```

In a polyrepo manifest, add `repository` only for a non-root target (which must also be declared in `targetRepositories[]`):

```yaml
protectedResources:
  - path: infrastructure/core/** # root shorthand
  - path: deployment/**
    repository: operations-repo
```

### Path matching and safety

- A declaration may name one regular file, one directory, or a glob. Directories are traversed recursively; globs are evaluated relative to the selected destination root.
- Only regular files are compared. `.git` is always excluded, and each declaration must match at least one file in both the published and re-instantiated trees.
- Files are identified by repository-relative `/` paths and compared with lowercase SHA-256 digests of their raw bytes.
- Empty paths, absolute paths, `..` traversal, and paths that escape the repository root are invalid.
- Symbolic links are never followed. A selected symlink, or a symlink encountered below a protected literal directory, fails safely.

The Blindata **starter blueprint** (new repo from registration) ships only the manifest, README, and descriptor template. Those first two are relocated, and it does not create `infrastructure/`. Its default is therefore an **empty** `protectedResources` list so publication is not applicable until authors add real files and matching paths.

---

## Parent-only (architectural decision)

Only the **parent** blueprint’s `protectedResources` list is evaluated.

A catalog `MODULE` must not declare a non-empty list; Module publication rejects it. Modules cannot instantiate or govern a data product independently, so retaining an inert policy would be misleading.

This is a deliberate product rule: the parent owns the data product, and inheriting a child’s list would require rewriting paths through typed instantiation routes and `.odm/<alias>/` (and would be ambiguous if the same Module were routed to different destinations). Inheritance is a possible future change, not current behaviour. Authors who want Module-originated output protected must list its final destination path and target on the parent.

---

## How evaluation works

When a data product version is published and the validator is **active**:

1. Policy calls Blueprint (`POST /api/v1/up/validator/evaluate-policy`) for the published version. The Policy V1 adapter obtains the complete Registry product/version context needed by the check.
2. If the version has **no blueprint lineage**, evaluation **passes** (not applicable).
3. If the recorded **parent** blueprint has **no** `protectedResources`, evaluation **passes** (not applicable) — including composed parents.
4. Each item resolves to its explicit `repository` or to the manifest’s `isRoot: true` target. The resulting distinct keys are the **protection coverage set**.
5. For each protected root target, the service uses Registry `dataProductRepo` and the version’s root `tag`. For each protected non-root target, it exactly joins `additionalDataProductRepos[].repositoryKey` with `additionalTags[].repositoryKey` and clones at that additional tag’s own `tag` value. Non-root refs never fall back to the root tag.
6. The service clones only those published targets at their own recorded refs, clones the parent Blueprint and any composed Module sources needed for re-instantiation, and renders disposable expected targets with the production instantiate semantics.
7. It SHA-256-compares every parent protected path within the published and expected trees for the same target key. Stored `integrity.value` is not part of that comparison.

N→1 adds **source** clones (Modules), not extra product remotes. In 1→N and N→N, each protected destination adds a published tree pair; unprotected **published** destinations are not cloned for this policy. Local re-instantiation still renders every declared destination once so production routing semantics remain authoritative.

A mismatch fails with a business-facing message (file missing from the data product version, not produced by the Blueprint, or contents differ). Missing, blank, duplicate, or conflicting Registry locator/ref data for a **referenced** target also fails closed before Git access. Unreferenced target mappings do not affect the result. Clone, auth, timeout, and render errors fail closed, and evaluation may stop at the first conclusive failure.

## Deliberate scope decisions

- Each publication uses only the protected-resource list from its recorded parent Blueprint version. A later version may add, remove, or retarget entries without a cross-version protection check.
- Blueprint update checkpoints (`blueprint-v*`) are not publication approval. Evaluation always compares against the product version’s recorded publication refs, even when an unchanged update reused a render checkpoint.
- Registry keys introduced or joined by this feature use Registry’s exact-string matching. The integrity feature does not add broader trimming or case-normalization behavior.
- Two logical targets may resolve to the same physical remote. No alias check is performed; each protected logical target is cloned and compared independently.
- The guarantee is the immediate publication decision. Repository locators are not snapshotted per version, so repeatable historical evaluation after a locator change is outside the current scope.
- The current Policy V1 adapter enriches its context from Registry. If future direct Policy V2 delivery lacks required locators or refs, the Registry publication event can be extended then.

---

## Configuration

Off by default. Enable with `blueprint.validator.active: true` and point at Policy. Service-level Git credentials are required for clones on this path — they are **not** taken from the event.

See [Configuration](../setup/configuration.md).

---

↑ Back to [docs index](../README.md)
