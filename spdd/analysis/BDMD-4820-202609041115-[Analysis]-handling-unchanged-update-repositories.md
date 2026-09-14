# SPDD Analysis: Handling unchanged repositories on multi-repository blueprint update

Companion to `BDMD-4820-202608271040-[Analysis]-support-all-update-repository-scenarios.md` and prompt `BDMD-4820-202608271455`. That work enabled 1→1 / N→1 / 1→N / N→N content-only updates. It explicitly deferred empty-commit policy to “whatever the Git layer already does.” This analysis **closes that gap**.

## Original Business Requirement

BDMD-4820
Handling multi-repository blueprint updates

When updating a multi-repository data product instantiated from a blueprint, sometimes not ALL the repositories have changed their content. Currently this causes errors when committing with git (no changes detected).

---

## Domain Concept Identification

### Existing Concepts (from codebase)

- **Update data product from blueprint version**: Hexagonal use case behind `POST /api/v2/pp/blueprint/blueprints-versions/update-data-product`. For every mapped target that receives routes it currently always: open at current checkpoint → create update branch → clean → re-render next routes (descriptor + parent lineage on the root key) → **commit** → tag `blueprint-v{next}` → push branch + tag → optional PR. Relationship: one sequential per-target loop; Git failure is **fail-fast** and aborts later remotes.
- **Multi-source, multi-target update (1→N / N→N)**: Parent (and modules) may route subtrees to **different** remotes. Next-version **content** often changes only some keys. Relationship: an unchanged remote is a **normal polyrepo outcome**, not a malformed request.
- **Pure checkpoint / tag-based 3-way merge**: Each remote that received instantiate files has `blueprint-v{current}` as a pure render. The next update must leave `blueprint-v{next}` on that remote so a later roll-forward can open at that tag. Relationship: the checkpoint **name** is per remote (same tag string, different Git repositories). The tag must exist even when the **tree** did not change; otherwise the next update of that remote fails with a missing checkpoint while other remotes have already moved forward.
- **Working-tree clean before re-render**: Update deletes the working tree (preserving `.git`), then writes the next pure render. If that tree is byte-identical to the current checkpoint, Git sees a clean working tree.
- **Git commit refuses empty commits**: `git-utils` `commit` inspects working-tree status and throws when it is clean (`No changes to commit. Working tree is clean.`). Blueprint-server’s update Git port always stages and commits after render; the exception is mapped to HTTP 400 (`GitOperationFailed`). Registry tests also rely on this throw — it is **intentional library behavior**, not a bug in git-utils.
- **Per-target update result + request-level warnings**: Each processed key already returns branch name, next checkpoint tag, commit SHA, optional PR URL. `warnings[]` is the established channel for **successful** side-operation caveats (PR open failed after a successful push). Relationship: an unchanged remote is closer to a **successful no-op** than to a Git failure, so it should not use the fail-fast error path.
- **Optional global pull request**: When `createPullRequest` is true, a PR is opened from the pushed update branch. An empty or missing branch makes a PR meaningless (and provider APIs may reject “no commits between” PRs).
- **Structure freeze / content-only update**: Current vs next **layout** is already frozen. Identical files on some remotes is allowed **content** (including “no file delta on that key”). It is not a structural validation error.

### New Concepts Required

- **Unchanged update target (content no-op)**: After clean + next render, a target whose working tree matches the current checkpoint. Relationship: the update **job** for that key still succeeded; Git **publish** of a new commit/branch/PR did not. The next checkpoint tag must still be published so version identity stays aligned across remotes.
- **Checkpoint retag on existing pure commit**: When there is no tree delta, `blueprint-v{next}` is attached to the **same** commit that already carries `blueprint-v{current}`. Relationship: two version tags may share one SHA on that remote. Future updates check out `blueprint-v{next}` and still get a pure baseline. 3-way merge for a **later** version that does change files remains valid (ancestor is that shared commit).
- **Skipped Git publish (branch / commit / PR)**: For a content no-op, do **not** create an empty commit, do **not** push `update/blueprint-v{next}`, do **not** open a PR. Relationship: avoids empty PRs and leftover branches; clients must not be told a branch exists on the remote if it was not pushed.

### Key Business Rules

