# SPDD Analysis: Data Product repository update on new Blueprint version (Tag-Based 3-Way Merge)

## Original Business Requirement

BDMD-5127

Feature: Support Data Product repository update when a new Blueprint version is available.

## Overview

The Tag-Based 3-Way Merge strategy uses Git tags as static "checkpoints" of pure blueprint renders. These checkpoints give Git a common ancestor (baseline), enabling it to automatically calculate diffs between template updates and custom user edits without requiring extra long-lived branches.

## The 4-Step Process

1. Initial Generation (v1.0)

- For a non-empty target, create a pure orphan branch with an empty index/work tree, render the blueprint there, and create a parentless commit.
- Tag the pure commit as a checkpoint (e.g., blueprint-v1.0.0), then merge it locally into the data product default branch while preserving existing user files.
- For an empty target, the first pure commit may remain directly on the default branch.

2. Blueprint Update (v2.0)

- Create a temporary update branch starting directly off the previous tag checkpoint (blueprint-v1.0.0), not off the default branch.
- Clean the directory
- Instantiate the new blueprint version.
- Commit
- Tag this new commit as the next checkpoint (e.g., blueprint-v2.0.0).
- Push the branch and the tag.

3. Pull Request & Merge

- Open a Pull Request from the temporary update branch into main.
- The 3-Way Merge in Action: Git recognizes blueprint-v1.0.0 as the shared parent and compares three states: - The Baseline: blueprint-v1.0.0 - User Modifications: main - Blueprint Updates: blueprint-v2.0.0
- Non-overlapping changes merge automatically. Overlapping line edits highlight standard merge conflicts for the user to resolve directly in the PR.

4. Cleanup & Readiness for Future Updates

- Once the PR merges into main, delete the temporary update branch.
- The blueprint-v2.0.0 tag remains permanently attached to the commit history on main, naturally serving as the common ancestor for the future v3.0.0 update.

## Flow recap

```
(v1.0 tag)               (v2.0 tag)                (v3.0 tag)
     C1 ────────────────────> C2 ─────────────────────> C3 (update/blueprint-v3.0.0)
      │                        │
      ▼                        ▼ (PR v2 Merged)
   main: M1 ───> M2 ─────────> M3 ───> M4
             (User Edits 1)        (User Edits 2)
```

Notes:

- automatic pr opening is optional and **global** (one on/off for **all** target repositories in the request).
- automatic pr opening is done with Git provider apis (not git primitives), **after** each target’s update branch/tag push succeeds (option A: separate open-PR step).
- **PR open is a side operation**: if it fails after a successful update, the API still returns **success (HTTP 200)** with user-visible **`warnings`**; it must not fail the whole request.
- both pull request merge and the branch cleanup are done by the user manually in the Git provider interface (out of scope)
- checkpoint / update-branch / orphan-init naming is **domain policy** via shared **`BlueprintGitNamingConventions`** (`blueprint-v{versionNumber}` / `update/blueprint-v{versionNumber}` / `odm-init/{uuid}`); git outbound ports only consume those strings.
- the update REST contract must stay **list-based** (`targetRepositories` / `results`) like instantiate so multi-repo layouts (N→1, 1→N, N→N) can be enabled later without breaking clients; phase 1 still validates a single `root` target.
- hexagonal SoC: auth headers factory→git port only; author defaults inside git port; descriptor enrichment via templating outbound port; both **instantiate** and **update** orchestrate Git workflows via **granular** per-use-case git ports so steps stay readable (`createInitialCheckpoint` / `updateFromCheckpoint`); `CreatePullRequest` stays inside update `openPullRequest` only. Layout is selected via shared **`InstantiationScenario`** (only monorepo-no-composition is implemented).

## API rest signature

**POST /api/v2/pp/blueprint/blueprints-versions/update-data-product**

Body content:

- Complete new blueprint version parameters list (with their values)
- Blueprint identifier (name, aligned with instantiate)
- Next Blueprint version
- Current Blueprint version
- **`targetRepositories`** (list, same forward-compatible shape as instantiate): each entry has logical `type` (e.g. `root`), optional integration `branch`, `repository` reference, and optional **`pullRequestTargetBranch`** (PR base for that repo when global PR open is on; falls back to that repository’s `defaultBranch` when unset)
- Git author metadata (to mark git operations with the correct user)
- **`createPullRequest`** (optional boolean, default false): **global** switch — when true, open a PR for **every** processed target repository; when false, open none

