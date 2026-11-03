# SPDD Analysis: Blueprint Repository Naming Conventions

> **Scope note:** This feature spans three repositories. `odm-platform-pp-blueprint-server` owns the manifest specification and its publish-time validation. `blindata-ui` owns the instantiation wizards, which create or select the physical repositories and, for now, are the only place where naming conventions are enforced. `blindata-site-docs` owns the public manifest and instantiation documentation. This analysis lives in the blueprint server because the convention is a manifest concept. Companion UI and docs prompts should reference it.

## Original Business Requirement

BDMD-5427

As a platform engineer,
I want to define the naming conventions for data product git repositories in Blueprints
so that when a data product developer instantiate a new Product from a Blueprint,
the data product repositories names follow a pre-defined pattern.

## Domain Concept Identification

### Existing Concepts (from codebase)

- **Blueprint / Blueprint Version**: A catalogued template and its published release. Each version stores its parsed manifest as JSON content, so anything declared in the manifest is versioned with the blueprint source. Blueprints are typed as `BLUEPRINT` (instantiable) or `MODULE` (composable child only).
- **Blueprint Manifest (`odm-blueprint-manifest`, spec 1.0.0)**: The platform engineer writes this contract in the blueprint Git repository. It is modelled in Java in the server's `manifest/model` package and mirrored in the UI `manifestSdk`. Both models keep unknown properties (`additionalProperties`), so an additive field survives parse and serialize even before it gets typed support. The spec states explicitly that *"Target repository creation, physical Git URLs, and repository creation policies are handled dynamically at runtime by the client orchestrator"*. Today the manifest says nothing about physical repository names.
- **Target Repository Declaration (`targetRepositories[]`: `key`, `description`, `isRoot`)**: A logical repository slot that the client must bind to a physical Git repository at instantiate time. Exactly one entry is the root. This is the natural home for a per-repository naming rule. `description` sets a precedent: the UI already uses it as the default description when it creates a repository for that key (BDMD-4820).
- **Manifest Parameters and reserved `dp*` keys**: Parameters declared by the blueprint, with type, default and validation (`pattern`, `min`/`max`, `allowedValues`). The UI treats `dpDomain`, `dpName`, `dpFqn`, `dpDisplayName`, `dpDescription`, `dpOwnerId` and `dpOwnerName` as reserved keys and auto-fills them, but only when the manifest declares them. `dpDomain`, `dpName`, `dpFqn`, `dpDisplayName` and `dpDescription` come from the selected data product. `dpOwnerId` and `dpOwnerName` come from the profile of the user running the wizard (`dpOwnerName` falls back to the data product display name). The parameters step hides reserved keys from the editable list. In the UI, a value the user entered wins over the auto-filled value, which wins over the manifest default. The server sees them as ordinary parameters and merges only two sources: the request value, else the manifest default.
- **Template Rendering (Velocity)**: The server renders only `*.vm` files and the descriptor template, using `${key}` references to parameters. The manifest snapshot copied into the root repository for lineage (`.odm/blueprint/blueprint-manifest.yaml`) is the stored version content, not rendered, so placeholders in it stay literal. Resolved parameter values reach lineage through the descriptor enrichment instead. Note that the manifest README (*Instantiation Workflow*, step 5) says the copy includes resolved parameter values, which does not match the code.
- **Instantiation Wizard (UI)**: There are two entry points with different step sequences:
  - **Blueprint detail modal**: *Pick Data Product* → *Configure Target Repositories* → *Insert Blueprint Parameters*. The blueprint version is fixed by the caller.
  - **Builder init-from-blueprint (BDMD-4629)**: *Pick Blueprint* (with a version picker) → *Repositories* → *Parameters*. The data product is passed in, so there is no data product step. Changing the version resets the parameters.
  
  Both reuse the same repositories step, parameters step, instantiation flow and per-key draft merge. Each key has its own create or select mode. In create mode the default repository name is hard-coded today: the root gets the data product `name` and each additional key gets `<dpName>-<key>`. The additional default is based on the data product name, not on whatever the user typed for the root. Defaults keep following the data product name until the user edits the field, and they are empty when the data product has no name. The field is free text with no pattern validation.