- An unchanged remote **must not** fail the whole update request. Real Git errors (clone, missing current tag, push rejection, next-tag collision) remain fail-fast.
- After a successful request, **every** processed target — changed or not — must have checkpoint tag `blueprint-v{next}` on the remote, so the next roll-forward can use a single current/next version pair for all keys.
- Unchanged targets must **not** receive an empty commit. Empty commits add noise and empty PRs; git-utils also forbids them, and other consumers (registry) depend on that.
- Unchanged targets must **not** get an update branch push or a pull request. There is nothing to merge.
- Changed targets keep today’s policy: commit, tag next on the **new** SHA, push branch + tag, optional PR.
- The same no-op policy applies to **1→1** (and N→1) when the single remote’s next render is identical — not only to polyrepo. Polyrepo is the common case (lineage/descriptor on the root key often changes even when a secondary key’s files do not).
- Detection is **after** clean + render on that target (actual Git working tree), not a pre-flight guess from the manifest. Lineage/descriptor writes on the root key count as changes.
- Unchanged is **success** (HTTP 200), not validation 400. The client should be able to see which targets were no-ops (warning and/or a non-breaking per-result indicator).
- Do not skip the target entirely (no tag). That would strand that remote on `blueprint-v{current}` while the API’s next call uses `{next}` as current for **all** keys.
- Do not fall back to the integration branch. No-op still starts from the current checkpoint.
- UI remains out of scope for this backend ticket; the response must stay list-compatible so the existing update wizard can keep rendering results and warnings.

---

## Strategic Approach

### Solution Direction

Treat this as a **backend** policy change inside the existing **update** use case only (blueprint-server). Instantiate is unaffected (first apply always writes a new tree onto an orphan). Registry and git-utils **commit-refuses-empty** behavior stay as they are.

After each target is cleaned and re-rendered, the use case **asks whether the working tree has changes** before it publishes Git refs:

- **Has changes** → existing path: commit, tag next on the new commit, push branch + tag, optional PR.
- **No changes** → do not commit; place `blueprint-v{next}` on the current checkpoint commit; push **that tag only**; omit branch push and PR; still append a `results[]` row and a user-visible no-op notice; continue the remaining targets.

High-level data flow is unchanged until the per-target publish step: validate → materialize next sources → for each target, open at current checkpoint → clean → apply next routes / root lineage → **then branch on cleanliness** → present results + warnings.

Detection belongs in the Git outbound port (working-tree fact). The use case owns **when** to skip publish vs commit (business policy). Do not catch “No changes to commit” as control flow.

git-utils today exposes commit, tag, push, and branch-scoped HEAD SHA, but **not** a working-tree cleanliness query. A small **git-utils capability** (inspect status after render) is required so blueprint-server does not parse exception messages or call JGit outside git-utils. Do **not** change `commit` to allow empty commits — registry and git-utils tests rely on the throw.

### Key Design Decisions

- **Skip empty commit vs allow empty commit (**`--allow-empty`**)**: Allowing empty commits would keep a unique SHA per version and a linear tag chain, but would push empty update branches and encourage empty PRs. git-utils currently rejects empty commits; relaxing that would affect registry. → **Skip the commit.** Retag the existing pure SHA as `blueprint-v{next}`. 3-way merge for later real deltas still has a pure ancestor.
- **Retag vs skip the target entirely**: Skipping tag+push leaves that remote without `blueprint-v{next}`. The API takes **one** current/next version for the whole request, so the next update would fail on that remote (missing checkpoint) after others had moved. → **Always publish the next checkpoint tag** on the existing SHA.
- **Push update branch on no-op vs tag-only**: A branch pointing at the same commit as the current tag is empty vs main from a blueprint-diff perspective and invites a no-diff PR. `createAndCheckoutBranch` also refuses if `update/blueprint-v{next}` already exists on the remote — a no-op should not require that name to be free. → **Do not push (or need) the update branch** when there is no content delta.
- **PR on unchanged target**: Global `createPullRequest` still means “open a PR for targets that produced an update branch.” → **Skip PR** on no-ops. Do not record this as a PR-open **failure** warning; record it as an unchanged-content notice.
- **How clients learn about no-ops**: `warnings[]` already carries successful caveats and the update wizard already lists them. A structured per-result flag (e.g. content unchanged) is a **non-breaking** addition that lets UIs hide “merge this branch” CTAs. → **Do both**: keep a clear request-level warning **and** add an optional per-result unchanged indicator. Null/omit `updateBranchName` and `pullRequestWebUrl` when nothing was pushed; still return `checkpointTag` (`blueprint-v{next}`) and `commitHash` (existing SHA).
- **Where to detect cleanliness**: Manifest/route comparison cannot see lineage sidecar, Velocity output, or file-mode/content identity. → **Detect on the Git working tree after clean + render** (and after root descriptor/lineage when applicable).
- **git-utils** `commit` **vs new status API**: Catching the existing exception is brittle and treats success as failure until mapped. Changing `commit` to no-op would hide the decision from the use case and break callers that expect the throw. → **Add a cleanliness/status primitive in git-utils**; blueprint-server update Git port uses it. Leave `commit` unchanged.
- **Fail-fast**: Unchanged is **not** a Git failure. Later targets still run. Clone/push/tag-collision failures still stop the loop. Partial remotes may already have been mutated (unchanged ones may already have the next tag).
- **1→1 identical render**: Same policy. A user who re-applies the same content gets HTTP 200, next tag on the same SHA, no PR — not 400.
- **UI**: Out of scope. Existing wizard hides blank branch fields but still offers a “merge update branch” repo link when there is no PR URL. That CTA is **misleading** for no-ops. The structured unchanged flag is the hook for a later UI fix; this ticket does not change the wizard.