Response content:

- **`results`** (list, one entry per processed target repository): for each target — repository identity (enough to correlate with the request), `updateBranchName`, `checkpointTag`, `commitHash`, optional `pullRequestWebUrl`
- **`warnings`** (list of strings, may be empty): user-visible side-operation messages (e.g. PR open failed after a successful update). Presence of warnings does **not** change success status.

**API extensibility (multi-repo / composition):**

- Do **not** use a singular `targetRepository` or singular result object — that would force a breaking change when enabling monorepo+composition (N→1), polyrepo (1→N), or polyrepo+composition (N→N).
- Align with instantiate: keep a **list** on the wire even while phase-1 validation enforces **exactly one `root`** target (same limits as instantiate today: monorepo, no composition).
- Shared fields (blueprint name, current/next versions, parameters, author, **global** `createPullRequest`) stay top-level; per-repo Git outcomes stay in `results[]`; **`pullRequestTargetBranch`** stays on each `targetRepositories[]` entry; side-operation issues (e.g. PR open failure) stay in top-level **`warnings[]`** without failing the request.
- Future multi-repo behavior can widen validation and loop the same per-target tag-based update orchestration without renaming the endpoint or reshaping the request/response schema.

## Scenario 1: Applying Blueprint to a Repo with Existing User Files

### The Challenge

If you render the blueprint directly onto main (which already has user files) and tag main as blueprint-v1.0.0, Git will assume those pre-existing user files are part of the initial blueprint.

When you later create an update branch off blueprint-v1.0.0, wipe the directory, and re-render pure v2.0.0, Git will think the blueprint deleted those user files and will attempt to delete them from main during the PR merge.

### The Solution: Use a "Pure" Initial Commit

To solve this, ensure the blueprint-v1.0.0 tag points exclusively to the initial rendered blueprint files, isolated from the pre-existing user files.

Create an Orphan/Isolated Commit: Create a standalone commit containing only the rendered blueprint files (no user files).

Tag It: Tag this pure commit as blueprint-v1.0.0.

Merge into main: Merge this pure commit into the user’s main branch.

```
       [blueprint-v1.0.0] (Pure template v1)
              C1 ──────────────────────────────> C2 (update/blueprint-v2.0.0)
               \                                  \
                \ (Merge into main)                \ (PR / Merge)
 main:  M0 ────> M1 ────────────────> M2 ──────────> M3
   (Existing    (Has BP v1 +           (User edits
  User Files)   User Files)            user files)
```

Why this works:
Ancestor (C1): Contains [bp_file].

User Branch (main): Contains [existing_user_file, bp_file]. Git sees existing_user_file as an addition made on main.

Update Branch (C2): Contains [bp_file_v2].

Outcome: Git keeps existing_user_file completely untouched and cleanly updates bp_file to v2.

## Scenario 2: Updating a Blueprint File that the User Edited

Because this strategy uses Git's 3-way merge engine (comparing the Baseline Tag, main, and the Update Branch), Git evaluates edits line-by-line:

### Case A: Edits on Different Lines (No Conflict)

If the user modified Line 10 of a file on main, and the blueprint update modified Line 2 of that same file:

Result: No merge conflict. Git automatically combines both changes. The merged file will contain the blueprint's updates on Line 2 and the user's custom edits on Line 10.

### Case B: Edits on the Exact Same Lines (Conflict)

If the user modified Line 10 on main, and the new blueprint version also updated Line 10:

Result: Merge Conflict. Git flags a conflict on Line 10 in the Pull Request. The PR cannot be merged automatically, forcing the user to review the diff and choose which logic to keep directly in the PR interface.

## Domain Concept Identification

#### Existing Concepts (from codebase)

