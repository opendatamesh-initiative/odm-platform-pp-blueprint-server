# Protected resources

How to declare files the Blueprint Server must keep unchanged after instantiate, how to enable the publication check, and how that check works.

Related:

- [Blueprint manifest](../../src/main/java/org/opendatamesh/platform/pp/blueprint/manifest/README.md) — `protectedResources` schema
- [Multi-repository & composition](repositories-and-composition.md) — destination keys, routes, and layouts
- [Configuration](../setup/configuration.md) — validator, Policy Service, and Git credentials

---

## What a protected resource is

A protected resource is a file, directory, or glob that must stay as the blueprint produced it. Typical examples are infrastructure-as-code, locked documentation, and generated scaffolding that teams must not rewrite.

On **data-product publication**, the Blueprint Server SHA-256-hashes those paths in the published repository and compares them with a **local re-instantiation** of the same parent blueprint version (same parameters, no Git push). If a listed path is missing, produces a different file set, or has different contents, the check **fails** and the message names every mismatched path.

The check is **not applicable** (it passes) when the data product version has no blueprint lineage, or when the recorded parent blueprint has an empty `protectedResources` list.

Only the **parent** blueprint’s list is evaluated. A catalog `MODULE` must leave `protectedResources` empty. To protect files that come from a module, list their **final destination path** on the parent.

The same declaration model applies to 1→1, N→1, 1→N, and N→N layouts.

---

## How to declare them

Each `protectedResources[].path` is relative to a **destination repository root after instantiate**, not the source blueprint tree.

Optional `protectedResources[].repository` names a `targetRepositories[].key`. Omit it (or leave it blank) to protect the target marked `isRoot: true`. When the field is set, it must match a declared target key.

A 1→1 (or root-only) list:

```yaml
protectedResources:
  - path: infrastructure/core/**
  - path: docs/architecture.md
```

In a polyrepo manifest, add `repository` only for a non-root target:

```yaml
protectedResources:
  - path: infrastructure/core/** # root shorthand
  - path: deployment/**
    repository: operations-repo
```

Path matching:

- A declaration may name one regular file, one directory, or a glob. Directories are traversed recursively; globs are evaluated relative to the selected destination root.
- Only regular files are compared. `.git` is always excluded, and each declaration must match at least one file in both the published and re-instantiated trees.
- Files are identified by repository-relative `/` paths and compared with lowercase SHA-256 digests of their raw bytes.
- Empty paths, absolute paths, `..` traversal, and paths that escape the repository root are invalid.
- Symbolic links are never followed. A selected symlink, or a symlink under a protected directory, fails the check.

---

## How to enable the check

The check is **off by default**. To turn it on:

1. Run the **Policy Service** and point Blueprint at it (`odm.product-plane.policy-service.active` and `address`).
2. Set `blueprint.validator.active: true`.
3. Configure service-level Git credentials under `blueprint.validator.git.credentials` so the check can clone published and blueprint repositories. Credentials are not taken from the publication event.
4. Optionally set `blueprint.validator.evaluation-timeout-seconds` and `blueprint.validator.policy.blocking`.

See [Configuration](../setup/configuration.md) for the full property list.

---

## How verification works

When a data product version is published and the validator is active:

1. Policy asks Blueprint to evaluate the published version.
2. No blueprint lineage, or an empty parent `protectedResources` list → **pass** (not applicable).
3. Blueprint clones each **protected** published repository at its recorded tag, re-instantiates the recorded parent blueprint locally, and SHA-256-compares every declared path.
4. **All path mismatches are reported together** in one failure message (file missing from the data product version, not produced by the blueprint, or contents differ).
5. Infrastructure errors — clone, authentication, timeout, or failed re-instantiation — **stop immediately**. The check cannot continue, so remaining repositories are not cloned.

---

↑ Back to [docs index](../README.md)