### Alternatives Considered

- **Keep failing (**`GitOperationException` **→ HTTP 400)**: Rejected. This is the production bug: one unchanged secondary remote aborts remotes that did change (and may already have been pushed).
- **Allow empty commits in git-utils**: Rejected. Empty history/PRs are poor UX; the throw is a shared library contract (registry ITs assert it).
- **Catch “No changes to commit” in the update Git port**: Rejected. Exceptions as control flow, message-coupled to git-utils wording.
- **Skip tag, branch, and result row for unchanged remotes**: Rejected. Breaks the cross-remote checkpoint contract for the next update; clients could not tell the key was processed.
- **Create empty commit but skip PR**: Rejected. Still pollutes history; still needs git-utils to allow empty commits.
- **Push update branch without a new commit (branch at current checkpoint) + skip PR**: Rejected. Remote branch clutter; collision with leftover `update/blueprint-v{next}` names; no merge value.
- **Pre-compare source files / routes to skip clone**: Rejected. Incomplete (lineage, templating, path copies). Clone + render is already required to know the truth.
- **Treat all-targets-unchanged as 400 “nothing to update”**: Rejected. The user asked to update; applying the next version identity (tags) is the outcome. Failing would reintroduce a polyrepo foot-gun when only the root (lineage) changed and secondaries did not — the mixed case must succeed; the all-noop case should too.
- **Server-side merge / skip Git entirely when hashes match**: Rejected. Checkpoints are the product strategy; identity of `{next}` on each remote is required.
- **Change instantiate**: Rejected. First apply is not an empty-commit problem.
- **UI in this ticket**: Rejected for implementation scope; call out the misleading merge CTA as a follow-up.

---

## Risk & Gap Analysis

### Requirement Ambiguities

- **Success vs skip semantics**: The requirement says the current error is wrong; it does not say whether an unchanged remote should still advance the checkpoint. **Resolved here:** yes — retag `blueprint-v{next}` on the existing SHA so a single current/next pair remains valid for all remotes.
- **Empty commit vs skip commit**: Not specified. **Resolved:** skip commit; do not allow empty commits.
- **PR / branch on no-op**: Not specified. **Resolved:** skip both; warn; do not pretend a branch exists on the remote.
- **1→1 identical content**: The story is framed as multi-repository. **Resolved:** same Git policy for every target, including a single remote, so behavior does not fork by topology.
- **How the client is told**: Not specified. **Resolved:** HTTP 200, `results[]` still includes the key, `warnings[]` explains the no-op, plus a non-breaking per-result unchanged indicator. No breaking DTO removals.
- **All targets unchanged**: Not specified. **Resolved:** still HTTP 200 (tags published, no branches/PRs).

### Edge Cases

- **Secondary polyrepo key unchanged, root changed (lineage/descriptor)**: The typical case. Root follows the full publish path; secondary retags only. Request succeeds.
- **Root unchanged as well** (`descriptorTemplatePath` blank and routed files identical): Root is also a no-op. Still tag next on the existing SHA.
- **Every target unchanged**: HTTP 200, N result rows, N no-op notices, no PRs, next tags pushed.
- **Parameters changed but they only affect another key’s templates**: Working-tree detection on this key is clean → no-op. Correct.
- **Next tag already exists** on an unchanged remote (retry after success): Still a **collision** / Git failure, same as today for changed remotes. No-op is not a license to overwrite tags.
- **Update branch leftover on remote** from a previous **changed** run of the same next version: Changed path still collides (existing rule). No-op path does not need that branch name, so it can succeed and still push the tag if the tag is free — if the tag also exists, collision.
- **Missing** `blueprint-v{current}`: Still fail at open-target; do not skip as “unchanged.”
- **Clean + render not byte-identical due to timestamps, generated lineage, or descriptor rewrite on root**: Treated as **changed** (commit + branch). That is correct.
- **User files on** `main`: Irrelevant to this detection. Comparison is vs the **pure checkpoint**, not vs the integration branch. Unchanged checkpoint vs next render can still differ from `main`; we simply do not open a no-diff blueprint PR.
- **Concurrent updates** racing tags: Unchanged; same as today.
- **Partial loop**: First target no-op (tag pushed), second target Git fails: request errors; first remote already has `blueprint-v{next}`. Same non-atomic reality as today’s partial push. Document; do not add distributed rollback.
- `createPullRequest` **true, mix of changed and unchanged**: PRs only for changed targets; unchanged get skip notices, not PR-failure warnings.
- **Two tags on one SHA**: Operators may wonder whether `{next}` “really applied.” The warning must state that content was identical and the next checkpoint reuses the existing commit.