- **Blueprint**: Persisted aggregate (`blueprints` / `Blueprint`) holding identity and linked blueprint Git repository metadata (`blueprints_repositories` / `BlueprintRepo`). Owns versions and supplies Git provider identity (provider type, base URL) used to initialize `GitProvider` for source and target operations.
- **BlueprintVersion**: Persisted version entity (`blueprints_versions`) with `versionNumber`, `content` (manifest JSON), and a `tag` field that points at the **blueprint source repository** release tag (used today by instantiate to clone the blueprint at a frozen pointer). Related to Blueprint by parent UUID; selected by name + version in instantiate.
- **Blueprint Manifest & Parameters**: Manifest content on the version drives instantiation strategy (currently monorepo, no composition), parameter validation, Velocity templating, and lineage under `.odm/blueprint/`. Update must re-apply the **next** version’s manifest/parameters the same way instantiate does.
- **Data Product Target Repository**: Domain `Repository` (from git-utils), already accepted in instantiate via **`targetRepositories`** (list of typed entries: `type`, optional `branch`, `RepositoryRes`). Expected to already exist; instantiate commits onto a branch (default or override). Update must reuse this **list-based** contract so phase-1 monorepo (exactly one `root`) and future polyrepo/composition layouts share one API shape.
- **Instantiate Blueprint Version (use case)**: Public use case `POST .../blueprints-versions/instantiate`. **Aligned in this feature**: auth headers off the domain command (factory→git port only); descriptor enrichment via templating outbound port; resolves **`InstantiationScenario`** and implements only **`MONOREPO_NO_COMPOSITION`** (1→1 singular ROOT source/target); Initial Generation via use-case **`createInitialCheckpoint`** orchestrating a **granular** instantiate git outbound port (orphan → render → commit → tag → merge → push). Unsupported layouts throw `UnsupportedOperationException`.
- **Git Provider & Local Git Operations (git-utils — available)**: `GitProviderFactory` + `GitProvider` / `GitOperation` provide the primitives. **Blueprint-server use cases must not call these primitives directly** — only git outbound port **impls** may. Required primitives for Initial Generation (Scenario 1) and Blueprint Update:
  - `readRepository` at branch/tag/commit
  - `createAndCheckoutBranch` from current HEAD (including detached HEAD after tag checkout); refuses local/remote name collisions
  - `createAndCheckoutOrphanBranch` for a pristine, unborn branch with an empty index/work tree; refuses local/remote collisions and restores the original branch if cleanup fails
  - `mergeBranch` for related or unrelated local histories (including orphan → main); unrelated merge uses an empty-tree baseline and creates a two-parent merge commit; conflicts restore the target tip and clean state
  - `add` / `addAll` (including deletions via `AddMode.ALL` / `TRACKED_ONLY`)
  - `commit`, `addTag`
  - `pushBranch` / `pushTag` for selective publish (plus legacy `push`)
  - `createPullRequest(Repository, CreatePullRequest)` on all supported providers (GitHub, GitLab, Bitbucket, Azure DevOps), returning `PullRequest` (id, webUrl, …); does not merge or delete branches — **`CreatePullRequest` stays inside the git port impl**
- **Git Author Metadata**: Optional commit/tag author name and email; **server default identity fallback lives in the git outbound port** commit path (not in the use case).
- **Use-case architecture conventions**: Controller → `*UseCasesService` → Factory → `UseCase` + Command/Presenter + OutboundPorts (`spdd/norms/USE_CASE_IMPLEMENTATION.md`). Prefer **granular** git outbound ports so use-case `execute()` / scenario methods can show the workflow steps explicitly (clone lifecycle behind a `withCloned…` callback); do not call raw git-utils types from the use case.

#### New Concepts Required