- **Repository Provisioning**: The UI creates repositories through the blueprint server's Git-provider endpoint (`POST /api/v2/pp/blueprint/git-providers/repositories`, provider and organization as query parameters, body `name`, `description`, `isPrivate`). It creates only keys whose repository has no `id` yet, one at a time and with no rollback. Next it registers the data product in the ODM registry. Then it calls instantiate with a list of `{ targetId, repository, branch }` entries, where `targetId` is the manifest key and `repository` is the provider `Repository` metadata, name included.
- **Instantiate Use Case (server)**: Validates in stages, all before any Git work: the command (stops at the first error), then the manifest, modules and module parameter resolution (each stage collects its issues with a how-to-fix hint and throws them together). It never creates repositories. It binds each `targetId` to a declared key.
- **Update Use Case (server)**: Applies a newer version's content to existing repositories. Its structure-freeze check compares repository keys, the root key, topology, root target routes and composition slots. It never looks at repository names or any other `targetRepositories[]` attribute besides `key` and `isRoot`.
- **Publish Validation (server)**: Structural manifest rules checked at publish and again before instantiate. Today the `targetRepositories[]` visit only checks the key. Precedent: a `MODULE` manifest with protected resources is rejected at publish because only the parent `BLUEPRINT` owns that concern. That rule lives in `PublishBlueprintVersion`, not in the manifest validation visitor, because the visitor does not know the blueprint type.
- **Physical repository uniqueness (BDMD-4820)**: The UI repositories step forbids binding the same physical repository to more than one key (identity is the provider `id`, or owner plus name for repositories still to be created). The server does not enforce it: the UI flow and the instantiate command only check that `targetId`s are unique.
- **Seeded manifest template (registration)**: When the UI registers a blueprint with a new repository, it seeds a manifest that already declares one `targetRepositories[]` entry (`main-repository`, root), its `instantiation` route and all seven reserved `dp*` parameters.
- **`BlueprintGitNamingConventions`**: An existing server class that holds checkpoint tag, update branch and orphan-branch naming. It is **not** about repository names. The shared vocabulary could confuse readers (see Technical Risks).
- **Public documentation and UI copy**: The `blindata-site-docs` pages *data-product-blueprints-develop* (manifest field reference) and *data-product-blueprints-instantiation* (wizard fields). They disagree today:
  - The develop page correctly says names default to the data product name, and to `<name>-<key>` for additional keys.
  - The instantiation page says the repository name *"defaults to the blueprint identifier; change it to match your naming convention"*, which is wrong.
  - The UI helper text under the name field in the instantiation create form is also wrong. It reuses the registration string *"Defaults to your blueprint identifier until you change it"* (`blueprintRegistrationModal.repositorySelection.repositoryNameHelper`), which is likely where the doc wording came from.
  - The develop page's field table describes `targetRepositories[].description` as guidance shown when picking a repository, while the same page and the code use it as the default description of a created repository.

### New Concepts Required

- **Repository Naming Convention**: An optional per-target-repository declaration of the pattern a physical repository name follows. The platform engineer authors it in the manifest, it is versioned with the blueprint, and it is evaluated once for each data product instantiation. It relates one-to-one to a `targetRepositories[]` key.
- **Naming Enforcement Mode**: Part of the convention, also declared in the manifest. `binding` means the wizard only accepts names that match the convention. `suggested` means the convention only pre-fills the name and the developer may change it.
- **Naming Variables**: The values a convention may reference. For now these are only the reserved data product parameters (`dpDomain`, `dpName`, `dpFqn`, `dpDisplayName`, `dpDescription`, `dpOwnerId`, `dpOwnerName`) that the same manifest declares. Other blueprint parameters are out of scope for now. Using the reserved parameters keeps one input model; the convention does not introduce a separate variable namespace.
- **Resolved Repository Name**: The concrete name obtained by applying a convention to the reserved values of one instantiation. It is derived in the wizard and never stored. The registry keeps the physical repository it ends up bound to.
- **Naming Conformance Check**: Verifies, case-sensitively, that a repository name equals the resolved name. It runs only in the UI wizards, and only for `binding` conventions. The server does not run it.