### Technical Risks

- **git-utils gap**: No working-tree cleanliness API in 1.1.0. Mitigation: small additive git-utils API; bump blueprint-server dependency; do not weaken `commit`.
- **Checked-out commit SHA without a new branch**: Today the update port reads a SHA via a **branch** name after commit (`getHeadSha(File, String)`). A no-op may never create that branch. Mitigation: git-utils `getCheckedOutCommitSha(File)` for the commit currently checked out (detached HEAD after the checkpoint tag); stay behind the Git port.
- **Fail-fast vs no-op**: If cleanliness is checked **inside** `commitAll` and still thrown, the bug remains. Mitigation: the use case must branch **before** commit/push.
- **Non-atomic multi-repo**: Unchanged tags may land before a later Git failure. Mitigation: accept (already true for pushes); messages should name the target key.
- **UI merge CTA**: Wizard links to the repo to “merge the update branch” when there is no PR URL. For no-ops that branch was never pushed. Mitigation: structured unchanged flag; UI follow-up; backend still omits `updateBranchName` so the branch field hides.
- **Test blast radius**: Update controller ITs currently stub `commit` as always succeeding. Need a path where status is clean: mixed polyrepo (one dirty, one clean), all-clean 1→1, and assert no `commit` / no `pushBranch` / no `createPullRequest` on the clean remote, but `pushTag` of `blueprint-v{next}` still happens. Real Git-operation tests should cover tag-two-names-one-SHA if not fully mockable.
- **Lineage-only root change**: Easy to under-test; the mixed polyrepo fixture should keep descriptor/lineage on root so the root is dirty while a file-only secondary stays clean.

### Acceptance Criteria Coverage

The requirement does not number ACs; implied criteria:

| AC# | Description                                                                                                                                                      | Addressable? | Gaps/Notes                                                                                         |
| --- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------ | -------------------------------------------------------------------------------------------------- |
| 1   | When at least one mapped remote has no content delta vs its current checkpoint, update does **not** fail with “no changes to commit”                             | Yes          | Detection after clean + render; not a validation 400                                               |
| 2   | Remotes that **do** have a content delta still commit, tag next on the new SHA, push update branch + tag, and open a PR when the global flag is on               | Yes          | Unchanged policy must not alter the dirty path                                                     |
| 3   | Unchanged remotes still receive checkpoint tag `blueprint-v{next}` (same SHA as current checkpoint) so a later update can use one current/next pair for all keys | Yes          | Tag-only publish; no empty commit                                                                  |
| 4   | Unchanged remotes do **not** get an update branch push or a pull request                                                                                         | Yes          | Omit branch name and PR URL on that result row                                                     |
| 5   | The request still returns HTTP 200 with a `results[]` row per processed key, including no-ops                                                                    | Yes          | Continue the per-target loop; no-op is not fail-fast                                               |
| 6   | The client can tell which targets were unchanged                                                                                                                 | Yes          | Request-level warning **and** non-breaking per-result unchanged indicator                          |
| 7   | Same no-op policy for 1→1 / N→1 / 1→N / N→N                                                                                                                      | Yes          | Policy is per target, not per topology                                                             |
| 8   | Real Git failures (missing current tag, next-tag collision, push error) still fail the request and stop later targets                                            | Yes          | Do not classify those as unchanged                                                                 |
| 9   | git-utils `commit` still refuses empty commits for other callers                                                                                                 | Yes          | Additive status API only; no `--allow-empty`                                                       |
| 10  | Instantiate, registry, and UI implementation stay out of scope                                                                                                   | Partial      | Response must not break the existing wizard; misleading “merge branch” CTA is a known UI follow-up |

---