- **Data Product Blueprint Checkpoint Tag**: A permanent Git tag on the **data product** repository marking a **pure** blueprint render commit (baseline for 3-way merge). Distinct from `BlueprintVersion.tag` (blueprint **source** release tag). Naming convention **`blueprint-v{versionNumber}`**, owned by shared **`BlueprintGitNamingConventions`** (not by the git outbound port).
- **Pure Initial Checkpoint Commit (orphan / isolated)**: For repos that already contain user files (Scenario 1), the first checkpoint must be an **orphan/isolated commit** containing **only** rendered blueprint files — never the tip of `main` that already mixes user content. That pure commit is tagged, then **merged into main**, so pre-existing user files remain “additions on main” relative to the baseline. Orchestrated in instantiate as **`createInitialCheckpoint`** using granular git port ops (`withClonedSourceAndTarget`, orphan/commit/tag/merge/push); tag/orphan names from `BlueprintGitNamingConventions`.
- **Blueprint Update Branch (temporary)**: Short-lived branch created **from the current checkpoint tag**, not from the data-product default branch, holding only the pure re-render of the next blueprint version. Naming **`update/blueprint-v{versionNumber}`** via `BlueprintGitNamingConventions`. Deleted manually after PR merge (out of scope).
- **Update Data Product from Blueprint Version (use case)**: Orchestration for Step 2 (+ optional Step 3 PR): resolve current/next versions → `InstantiationScenario` switch → `updateMonorepoNoComposition` (singular ROOT) → use-case **`updateFromCheckpoint`** with granular git port steps → optional **`openPullRequest`** (option A) → return **`results`** / **`warnings`**.
- **Granular update Git outbound port**: `withClonedSourceAndTargetAtCheckpoint`, `createAndCheckoutBranch`, `cleanWorkingTreePreservingGit`, `commitAll`, `createCheckpointTag`, `pushBranch`, `pushTag`, plus `openPullRequest(...)` (git-utils `CreatePullRequest` only inside the port).
- **Per-target update result**: API response unit correlating one request target with that repo’s update refs and optional PR URL; enables 1→N / N→N without schema breakage.
- **Optional Pull Request (provider-side)**: Same-repo PR/MR after each successful update push, controlled by a **single global** `createPullRequest` flag; each target may set **`pullRequestTargetBranch`**. Opened via separate port method (option A). **Best-effort side operation**: PR failure leaves the update successful and adds a **`warnings`** entry (null `pullRequestWebUrl`). Merge and branch deletion remain user-driven and out of scope.
- **Working-tree Clean before Re-instantiate**: Explicit clean before writing the new blueprint render so removed template files do not linger — performed as use-case-orchestrated `gitPort.cleanWorkingTreePreservingGit` inside `updateFromCheckpoint`.
- **Line-level 3-way merge outcomes (Scenario 2)**: Server does not resolve conflicts; Git’s merge in the PR auto-combines non-overlapping line edits and flags same-line overlaps for the user.

#### Key Business Rules

- **Checkpoint tags are the merge baseline**: Updates must branch from the **current** checkpoint tag so Git’s three-way merge compares (baseline tag, default-branch user edits, update-branch blueprint changes).
- **Checkpoint must be pure**: The tagged commit must contain only blueprint-rendered content. Tagging a commit that already includes user files poisons the baseline (Scenario 1 failure mode: later updates appear to delete user files).
- **Initial apply onto non-empty main uses orphan + merge**: Create pure orphan commit → tag checkpoint → merge into main (not “render onto main then tag”).
- **Checkpoint ≠ blueprint source tag**: DP checkpoint naming is separate from `BlueprintVersion.tag` (source release tag).
- **Pure render on the update branch**: After clean, the update commit is only the next blueprint render so the next tag remains a valid future baseline.
- **PR open is optional; merge/cleanup are out of scope**: API may create PRs when the **global** flag is on; it must not merge or delete update branches. PR failure after a successful update is reported as **`warnings`** on HTTP 200, not as a failed request.
- **Conflicts are user-resolved in the provider UI**: Different-line edits merge automatically; same-line edits conflict in the PR (Scenario 2). Server does not auto-resolve.
- **Initial generation must leave a pure checkpoint**: Without a v1 pure checkpoint tag, Step 2 cannot establish a correct common ancestor. Current instantiate neither tags nor uses orphan+merge. For multi-target layouts, each target that receives rendered content needs its own pure checkpoint.
- **Parameters for the next version**: Request carries the full parameter map for the **new** version; validation follows that version’s manifest.
- **Git identity**: Commits/tags attributed with provided author metadata; **blank author → defaults applied in the git outbound port**.
- **List-based targets/results from day one**: Request uses `targetRepositories[]` and response uses `results[]` (instantiate pattern). Phase 1 validates exactly one `root` / monorepo-no-composition; widening to N→1 / 1→N / N→N must not break the public contract.
- **Tag-based merge is per target repository**: Checkpoint tags and update branches are scoped to each data-product Git repository in the list; the PR **on/off** decision is global across that list, while each entry may specify its own PR target branch.
- **Separation of concerns**: Use cases own orchestration + naming via `BlueprintGitNamingConventions` + `InstantiationScenario`; git ports own Git I/O + auth headers + author defaults; templating ports own render + descriptor enrichment. Neither use case sees auth headers or `CreatePullRequest`; both call granular git port methods so workflow steps remain visible.