### Conceptual Relationships

- **Target repository key 1 — 0..1 Repository Naming Convention**: The convention qualifies one logical key, the same way `description` does. It does not exist apart from its key.
- **Repository Naming Convention 1 — 1 Naming Enforcement Mode**: Each convention carries its own mode, so one blueprint can bind the root name and only suggest an additional one.
- **Repository Naming Convention 1 — 0..N Naming Variables**: Each variable is a reference to a reserved `dp*` parameter declared in the same manifest. A parameter can be referenced by any number of conventions.
- **Repository Naming Convention + data product and user values → 1 Resolved Repository Name** per instantiation. The same convention yields different names for different data products.
- **Resolved Repository Name ↔ physical repository**: In create mode the resolved name is the name of the repository to create (fixed when `binding`, pre-filled when `suggested`). In select mode, a `binding` convention limits the choice to repositories whose name equals the resolved name; a `suggested` one does not limit it.
- **Ownership:** The platform engineer owns the convention and its mode (through the manifest in Git). The data product and the user running the wizard supply the values, and through them the resolved name, but not the pattern. The UI wizard owns enforcement. The server owns the validity of the declaration only.
- **Lifecycle:** A convention lives and changes only with a blueprint version. A resolved name is used once, at instantiation, and is not stored. After that, the physical repository is only known through the registry, and a later version's convention has no effect on it.

### Key Business Rules

- **Optional and backward compatible**: A key without a convention behaves exactly as today. Existing manifests and products are unaffected.
- **Authored in the blueprint**: The convention and its mode are defined by whoever publishes the blueprint version. Different versions of the same blueprint may declare different conventions or modes.
- **Mode is declared in the manifest**: Each convention says whether it is `binding` or `suggested`. When the mode is omitted, the convention is `binding`.
- **Binding conventions**: In create mode, the name is the resolved name and the developer cannot change it. In select-existing mode, the developer can only select a repository whose name conforms; non-conforming repositories cannot be selected.
- **Suggested conventions**: In create mode, the resolved name is pre-filled and the developer may change it. In select-existing mode, any repository can be selected.
- **Case-sensitive conformance**: A name conforms only if it is exactly equal to the resolved name, case included.
- **Repository name only**: A convention governs the repository name, not the owner, organization, group or project. Restricting where repositories are created is done through the Git provider settings.
- **Reserved data product variables only**: For now a convention may reference only reserved `dp*` keys, and only ones the same manifest declares as parameters (the UI only fills reserved keys that the manifest declares). Other parameters are out of scope for now. The declaration is checked at publish, with all violations reported together with hints.
- **At least one variable**: A pattern must reference at least one variable. A constant pattern is rejected at publish, because every instantiation after the first would collide.
- **Resolution matches the submitted values**: The wizard resolves each variable with the same reserved-value logic it uses to fill the reserved parameters it submits, so the repository name and the rendered templates see the same value. A variable with an empty value makes the convention unresolvable for that instantiation: the wizard names the variable and the repository key, and blocks the key when the convention is `binding`.
- **Enforced in the UI wizards only (for now)**: The server stores the convention with the version and validates the declaration at publish, but instantiate never checks repository names. API clients that call instantiate directly are not constrained.
- **Instantiation-time only**: Conventions apply only when a new data product is instantiated. They are never applied retroactively. Updating a product to a version with a different convention must not validate, block or rename its existing repositories. A convention change is not a structure change for the update freeze.
- **Equal treatment of root and additional repositories**: Every key may declare its own convention and mode.
- **Distinct physical repositories**: The existing wizard rule that one physical repository cannot be bound to more than one key also applies to derived names.
- **Lineage**: The manifest copy in the root repository keeps the convention as authored, not resolved.
- **Modules do not own naming**: A catalog `MODULE` never decides physical repositories, because routing belongs to the parent. A `MODULE` manifest that declares a convention is rejected at publish, consistent with the protected-resources precedent (and checked in the same place, `PublishBlueprintVersion`).

## Strategic Approach

### Solution Direction

- **Extend the manifest specification** with an optional, structured naming-convention attribute on each `targetRepositories[]` entry, carrying a pattern and an enforcement mode. Its placeholder syntax is the same `${parameterKey}` reference already used in Velocity templates, so blueprint authors keep one mental model. Illustrative shape (field names to be fixed in the prompt):

  ```yaml
  targetRepositories:
    - key: main-repository
      isRoot: true
      naming:
        pattern: "${dpDomain}-${dpName}"
        enforcement: binding   # or: suggested; defaults to binding when omitted
  ```

- **Blueprint server, which owns the specification but does not enforce names:**
  - Give the attribute typed support in the manifest model and the visitors.
  - Add publish-time structural validation in the manifest validation visitor: pattern syntax, only reserved `dp*` keys that the manifest declares as parameters, a valid enforcement value, at least one placeholder per pattern, no two keys with textually identical patterns. These rules also run when the manifest is re-validated before instantiate, which checks the declaration, not repository names.
  - Reject conventions on `MODULE` manifests in `PublishBlueprintVersion`, next to the protected-resources rule, because that check needs the blueprint type.
  - Document the attribute, the reserved variable list, the modes and shared resolution examples in the manifest README. Also fix the README *Instantiation Workflow* step 5, which says the lineage copy contains resolved parameter values.
  - Leave the instantiate and update use cases unchanged: no conformance check, no distinctness check.
- **UI, which is the orchestrator and the only enforcer:**
  - Teach the manifest SDK the new attribute and a resolution helper that uses the same reserved-value logic as the parameters it submits.
  - In create mode, the repositories step derives each key's name from the convention, replacing today's hard-coded `dpName` and `<dpName>-<key>` defaults. The name is read-only for `binding` and editable for `suggested`.
  - In select-existing mode, a `binding` convention lets the developer pick only conforming repositories (non-conforming ones are disabled or filtered, with the reason shown). A `suggested` convention does not restrict the choice.
  - Gate the step on conformance, so nothing is created when a `binding` key does not conform or cannot be resolved.
  - Apply the same behaviour on both entry points: the Blueprint detail modal and Builder init-from-blueprint.
  - Keep the existing step order. The reserved values are known before the repositories step on both entry points: the data product is picked first in the detail modal and passed in by the Builder, and the owner values come from the user profile.
  - Replace the instantiation name-field helper text, which today reuses the registration string ("Defaults to your blueprint identifier…"), with instantiation-specific copy that explains convention-driven names.
  - Add an illustrative convention to the seeded registration manifest template.
- **Docs**: Document the new manifest attribute, its variables, modes, rules and examples on the *develop* page, and align its `targetRepositories[].description` field description with the actual behaviour (default description of a created repository). Update the *instantiation* page to describe convention-driven names in both modes and fix the existing "defaults to the blueprint identifier" inaccuracy. State that conventions are enforced by the wizard, not by the API.
- **Unchanged**: The instantiate and update use cases, the registry, Git rendering, checkpoint naming, the wizard step order, and the Builder repository configuration modal used without a blueprint.

### Key Design Decisions