## Strategic Approach

#### Solution Direction

- Introduce `POST /api/v2/pp/blueprint/blueprints-versions/update-data-product` as a dedicated use case (`BlueprintVersionsUseCaseController` / `BlueprintVersionUseCasesService`), Factory → UseCase → OutboundPorts.
- Reuse instantiate **manifest validation and Velocity templating**; route **descriptor lineage enrichment through the templating outbound port** (factory injects `BlueprintDataProductDescriptorService` into the templating impl only).
- Git orchestration is **use-case-visible with granular outbound ports**, with git-utils used only inside port impls:
  - Update: use-case `updateFromCheckpoint` (clone at checkpoint → create/checkout update branch → clean → render → commit → tag → pushBranch + pushTag) → optional separate **`openPullRequest`** when global flag is true (option A).
  - Initial generation (companion on instantiate): use-case `createInitialCheckpoint` + granular git port (orphan → render → commit → tag with use-case-supplied name → merge into integration → pushBranch + pushTag). Do **not** tag main’s mixed tip or push the temporary orphan branch.
- Consume git-utils **1.1.x** (bump blueprint-server dependency).
- Checkpoint / update-branch / orphan naming via shared **`BlueprintGitNamingConventions`** (`blueprint-v{version}` / `update/blueprint-v{version}` / `odm-init/{uuid}`) so version → ref needs no extra DB state; git ports consume the strings.
- Shape the REST contract like instantiate: **`targetRepositories[]` in + `results[]` out**; phase-1 validation = exactly one `root` / monorepo no composition; optional PR open is a **global** body flag; each target entry may set **`pullRequestTargetBranch`**.
- Auth headers: factory → git port ctor only (update and **aligned instantiate**); never on the domain command.
- When multi-repo/composition becomes supported, update reuses the same list contract and fills the corresponding `InstantiationScenario` method without a breaking API change.

#### Key Design Decisions

- **Separate update use case vs extending instantiate**: Git flows differ → **Dedicated update use case**; evolve instantiate for pure checkpoint + SoC alignment (auth off command, enrichment via templating port, scenario routing, `createInitialCheckpoint` with granular git port).
- **Convention-based DP checkpoint tags**: → shared **`BlueprintGitNamingConventions`** (`blueprint-v{versionNumber}` / `update/blueprint-v{versionNumber}` / `odm-init/{uuid}`); git ports do not own naming.
- **Git outbound port altitude**: → both update and instantiate use **granular** git ports so workflows stay in the use case; update keeps separate **`openPullRequest`** (`CreatePullRequest` only inside the port).
- **PR open after update (option A)**: → Global flag; after successful `updateFromCheckpoint`, use case calls `openPullRequest`. If PR fails → still HTTP 200 with **`warnings`** (update acknowledged); PR target = entry `pullRequestTargetBranch` or repo `defaultBranch`.
- **git-utils consumption**: → Consume APIs **only inside git port impls**; avoid embedding raw JGit/provider HTTP in blueprint-server use cases.
- **Pure initial commit for non-empty repos (Scenario 1)**: → instantiate **`createInitialCheckpoint`** with orphan → tag → merge → push; tag/orphan names from `BlueprintGitNamingConventions`.
- **Empty vs non-empty target at first apply**: Prefer the pure-commit path consistently.
- **Working-tree clean on update**: → Use-case-orchestrated `cleanWorkingTreePreservingGit` inside `updateFromCheckpoint` (preserve `.git`; stage deletions on commit).
- **Author defaults**: → Inside git port commit paths when name/email blank.
- **Auth headers**: → Factory → git port only; not on domain command; not validated in use case `execute()`.
- **Descriptor enrichment**: → Templating outbound port method; not a Spring bean dependency of the use case.
- **Conflict handling**: → No server-side merge resolution; document Scenario 2 for clients.
- **Forward-compatible multi-repo API**: → List-based `targetRepositories` + `results` from day one; phase-1 single-`root`; PR on/off global; PR base per target.
- **Remaining work is blueprint-server orchestration only**: Wire granular ports to git-utils 1.1.x; no further Git-library feature work required for BDMD-5127.

#### Alternatives Considered