- **Where the convention lives (manifest vs Blueprint entity vs platform-wide setting)**: A platform-wide setting cannot vary per blueprint, while different blueprints produce different kinds of product. A Blueprint entity attribute needs a DB migration and is not versioned with the template. The manifest is versioned and co-located with the key and description it qualifies. It is already authored in Git by the platform engineer, already reaches the UI with the blueprint version, and is preserved in lineage. → **Manifest, per `targetRepositories[]` entry.**
- **Pattern form (generative template vs validation regex vs both)**: A regex allows flexibility but cannot propose a name, so the developer still has to invent one. A template produces a deterministic name with zero effort and makes conformance a simple equality check. → **Generative template.** The attribute is a structured object rather than a bare string, so it carries the enforcement mode now, and a validation pattern or other options can be added later without breaking the spec.
- **Template grammar (full Velocity vs restricted `${key}` substitution)**: Full Velocity cannot be evaluated in the browser, and directives or methods make names unpredictable. A restricted subset of placeholders and literal text can be parsed identically in Java and JavaScript and still looks like Velocity to authors. → **Restricted placeholder substitution, with no directives, methods or filters in v1.**
- **Enforcement strength (default suggestion vs binding rule)**: Platforms differ: some need names to follow the pattern, others only want a sensible default. → **Configured per convention in the manifest** (`binding` or `suggested`), **defaulting to `binding`** because the story asks for names that "follow a pre-defined pattern"; authors opt out with `suggested`. *Decided with product.*
- **Enforcement location (UI wizards vs server)**: The manifest architecture already delegates repository creation and selection to the client orchestrator, and the wizards are where names are chosen. Server enforcement would also need a server-side resolver and a reserved-value source. → **UI wizards only, for now, guided by the manifest content.** The server validates the declaration at publish but does not check names at instantiate. Accepted consequence: API clients are not constrained. A server-side check can be added later without changing the spec. *Decided with product.*
- **Variable scope (reserved `dp*` keys vs any declared parameter)**: Any declared parameter would let engineers use blueprint-specific inputs, but those values are only entered after the repositories step, so the wizard would have to be reordered. → **Reserved `dp*` keys only, for now.** This keeps today's step order on both entry points. *Decided with product.*
- **Select-existing mode**: → **Follows the enforcement mode.** `binding` allows only conforming repositories; `suggested` allows any. Under `binding` this still supports the BDMD-4820 manual-retry scenario, because repositories created by a failed earlier attempt already conform. *Decided with product.*
- **Case sensitivity**: → **Case-sensitive equality.** *Decided with product.*
- **What the convention governs**: → **The repository name only.** Organization or namespace restrictions are handled through the Git provider settings. *Decided with product.*
- **Invalid characters and provider limits (auto-slugify vs fail explicitly)**: Silent transformation (lowercasing, replacing spaces) has to match the provider's rules, and it hides surprises, especially with case-sensitive conformance. → **No implicit transformation in v1.** The wizard fails with an explicit message. The docs tell authors which `dp*` values are safe to use in names.
- **Spec versioning**: The field is additive and optional, and both parsers tolerate unknown fields. → **Keep `specVersion: 1.0.0`.**
- **Terminology**: Use a distinct domain term such as *repository naming convention* or *repository name template*. Do not extend `BlueprintGitNamingConventions`, which governs checkpoint tags and branches.

### Alternatives Considered

- **Platform-wide (tenant) naming convention in Blindata settings**: Rejected. The story places the convention "in Blueprints", it cannot vary by blueprint, and it would need a new settings surface and API.
- **Convention as a Blueprint entity attribute set at registration**: Rejected. It is not versioned, sits outside the Git-authored manifest, needs a schema migration, and is missing from the lineage copy.
- **Server-authoritative enforcement at instantiate**: Deferred. It would close the API-client gap, but it needs a server resolver for the reserved values and a distinctness check, and product chose wizard-only enforcement for now.
- **Any declared parameter as a variable, with the wizard reordered to put parameters before repositories**: Deferred. More expressive, but it changes the step order on both entry points and is out of scope for now.
- **A single fixed enforcement mode for all conventions**: Rejected in favour of a mode declared per convention.
- **Moving repository creation into the instantiate endpoint**: Rejected. The manifest architecture deliberately delegates creation to the client orchestrator, and that change would be much larger than this story.
- **Full Velocity evaluation of names**: Rejected. It cannot run in the browser and allows non-deterministic or multi-line output.
- **Regex-only convention**: Rejected for v1 because it cannot pre-fill names. Kept as a possible future complement inside the structured attribute.
- **Automatic slugification of resolved names**: Rejected for v1 because of the risk of divergence from provider rules.

## Risk & Gap Analysis

### Resolved Requirement Decisions

- **Binding vs suggested**: Configured in the manifest for each convention, defaulting to `binding` when omitted. When `binding`, the developer cannot change the name.
- **What "name" covers**: The repository name only. Organization restrictions are handled through the Git provider settings.
- **Select-existing repositories**: Under `binding` they must conform, and non-conforming repositories cannot be selected. Under `suggested` they do not have to conform.
- **Variable scope**: Only the reserved data product parameters, for now. Other parameters are out of scope.
- **Authoring surface**: Editing the manifest in Git, plus an illustrative entry in the seeded registration template. No UI editor.
- **Constant patterns**: A pattern with no placeholders is rejected at publish.
- **Builder surfaces**: Builder init-from-blueprint is in scope. The Builder repository configuration modal used without a blueprint is out of scope.
- **Case sensitivity**: Conformance is case-sensitive.
- **Enforcement location**: UI wizards only, guided by the manifest, for now. No server-side enforcement.

### Open Questions

- **No explicit ACs**: The story ships without acceptance criteria. The derived ACs below should be validated with the PO.

### Accepted Boundaries and Deferred Concerns

- **API clients are not constrained**: Instantiate does not check names, so a client that bypasses the wizards can bind any repository.
- **Non-reserved parameters as variables**: Deferred; it would require reordering the wizard steps.
- **Organization or namespace conventions**: Handled through the Git provider settings, not by this feature.
- **Provider renames after creation**: If a provider normalizes the requested name, the created repository may not equal the resolved name. Nothing re-checks it after creation.

### Edge Cases

- **A referenced reserved value is empty for this instantiation** (for example a data product with no domain): The convention cannot be resolved. Under `binding` the key is blocked with a message naming the variable and the key. Under `suggested` no name is pre-filled and the developer types one.
- **A convention references an undeclared or non-reserved parameter**: Rejected at publish.
- **A value contains characters a provider rejects** (spaces, `&`), for example a domain like *Sales & Marketing*: Repository creation fails. Under `binding` the developer cannot fix it by editing the name; the data product attribute or the blueprint has to change. The message must say so.
- **Resolved name exceeds provider length limits**: Needs a clear error, with the same consequence under `binding`.
- **Two keys resolve to the same name** even though their patterns differ: The existing wizard duplicate-repository check catches it. Under `binding` neither name can be edited, so the blueprint has to change. Publish rejects textually identical patterns, which covers the common case.
- **Convention without placeholders** (a constant name): It would make every instantiation collide from the second product on, so it is rejected at publish, whatever the mode.
- **Resolved name already exists on the provider**: Create fails, so the developer switches that key to *select existing*. Under `binding` this works only when the existing repository's name equals the resolved name exactly. This also covers the BDMD-4820 manual-retry flow.
- **Existing repository differs only by case** (GitHub treats names as case-insensitive for uniqueness): Create fails because the name is taken, and under `binding` the existing repository does not conform, so the key is blocked. The message must explain the case mismatch.
- **Convention references `dpOwnerId` or `dpOwnerName`**: These come from the profile of the user running the wizard, so two developers instantiating for the same data product get different names. Allowed, but the docs should call it out.
- **Developer picks a different data product after names were derived** (detail modal, going back to step 1): Names for keys not yet created must be re-derived. Under `binding`, already-selected repositories must be re-checked.
- **Blueprint version changes inside the wizard** (Builder entry point only; the detail modal has a fixed version): The convention or mode may differ, so names must be re-derived for keys that persist (aligned with the existing per-key draft merge on version change).
- **Update to a version whose convention differs**: Must not block the update or rename repositories.
- **A `MODULE` declaring a convention**: Rejected at publish. A parent composing modules uses only its own `targetRepositories[]`.
- **Old manifests (no convention)**: Today's hard-coded defaults remain.

### Technical Risks