- **Long-lived template branch as forever merge base**: Rejected — tags are the chosen checkpoint mechanism.
- **Server-side merge / patch application for updates**: Rejected — Git’s 3-way merge in the user’s PR is the product strategy.
- **Always open PR**: Rejected — optional via global flag.
- **Per-target PR on/off flags**: Rejected — product choice is one switch for all repositories in the request.
- **Global-only `pullRequestTargetBranch`**: Rejected — each repository must be able to specify its own PR base branch.
- **PR embedded inside the update Git workflow method**: Rejected — option A keeps update vs PR side-operation boundaries clear (`openPullRequest` separate).
- **Fail the whole request when PR open fails**: Rejected — PR is best-effort; client must still see a successful update via HTTP 200 + `warnings`.
- **Raw git-utils types / `CreatePullRequest` in the use case**: Rejected — use granular outbound port methods; keep `CreatePullRequest` inside the port impl.
- **Checkpoint naming owned by the git outbound port**: Rejected — naming is domain policy (`BlueprintGitNamingConventions`).
- **Auth headers on the domain command** (current instantiate pattern): Rejected for update and to be removed from instantiate in this feature — factory→git port only.
- **Use case injects `BlueprintDataProductDescriptorService`**: Rejected — enrichment via templating outbound port.
- **Reuse** `BlueprintVersion.tag` **as DP checkpoint name**: Rejected as default — different meaning (source release vs DP baseline).
- **Tag main after overlay render (including existing user files)**: Rejected — causes Scenario 1 false deletions on later updates.
- **Re-implement create-branch / orphan / merge / PR in blueprint-server**: Rejected — git-utils already provides them.
- **Singular `targetRepository` / singular result DTO**: Rejected — would require a breaking schema change to support multi-repo scenarios; instantiate already proves the list-with-phase-1-validation pattern.

## Risk & Gap Analysis

#### Requirement Ambiguities

- **Checkpoint tag naming convention**: **Chosen** — `blueprint-v{versionNumber}` via `BlueprintGitNamingConventions`.
- **Update branch naming convention**: **Chosen** — `update/blueprint-v{versionNumber}` via `BlueprintGitNamingConventions`; collisions refused by git-utils (surfaced by git port).
- **Blueprint identifier form**: **Chosen** — align with instantiate (name + version numbers).
- **PR target branch**: **Chosen** — optional **`pullRequestTargetBranch` per `targetRepositories` entry**; else that repo’s default branch.
- **Scope of Initial Generation / Scenario 1**: **In this ticket** — evolve instantiate with `createInitialCheckpoint` + granular git port + `InstantiationScenario` routing + SoC alignment (auth off command, enrichment via templating port).
- **Optional PR shape**: **Chosen** — global `createPullRequest`; open via separate `openPullRequest` after successful update (option A); per-target PR base branch; PR failure → **`warnings`** on success response.
- **Current vs next version validation** (ordering, adjacency, lineage match) — still open beyond same-Blueprint existence checks.
- **Who performs the “Merge into main” of the pure initial commit**: **Chosen default** — server-side local merge via use-case-orchestrated `gitPort.mergeBranch` inside `createInitialCheckpoint` (then push the integration branch).
- **Multi-repo / non-monorepo behavior**: API shape is list-based and forward-compatible; runtime still limited like instantiate until templating supports those layouts. Per-target/module naming discriminator may be needed later.
- **Partial failure across multiple targets**: defer until multi-target is enabled; phase 1 has a single target.

#### Edge Cases

- **Repo with existing user files + naive tag-on-main** (Scenario 1 anti-pattern): must be prevented by pure initial commit; otherwise later updates delete user files.
- **Missing current checkpoint tag**: fail fast; do not fall back to default branch.
- **Next tag or update branch already exists**: reject (aligns with git-utils collision behavior); define retry UX.
- **Concurrent updates** racing on branch/tag names.
- **Previous update PR never merged**: divergent checkpoint history if “current” is chosen incorrectly.
- **Blueprint file removed in next version**: clean + deletions must appear on the update branch; user-only files introduced relative to the pure baseline (Scenario 1) must remain.
- **User edited same lines as blueprint update** (Scenario 2B): expected PR conflict; no server resolution.
- **User edited different lines** (Scenario 2A): expected clean auto-merge in PR.
- **Empty commit** after clean+render identical to previous pure tip — allow/deny policy.
- **Auth scopes**: push branch/tag vs create PR may differ.

#### Technical Risks