- **Two parsers of the grammar (Java validates, JavaScript resolves)**: They can disagree on what is a valid placeholder. Mitigation: keep the grammar minimal and share a set of examples, used as test vectors, between the spec README, server tests and UI tests.
- **Reserved key list duplicated**: Today the reserved `dp*` list exists only in the UI (`manifestSdk/reservedParameterKeys.js`). Publish validation needs it on the server too. Mitigation: define the list in the manifest spec README and test both sides against it.
- **Provider name identity**: GitLab distinguishes the project *name* from its *path*, Bitbucket uses a lowercase *slug*, and Azure DevOps allows spaces. With case-sensitive conformance, comparing the wrong identifier in select mode produces false mismatches. Mitigation: define per provider which identifier the wizard compares, and check what git-utils `Repository.name` carries for each provider. Not yet verified: on create, every git-utils provider sends `Repository.name` as the requested name, but how each provider maps its response (GitLab `path`, Bitbucket `slug`) back into `Repository.name` was not confirmed during analysis.
- **Partial provisioning**: The UI creates repositories one at a time with no rollback, then registers the data product, then calls instantiate. Naming adds no new server rejection, so it does not add orphan risk, as long as the wizard checks every `binding` key before creating anything.
- **Repositories-step state**: The step now reacts to the convention, the mode and changes of data product or version, on two entry points, on top of BDMD-4820 workspace state (draft merge on version change, validation gating). Mitigation: extend the existing repositories-step and state tests.
- **Terminology collision with `BlueprintGitNamingConventions`**: This could confuse implementers or lead them to extend the wrong class. Mitigation: use a distinct concept name in the prompt.
- **Publish-time validation cost**: Negligible, because it is structural only and does no Git access.
- **Documentation drift**: The instantiation docs page and the UI helper text already misstate the default name, the develop page contradicts itself on `targetRepositories[].description`, and the manifest README misstates the lineage copy. Mitigation: include the docs, UI copy and README fixes in scope.

### Acceptance Criteria Coverage

The requirement has no explicit ACs. The following are derived from the story and the product decisions above, and must be confirmed.

| AC# | Description | Addressable? | Gaps/Notes |
|-----|-------------|--------------|------------|
| 1 | A platform engineer can declare a naming convention, with a `binding` or `suggested` mode, for each target repository of a blueprint | Yes | Optional structured attribute on `targetRepositories[]`, authored in the Git manifest. The mode defaults to `binding` when omitted. |
| 2 | Invalid conventions (syntax errors, no placeholders, non-reserved or undeclared variables, invalid mode, identical patterns, use in a `MODULE`) are rejected at publish, with all issues and hints | Yes | Manifest validation visitor, plus `PublishBlueprintVersion` for the `MODULE` rule. |
| 3 | In both instantiation wizards, create-mode repository names are derived from the convention using the reserved data product values | Yes | Blueprint detail modal and Builder init-from-blueprint. Step order unchanged. |
| 4 | Under `binding`, the developer cannot change the derived name; under `suggested`, the name is pre-filled and editable | Yes | |
| 5 | Under `binding`, only repositories whose name conforms can be selected; under `suggested`, any repository can be selected | Yes | Supports the BDMD-4820 retry scenario. |
| 6 | Conformance is case-sensitive | Yes | Depends on comparing the right provider identifier. |
| 7 | Blueprints without conventions and existing data products behave as today; updates are never blocked by conventions | Yes | Additive optional field; the update freeze reads only `key` and `isRoot` from `targetRepositories[]`. |
| 8 | Public documentation and UI copy describe the convention, its modes and the instantiation behaviour | Yes | Develop and instantiation pages in `blindata-site-docs`, the instantiation name-field helper text, and the manifest README. |
| 9 | Instantiate requests from API clients are checked against conventions | No | Out of scope for now: enforcement is in the UI wizards only. |
| 10 | Conventions can use blueprint parameters other than the reserved `dp*` keys | No | Out of scope for now. |
| 11 | Naming of owner, organization or group follows a convention | No | Handled through the Git provider settings. |