- **Dependency version bump**: blueprint-server must adopt git-utils **1.1.x** (still may be pinned lower until implementation).
- **Instantiate SoC + pure checkpoint**: Implemented via auth off command, enrichment via templating port, `InstantiationScenario`, and `createInitialCheckpoint` + granular git port alongside update.
- **Clean + render correctness**: Bad clean (touching `.git`, missing deletion staging) corrupts merge semantics — owned by `cleanWorkingTreePreservingGit` / `commitAll` inside `updateFromCheckpoint`; mitigate with ITs for Scenario 1 and 2.
- **Local merge then push of integration branch**: After `mergeBranch`, push integration branch; mid-flow failure can leave remote without merge while tag was pushed — ordering is explicit in use-case `createInitialCheckpoint` (tag → merge → pushBranch → pushTag).
- **PR after push (option A)**: Branch/tag may already exist on remote if `openPullRequest` fails — still return HTTP 200 with clear **`warnings`** so the client can acknowledge the successful update and handle PR manually.
- **Long-running Git I/O**: same class of concern as instantiate.
- **Provider PR differences**: already abstracted; still verify auth scopes and webUrl presence for UX.

#### Acceptance Criteria Coverage

| AC# | Description                                                                                                                              | Addressable? | Gaps/Notes                                                                                              |
| --- | ---------------------------------------------------------------------------------------------------------------------------------------- | ------------ | ------------------------------------------------------------------------------------------------------- |
| 1   | Expose `POST .../update-data-product` with blueprint id, current/next versions, parameters, **`targetRepositories[]`**, author metadata  | Yes          | Align with instantiate list shape; phase-1: exactly one `root`                                          |
| 2   | Create temporary update branch from **current** checkpoint tag (not default branch)                                                      | Yes          | Use-case `updateFromCheckpoint` → `createAndCheckoutBranch`; names from `BlueprintGitNamingConventions` |
| 3   | Clean working tree, instantiate **next** version, commit with author metadata                                                            | Yes          | Clean+commit in git port; render/enrich via templating port callback; author defaults in git port       |
| 4   | Create and push **next** checkpoint tag; push update branch                                                                              | Yes          | Use-case `updateFromCheckpoint` (`createCheckpointTag` + `pushTag` / `pushBranch`)                      |
| 5   | Response returns **`results[]`** with branch name, checkpoint tag, commit hash (and optional PR URL) per target, plus **`warnings[]`** | Yes          | List response from day one; phase 1 length = 1; warnings for PR side-op failures                        |
| 6   | Optional automatic PR via provider APIs                                                                                                  | Yes          | Global flag; option A; **PR failure → warnings on HTTP 200**, not ErrorRes; merge/delete out of scope   |
| 7   | PR merge and update-branch deletion remain manual                                                                                        | Yes          | Non-goal                                                                                                |
| 8   | Checkpoint tag naming resolves tag from blueprint version                                                                                | Yes          | **Chosen**: `BlueprintGitNamingConventions` → `blueprint-v{versionNumber}`                              |
| 9   | Next tag remains future merge base after user merges PR                                                                                  | Yes          | Git behavior once tags/commits are pure                                                                 |
| 10  | Initial generation produces a **pure** checkpoint (Scenario 1): orphan → tag → merge into main when user files may exist                 | Yes          | Instantiate `createInitialCheckpoint` + granular git port; naming from `BlueprintGitNamingConventions`  |
| 11  | Pre-existing user files are not deleted by later blueprint updates (Scenario 1 outcome)                                                  | Yes          | Guaranteed by pure baseline once AC10 is implemented; verify with IT                                    |
| 12  | Same-file different-line user vs blueprint edits auto-merge in PR (Scenario 2A)                                                          | Yes          | Git/provider merge behavior; document; server does not resolve                                          |
| 13  | Same-line overlapping edits produce PR conflict for user resolution (Scenario 2B)                                                        | Yes          | Document; no server auto-resolve                                                                        |
| 14  | Public API remains non-breaking when multi-repo/composition layouts are enabled later                                                    | Yes          | List request/response + `InstantiationScenario` methods                                                 |
| 15  | Hexagonal SoC: no auth headers / `CreatePullRequest` in use case; granular git ports; enrichment via templating port; both use cases aligned | Yes       | Matches REASONS canvas prompt                                                                           |
