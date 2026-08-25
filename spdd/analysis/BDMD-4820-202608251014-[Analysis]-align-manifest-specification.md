# SPDD Analysis: Align Code to Updated Blueprint Manifest Specification

## Original Business Requirement

BDMD-4820 I have updated the manifest specification, now I want to align the existing code to that specification.
Projects involved: blueprint service, blindata ui

In scope: update code to support the new specificaiton.
Out of scope: add new features not yet implemented.

---

The authoritative updated specification (from `odm-platform-pp-blueprint-server/src/main/java/org/opendatamesh/platform/pp/blueprint/manifest/README.md`) follows verbatim:

# Software Requirements Specification: Blueprint Manifest

<!-- TOC -->

- [Software Requirements Specification: Blueprint Manifest](#software-requirements-specification-blueprint-manifest)
  - [Initial Context](#initial-context)
    - [1. Overview and Purpose](#1-overview-and-purpose)
    - [2. Format Recommendation: YAML](#2-format-recommendation-yaml)
    - [3. Core Responsibilities](#3-core-responsibilities)
      - [3.1. Parameter Management](#31-parameter-management)
      - [3.2. Resource Protection](#32-resource-protection)
      - [3.3. Blueprint Composition (Modularity)](#33-blueprint-composition-modularity)
      - [3.4. Instantiation Strategy](#34-instantiation-strategy)
      - [3.5. Versioning Control](#35-versioning-control)
    - [4. Instantiation Workflow](#4-instantiation-workflow)
  - [Specification](#specification)
    - [1. Specification Definition with Schema](#1-specification-definition-with-schema)
      - [Core Schema Objects](#core-schema-objects)
      - [Validation constraints](#validation-constraints)
      - [UI metadata (`parameters[].ui`)](#ui-metadata-parametersui)
    - [2. Manifest Examples](#2-manifest-examples)
      - [2.1. Monorepo, no composition](#21-monorepo-no-composition)
      - [2.2. Monorepo + composition](#22-monorepo--composition)
      - [2.3. Polyrepo, no composition](#23-polyrepo-no-composition)
      - [2.4. Polyrepo + composition](#24-polyrepo--composition)
  - [Java API](#java-api)
    - [Using the parser](#using-the-parser)
    - [Extending the specification](#extending-the-specification)

<!-- TOC -->

## Initial Context

### 1. Overview and Purpose

The **Blueprint Manifest** is a core configuration file residing within a Blueprint repository.
A Blueprint is defined as a Git repository containing parameterized templates and infrastructure-as-code (IaC) files
necessary to provision a Data Product within a Data Mesh architecture (e.g., databases, S3 buckets, virtual machines).

The primary purpose of the manifest is to orchestrate the instantiation of the Data Product by defining required
parameters, protecting specific resources, managing blueprint composition, and declaring logical target routing.

> **Architectural Note**: The location of the Data Product descriptor template within the repository is intentionally
> not specified or tracked by this manifest. Instead, its location is registered and stored within the Platform's
> internal
> Blueprint model. This ensures the manifest remains strictly focused on provisioning and infrastructure orchestration.

> **Architectural Note**: Target repository creation, physical Git URLs, and repository creation policies are handled
> dynamically at runtime by the client orchestrator. The manifest abstracts target locations using logical repository
> keys (`instantiation.repositories[].key`). Created or pre-existing repositories are supplied to the instantiate endpoint,
> each mapped to a repository key.

A copy of this manifest is retained in the **root target repository** (the primary data product repository designated
at instantiation time) to maintain lineage and trace the original Blueprint used. Module and secondary target
repositories do not receive a manifest copy.

### 2. Format Recommendation: YAML

**Recommendation:** **YAML** is strongly recommended over JSON for this specific use case.

**Rationale:**

- **Human Readability & Editability:** YAML is significantly easier for developers and data engineers to read and write.
  It is the industry standard for infrastructure configuration (e.g., Kubernetes, Ansible, GitHub Actions).
- **Comments:** YAML supports comments (`#`), which is critical for documenting what specific parameters do, providing
  examples, or leaving instructions for users. JSON does not natively support comments.
- **Multi-line Strings:** YAML handles multi-line strings gracefully, which is highly beneficial if parameters require
  passing scripts or complex descriptions.

### 3. Core Responsibilities

#### 3.1. Parameter Management

The manifest must explicitly declare all parameters required to successfully instantiate the Blueprint templates.

- It must define the parameter keys, expected data types, and any default values or validation rules.
- These parameters will act as variables that are injected and resolved within the Blueprint files during the
  instantiation process.
- **UI/UX Presentation:** The specification must support presentation metadata for parameters to drive collection UIs
  during instantiation. The `parameters[].ui` object carries this metadata; see
  [UI metadata (`parameters[].ui`)](#ui-metadata-parametersui) for supported fields and how to apply them.
  - **Required parameters:** Declare mandatory inputs with `required` on the parameter. Treat `required` as binding for
    validation and mark the corresponding control as required in the collection UI.

#### 3.2. Resource Protection

The manifest must define a list of **Protected Resources** (specific files, directories, or paths).

- Once a Blueprint is instantiated, the resources defined in this list are marked as read-only or immutable in the
  context of future updates.
- This ensures that critical infrastructure definitions or core scaffolding cannot be accidentally modified or
  overwritten by developers working in the target repository.

#### 3.3. Blueprint Composition (Modularity)

To support DRY (Don't Repeat Yourself) principles, the manifest must support Blueprint Composition.

- **Parent-Child Relationship:** The current manifest acts as the "Parent" and can reference other remote Blueprint
  repositories ("Children" or "Modules").
- **Parameter Passing:** Similar to Terraform modules, the manifest must explicitly map and pass the required parameters
  down to the referenced child Blueprints to ensure successful downstream instantiation.
- **Co-located Routing & Mapping:** Child modules explicitly define their blueprint source, parameter mappings, and
  routing rules (`targets[]` with `sourcePath`, `repository`, and `path`) in a single `composition[]` entry.

#### 3.4. Instantiation Strategy

The manifest abstracts target destinations using logical repository identifiers declared in
`instantiation.repositories`.

- **Monorepo Topology:** All generated output maps to a single repository key.
- **Polyrepo Topology:** Root contents and composed child modules are distributed across multiple repository keys.
- **Uniform routing:** Both `instantiation.root.targets[]` and `composition[].targets[]` use the same route shape
  (`sourcePath` → `repository` + `path`). Root `sourcePath` is relative to the parent blueprint repository; module
  `sourcePath` is relative to the child blueprint repository.
- **Path Splitting:** Multiple entries in a `targets[]` list route different source subdirectories to separate
  repository keys and paths.

#### 3.5. Versioning Control

To ensure stability, backward compatibility, and reliable lineage, the manifest must handle versioning at two distinct
levels:

- **Specification Versioning:** The manifest must declare the schema version of the manifest file itself (e.g.,
  `specVersion: 1.0.0`). This allows the orchestrating system to parse the file correctly and supports future iterations
  or
  breaking changes to the manifest schema.
- **Blueprint Versioning:** The manifest must declare the release version of the specific Blueprint it represents (e.g.,
  `version: 1.2.0`, adhering to Semantic Versioning). This allows users to instantiate specific, stable releases of a
  Blueprint and safely upgrade their Data Products over time.

### 4. Instantiation Workflow

The system orchestrating the Blueprint must support the following lifecycle:

1. **Selection:** The user selects a specific Blueprint and version for their Data Product.
2. **Target Resolution:** The client orchestrator resolves each logical repository key declared in
   `instantiation.repositories` to an actual Git repository. Repository creation policies (`create_if_missing`,
   `must_exist`, and so on) are enforced **outside** the manifest; the orchestrator creates or selects repositories and
   passes them to the instantiate endpoint, each entry mapped to a repository key (`targetId`).
3. **Configuration:** The user provides values for all parameters declared in the Blueprint Manifest (facilitated by the
   UI/UX metadata).
4. **Generation & Copy:** The system parses `instantiation.root.targets` and `composition[].targets`, extracts files from
   the declared source directories, resolves parameter variables, and writes generated code into the mapped target
   repositories and paths.
5. **Lineage Preservation:** A version of the manifest, complete with the resolved parameter values and versioning
   metadata, is copied **only into the root target repository** designated at instantiation time—not into module or
   secondary target repositories.

## Specification

### 1. Specification Definition with Schema

The Blueprint Manifest represents the authoritative contract for a **Data Product template**. The schema is strictly
typed to ensure predictable parsing and validation by the orchestrating engine, while offering extensibility for custom
integrations.

#### Core Schema Objects

- `spec` (String, Required): The specification name, must be set to `odm-blueprint-manifest`.
- `specVersion` (String, Required): The version of the manifest schema itself (e.g., `1.0.0`). The orchestrator uses
  this
  to determine how to parse the file.
- `name` (String, Required): The machine-readable identifier of the blueprint.
- `displayName`(String, Optional): The human-readable identifier of the blueprint.
- `version` (String, Required): Semantic version of the blueprint release (e.g., `1.2.0`).
- `description` (String, Optional): Human-readable summary of the blueprint's purpose.
- `parameters` (Array of Objects, Optional): Defines the inputs required from the user during instantiation.
  - `key` (String, Required): The variable name to be injected into templates.
  - `type` (Enum: `string`, `integer`, `boolean`, `array`, `object`, Optional - defaults to `string`): Data type for
    backend parsing and structure validation.
  - `required` (Boolean, Optional - defaults to `false`): Whether the user must provide a value.
  - `default` (Any, Optional): A fallback value if none is provided. Must match the declared `type`.
  - `validation` (Object, Optional): Defines strict constraints to evaluate the provided value before instantiation.
    - `allowedValues` (Array, Optional): For `string` (and optionally other scalar types), the value must equal one
      of the listed entries.
    - `format` (String, Optional): Well-known string formats (e.g., `hostname`, `uri`, `email`) when the
      orchestrator implements them; semantics align with common JSON Schema string formats where applicable.
    - [hint, not supported for now]`schemaRef` ~~(String, Optional): URI of a machine-readable schema (e.g., JSON
      Schema) that the value must satisfy; the orchestrator resolves and applies it if supported.~~
    - `pattern` (String, Optional): A Regular Expression (Regex) the value must match (primarily for `string`
      types).
    - `min` (Number, Optional): Minimum numeric value, or minimum length/item count for strings and arrays.
    - `max` (Number, Optional): Maximum numeric value, or maximum length/item count for strings and arrays.
  - `ui` (Object, Optional): Presentation metadata for dynamic forms. Standard fields (all optional strings unless
    noted) are `group`, `label`, `description`, and `formType`. The parser preserves any additional keys on this
    object
    for forward compatibility. See [UI metadata (`parameters[].ui`)](#ui-metadata-parametersui) for how to use them.
- `protectedResources` (Array of Objects, Optional): Files, directories, or globs marked immutable after initial
  generation. Each item:
  - `path` (String, Required): Path relative to the repository root, or a glob (e.g., `infrastructure/`\*).
  - `integrity` (Object, Optional): Cryptographic digest for tamper detection. **Omitted** in the **source** Blueprint
    manifest; **populated** on the manifest copy stored in the instantiated Data Product repository (for concrete
    files, or per platform rules for globs/directories). When present:
    - `algorithm` (String, Required): Hash algorithm identifier (e.g., `sha256`).
    - `value` (String, Required): Lowercase hex-encoded digest of the protected content at instantiation time.
- `composition` (Array of Objects, Optional): Defines child blueprints (modules) to be instantiated alongside the
  parent.
    - `module` (String, Required): A logical alias for the child module.
    - `blueprintName` (String, Required): The identifier of the child blueprint.
    - `blueprintVersion` (String, Required): The target release version of the child blueprint.
    - `parameterMapping` (Object, Optional): Maps **child** parameter keys to **values** supplied at instantiation. Each
      value is either a **literal** (string, number, boolean) or a **reference** to a parent parameter by key (the
      orchestrator resolves references from the parent parameter set). This is the manifest analogue of Terraform’s
      explicit `module "x" { ... }` variable passing: only declared inputs are passed—there is no implicit global scope.
      Nested expressions (e.g., string concatenation) are out of scope; if a value must be derived, expose it as a
      parent parameter.
    - `targets` (Array of Objects, Required): Routes subdirectories of the **child blueprint repository** to destination
      repositories. Same shape as `instantiation.root.targets[]`. May contain multiple entries for path splitting.
      - `sourcePath` (String, Optional — defaults to `./`): Directory path relative to the child blueprint repository
        root.
      - `repository` (String, Required): Must match an entry in `instantiation.repositories[].key`.
      - `path` (String, Optional — defaults to `./`): Directory path relative to the destination repository root.
- `instantiation` (Object, Required): Defines logical repository keys and routes parent blueprint contents to mapped
  destinations.
  - `repositories` (Array of Objects, Required): Abstract repository keys the client must resolve at instantiation time.
    - `key` (String, Required): Unique logical alias referenced by `root.targets` and `composition[].targets` (e.g.,
      `main`, `infra-repo`).
    - `description` (String, Optional): Human-readable guidance for UI selection.
  - `root` (Object, Required): Routes subdirectories of the **parent blueprint repository** to destination repositories.
    - `targets` (Array of Objects, Required): May be an **empty array** for pure orchestration parents that delegate all
      output to composed modules. Same shape as `composition[].targets[]`.
      - `sourcePath` (String, Optional — defaults to `./`): Directory path relative to the parent blueprint repository
        root.
      - `repository` (String, Required): Must match an entry in `instantiation.repositories[].key`.
      - `path` (String, Optional — defaults to `./`): Directory path relative to the destination repository root.

#### Validation constraints

The orchestrator must enforce the following rules when validating a manifest:

- `instantiation.repositories[].key` values must be **unique**.
- `composition[].module` values must be **unique** (when `composition` is present).
- Every `repository` reference in `instantiation.root.targets[]` and `composition[].targets[]` must match an existing
  `instantiation.repositories[].key`.
- When a `targets` array contains **more than one** entry, each entry must declare an explicit `sourcePath` (the `./`
  default must not be relied upon implicitly, to avoid accidental duplication of the entire source repository).
- Repository paths must be **relative** to the repository root; absolute paths and path traversal segments (`..`) are
  rejected.
- Path normalization (leading `./`, trailing slashes) is implementation-defined but must be applied consistently.

#### UI metadata (`parameters[].ui`)

Optional hints for tools that collect parameter values (web UI, CLI wizard, IDE plugin, and so on).

**Authors:** Add `ui` to steer grouping, labels, help text, and control style. Limit the block to the four standard
string fields unless a target client documents additional keys.

**Clients:** Support `group`, `label`, `description`, and `formType`. Bind each field to widgets and layout in the
collection flow; keep the same manifest behaving the same way across product releases. Preserve unknown keys on round-trip
when the tool reads and writes manifests; drop or ignore extra properties only if the product definition says so.

##### Fields

| Field         | Purpose                                                                                                                                                                                                                                                                            |
| ------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `group`       | **Authors:** Split the form into sections with a `/`-separated path (trim segments; ignore extra spaces around `/`). Example: `Networking / Firewall`. Leave empty or omit to mark the parameter as ungrouped. **Clients:** List ungrouped parameters after every grouped section. |
| `label`       | **Authors:** Set a short title for the field. **Clients:** Show `label` when present; display `key` when `label` is absent.                                                                                                                                                        |
| `description` | **Authors:** Add helper text for the field. **Clients:** Show it as tooltip, caption, or inline help next to the control.                                                                                                                                                          |
| `formType`    | **Authors:** Suggest a control style; combine with `type` and `validation` (see below). **Clients:** Interpret `formType` together with `type` and `validation` when picking a control.                                                                                            |

##### Grouping

**Authors:** Use `group` to mirror the user’s task (wizard steps, accordions, columns). Treat each path segment as one
level of nesting.

**Clients:** Build nested sections from the path. Pick one ordering rule—manifest order, alphabetical segment order, or
fixed platform order—and apply it to sibling groups and to fields inside each group for every run.

##### Relating `type`, `validation`, and `formType`

**Clients:** Pick a single control per parameter that collects values compatible with `type` and `validation`.

**Authors:** Set `type` first, add `validation` constraints, then set `ui.formType` to one of the supported values for
that `type` (or omit it and rely on defaults described below).

| Parameter `type` | Supported `formType` values                                                                                                                                                                                        | Use `validation` for                                                                                                                                               |
| ---------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `string`         | `text` — single-line input (default when omitting `formType`). `textarea` — multi-line input. `dropdown` — fixed choices; omit `formType` when `validation.allowedValues` is set and a choice control is intended. | `allowedValues` (enforce choice list), `pattern` (regex), `format` (e.g. `textarea` / `multiline` with `textarea`-style `formType`), `min` / `max` (string length) |
| `integer`        | `number` — numeric input (default when omitting `formType`). `text` — free-form field; still coerce to integer before submit.                                                                                      | `min`, `max`                                                                                                                                                       |
| `boolean`        | `checkbox`, `switch` — two-state controls (omit `formType` to use the same semantics with a default style).                                                                                                        | —                                                                                                                                                                  |
| `array`          | `json` — structured editor or JSON text for arbitrary array content (default when omitting `formType`). `stringList` — row or line-based editor for an array of strings.                                           | `min` / `max` (item count or length per product rules)                                                                                                             |
| `object`         | `json` — structured editor or JSON text (default when omitting `formType`).                                                                                                                                        | `min` / `max` (property count per product rules, if used)                                                                                                          |

**Authors:** Avoid contradictory combinations (for example `type: boolean` with `formType: dropdown` unless
`validation.allowedValues` defines the choices). **Clients:** Reject ambiguous manifests at validation time or apply a
deterministic fallback and warn the user.

**Non-standard `formType` strings** (`tags`, `password`, `json-editor`, vendor-specific names): **Authors:** Prefer the
table above for portability. **Clients:** Map them to the closest control in the same row, treat as plain text, or omit;
record that mapping in product documentation.

---

### 2. Manifest Examples

All examples use `spec: odm-blueprint-manifest` and `specVersion: 1.0.0`. They are **source** Blueprint manifests unless
noted. Parameter lists are abbreviated; real manifests would declare every input the templates need.

#### 2.1. Monorepo, no composition

Single target repository; single root directory mapping.

```yaml
spec: odm-blueprint-manifest
specVersion: 1.0.0
name: analytics-lakehouse
displayName: Analytics Lakehouse Blueprint
version: 1.0.0
description: Provisions storage and compute defaults for an analytics data product.

parameters:
  - key: environment
    type: string
    required: true
    validation:
      allowedValues: [dev, staging, prod]
    ui:
      group: General Configuration
      label: Environment
      description: Deployment stage for this data product.
      formType: dropdown

  - key: retentionDays
    type: integer
    default: 90
    validation:
      min: 1
      max: 3650
    ui:
      group: Storage
      label: Data retention (days)
      formType: number

protectedResources:
  - path: infrastructure/core/**
  - path: README.md

instantiation:
  repositories:
    - key: main
      description: Target repository for all data product assets
  root:
    targets:
      - sourcePath: ./
        repository: main
        path: ./
```

#### 2.2. Monorepo + composition

Single target repository (`main`). Child modules write into subdirectories within `main`.

```yaml
spec: odm-blueprint-manifest
specVersion: 1.0.0
name: full-stack-dp
version: 2.1.0
description: Parent blueprint composing storage and serving modules into one repo.

parameters:
  - key: projectSlug
    type: string
    required: true
    validation:
      pattern: '^[a-z][a-z0-9-]{1,62}$'
    ui:
      group: General Configuration
      label: Project slug
      formType: text

  - key: enablePiiMasking
    type: boolean
    default: true
    ui:
      group: Security
      label: Enable PII masking

instantiation:
  repositories:
    - key: main
      description: Single repository containing application and infrastructure

  root:
    targets:
      - sourcePath: ./
        repository: main
        path: ./

composition:
  - module: storage
    blueprintName: odm-blueprint-s3-lake
    blueprintVersion: 3.0.1
    parameterMapping:
      bucketPrefix: projectSlug
      encryptAtRest: enablePiiMasking
    targets:
      - sourcePath: ./
        repository: main
        path: data-plane/storage

  - module: serving
    blueprintName: odm-blueprint-api-skeleton
    blueprintVersion: 1.4.0
    parameterMapping:
      serviceName: projectSlug
    targets:
      - sourcePath: ./
        repository: main
        path: app/serving
```

#### 2.3. Polyrepo, no composition

A single source blueprint splits subdirectories across multiple abstract target repositories (`infra-repo` and
`app-repo`).

```yaml
spec: odm-blueprint-manifest
specVersion: 1.0.0
name: split-stack-template
version: 0.5.0

parameters:
  - key: awsRegion
    type: string
    required: true
    validation:
      allowedValues: [eu-west-1, eu-central-1, us-east-1]
    ui:
      label: AWS region
      formType: dropdown

instantiation:
  repositories:
    - key: infra-repo
      description: Target repository for infrastructure code and policies
    - key: app-repo
      description: Target repository for application code

  root:
    targets:
      - sourcePath: terraform/
        repository: infra-repo
        path: ./
      - sourcePath: application/
        repository: app-repo
        path: ./
      - sourcePath: policies/
        repository: infra-repo
        path: governance/policies
```

#### 2.4. Polyrepo + composition

Composed child modules and root files are mapped across distinct target repository aliases.

```yaml
spec: odm-blueprint-manifest
specVersion: 1.0.0
name: mesh-polyrepo-parent
version: 1.3.0

parameters:
  - key: dataDomain
    type: string
    required: true
    ui:
      group: Governance
      label: Data domain
      formType: text

instantiation:
  repositories:
    - key: pipeline-repo
      description: Target repository for data pipeline components
    - key: api-repo
      description: Target repository for API serving components

  root:
    targets:
      - sourcePath: ./core
        repository: pipeline-repo
        path: ./

composition:
  - module: ingest
    blueprintName: odm-blueprint-ingest-batch
    blueprintVersion: 2.0.0
    parameterMapping:
      domain: dataDomain
    targets:
      - sourcePath: ./
        repository: pipeline-repo
        path: pipelines/batch

  - module: consume
    blueprintName: odm-blueprint-consumer-api
    blueprintVersion: 1.1.0
    parameterMapping:
      domain: dataDomain
    targets:
      - sourcePath: ./
        repository: api-repo
        path: services/consumer
```

For **pure orchestration** parents that delegate all output to composed modules, set `instantiation.root.targets: []`
and declare only `repositories` and `composition[]` entries.

## Java API

The Blueprint Server ships a Jackson-based parser for the manifest model (
`org.opendatamesh.platform.pp.blueprint.manifest`).

### Using the parser

1. **Obtain a parser** — `ManifestParserFactory.getParser()` builds a default `ObjectMapper` with empty values omitted
   on write (`JsonInclude.Include.NON_EMPTY`). Use `ManifestParserFactory.getParser(ObjectMapper)` if you need a custom
   mapper (modules, YAML at the root, etc.).

2. **Load the document to a `JsonNode`** — The parser API is **tree in, tree out** (`deserialize` / `serialize`). You
   choose the format when reading:
   - **JSON:** `new ObjectMapper().readTree(inputStream)` or `readTree(jsonString)`.
   - **YAML:** use `new ObjectMapper(new YAMLFactory())` from `jackson-dataformat-yaml` and call `readTree` on the
     manifest file or string. Ensure that artifact is on your **runtime** classpath if the service loads YAML
     manifests (it is not always pulled in transitively).

3. **Parse and emit:**

   ```java
   ManifestParser parser = ManifestParserFactory.getParser();
   JsonNode root = /* ObjectMapper.readTree(...) */;
   Manifest manifest = parser.deserialize(root);
   JsonNode out = parser.serialize(manifest);
   ```

Invalid or unsupported shapes fail during binding (Jackson), similar to the descriptor parser.

### Extending the specification

Every schema object in the manifest model inherits from `ManifestComponentBase`. **Standard fields** map to typed Java
properties; **any other property** in the document is captured as raw JSON in `additionalProperties` (forward
compatibility).

To give a **vendor- or platform-specific** key a typed representation:

1. **Define a POJO** extending `ManifestComponentBase` with the fields you need (Jackson will bind nested content).

2. **Implement `ManifestComponentBaseExtendedConverter<T>`** (
   `org.opendatamesh.platform.pp.blueprint.manifest.extensions`):
   - `supports(String key, Class<? extends ManifestComponentBase> parentClass)` — return `true` for the extension
     property name and the parent node type (for example root manifest: `Manifest.class` and your top-level key).
   - `deserialize(ObjectMapper, JsonNode)` — produce your subtype (typically
     `mapper.treeToValue(jsonNode, MyExtension.class)`).
   - `serialize(ObjectMapper, T)` — produce a `JsonNode` for that property (typically `mapper.valueToTree(value)`).

3. **Register the converter on the parser** (fluent), then deserialize or serialize as usual:

   ```java
   ManifestParser parser = ManifestParserFactory.getParser()
       .register(new MyExtensionConverter());
   ```

On **deserialization**, matching keys are removed from `additionalProperties` and the typed instance is stored in
`parsedProperties`. On **serialization**, parsed extensions are written back into the JSON tree for those keys. Keys
that are **not** covered by a registered converter remain in `additionalProperties` only.

You can register multiple converters; the first converter whose `supports` method matches wins. Extension handling walks
the full manifest tree (root, parameters, composition, instantiation, nested objects), so you can target extension
fields on child nodes by returning the appropriate `parentClass` from `supports`.

---

## Domain Concept Identification

### Existing Concepts (from codebase)

- **Blueprint Manifest**: Authoritative YAML/JSON contract (`spec: odm-blueprint-manifest`) parsed by Jackson in the blueprint service (`manifest` package) and mirrored by the Blindata UI Manifest SDK (`manifestSdk`). Relationship: root document governing parameters, protection, composition, and instantiation routing for a blueprint version’s `content`.
- **Manifest Parser / SDK**: Tree-in/tree-out binding with `additionalProperties` / extension converters (Java) and `fromRaw` / serialization visitors (JS). Relationship: shared contract surface that must stay synchronized with the README schema.
- **Manifest Parameter (+ validation + ui)**: Declared inputs with `key`, `type`, `required`, `default`, `validation`, and `ui` (`group`, `label`, `description`, `formType`). Relationship: already largely aligned with the updated specification; drives parameter collection UIs and instantiate-time validation.
- **Protected Resource (+ integrity)**: Immutable path/glob markers; integrity digest populated on the lineage copy in the data-product repo. Relationship: model exists in both stacks; behavior largely unchanged by this specification revision.
- **Composition module**: Child blueprint reference (`module`, `blueprintName`, `blueprintVersion`, `parameterMapping`). Relationship: today routing for children lives separately under `instantiation.compositionLayout` / polyrepo `targets[].module`; the new spec co-locates routing as `composition[].targets[]`.
- **Instantiation (old shape still in code)**: `instantiation.strategy` (`monorepo` | `polyrepo`), optional `compositionLayout[]` (`module` + `targetPath`), and `targets[]` with `repositoryNamePostfix`, `createPolicy`, `module`, `sourcePath`, `targetPath`. Relationship: this is the primary gap versus the updated specification.
- **Instantiation scenario (runtime)**: Derived from repository-key cardinality + composition presence into `MONOREPO_NO_COMPOSITION` (implemented), vs composition/polyrepo scenarios (explicitly unsupported). Relationship: scenario detection and monorepo render path are based on the new topology model without enabling unsupported scenarios.
- **Target repository (API / UI)**: Instantiate/update commands pass `targetRepositories` with `targetId` (manifest `repositories[].key`) and Git repository metadata; UI monorepo step configures a single mapped repository. Relationship: reconcile request `targetId` with the sole repository key; root vs module role lives in the manifest (`instantiation.root` vs `composition[]`), not on the request entry. Phase-1 runtime still supports a single repository key only.
- **Manifest validator / autofiller**: Visitors that currently require `strategy`, validate `compositionLayout` and postfix-based polyrepo targets, and default missing strategy to `monorepo`. Relationship: must be rewritten against repositories/root/composition targets and new uniqueness/path constraints.
- **Registration / scaffold templates (UI)**: Default manifest YAML emitted at blueprint registration still uses `instantiation.strategy: monorepo`. Relationship: scaffolds must emit the new `repositories` + `root.targets` shape so newly registered blueprints are valid under the updated contract.
- **Instantiate & update use cases**: Clone, render, checkpoint-tag, and merge flows gated on old strategy detection (also in `UpdateDataProductFromBlueprintVersion` and `BlueprintRenderService`). Relationship: supported monorepo-no-composition path must keep working after schema alignment; unsupported scenarios remain unsupported.

### New Concepts Required

- **Logical repository key (`instantiation.repositories[]`)**: Abstract destination alias (`key`, optional `description`) that the client resolves to a concrete Git repository at instantiation time. Relationship: replaces `strategy` + `repositoryNamePostfix` as the topology vocabulary; referenced by all route `repository` fields.
- **Root routing (`instantiation.root.targets[]`)**: Uniform route entries (`sourcePath` → `repository` + `path`) for parent blueprint content; may be an empty array for pure orchestration parents. Relationship: replaces flat `instantiation.targets` for parent output and removes create-policy from the manifest.
- **Composition-local targets (`composition[].targets[]`)**: Same route shape as root targets, scoped to the child blueprint repository. Relationship: replaces `instantiation.compositionLayout` and module-bearing polyrepo targets; co-locates module identity, parameter mapping, and routing.
- **Topology inferred from repositories (not strategy enum)**: Monorepo vs polyrepo becomes a derived property (one vs many repository keys) rather than a first-class manifest field. Relationship: scenario resolution, UI repository-step branching, and docs must stop reading `instantiation.strategy`.
- **Orchestrator-owned creation policy**: `create_if_missing` / `must_exist` and physical URLs live outside the manifest (client/orchestrator). Relationship: remove from manifest model; keep only in client/API behavior where already implemented for monorepo create/select.

### Key Business Rules

- Manifest remains focused on provisioning/orchestration; DP descriptor path stays in the platform Blueprint model, not the manifest.
- Client resolves each `instantiation.repositories[].key` and supplies mapped repositories to the instantiate endpoint; creation policies are not part of the manifest.
- Lineage manifest copy is written only to the root target repository designated at instantiation time.
- Validation must enforce: unique repository keys; unique composition module aliases; every route `repository` references a declared key; multi-entry `targets` require explicit `sourcePath`; relative paths only (no absolute / `..`).
- Parameter `type` defaults to `string` when omitted; `required` defaults to false; `schemaRef` remains explicitly unsupported.
- **Scope guardrail:** Align models, parsers, validators, autofillers, fixtures, SDK, and currently implemented flows to the new schema. Do **not** implement previously unsupported runtime features (composition instantiation, multi-repo instantiate/update, polyrepo UI) as part of this work.

## Strategic Approach

### Solution Direction

Treat the updated README as a **breaking schema alignment** across the blueprint service and Blindata UI. Replace the old instantiation vocabulary (`strategy`, `compositionLayout`, postfix/`createPolicy` targets) with `repositories`, `root.targets`, and `composition[].targets`. Re-ground scenario detection on repository-key cardinality + composition presence so the already-supported **single-repository, no-composition** path continues to work. Keep unsupported scenarios throwing the same class of “not supported yet” outcomes. Parameters, protected resources, and UI metadata remain as-is aside from any incidental coupling to the old instantiation model. Update test YAML examples, registration scaffolds, and process docs references so authors and clients only see the new contract.

High-level data flow (unchanged at product level): publish/register blueprint version with manifest content → UI deserializes manifest → collect parameters and resolve repository key(s) → instantiate/update endpoints validate and (for supported scenario) render into the mapped root repository.

### Key Design Decisions

- **Hard cut to the new schema vs dual-read compatibility for old manifests**: Dual-read reduces migration pain for already-stored version content but prolongs two models and ambiguous validation. → **Hard cut only.** Treat previously stored old-shape manifests as non-existent (service not in real use); no migration, dual-read, or special rejection path for legacy `strategy`/postfix content.
- **How to detect monorepo vs polyrepo without `strategy`**: Options include counting `repositories[]`, inspecting distinct `repository` references in routes, or retaining a deprecated field. → **Derive topology from `instantiation.repositories` cardinality** (and/or distinct referenced keys), with composition presence still selecting the composition-related unsupported scenarios. Single key + empty/non-empty composition maps to today’s monorepo scenarios; multiple keys map to polyrepo scenarios.
- **Instantiate/update request contract (`targetId` only)**: Spec expects targets mapped by repository key; old API/UI used `type: root` without a key. → **Replace `type` with `targetId`**: `targetId` reconciles with `instantiation.repositories[].key`. Root vs module is expressed by manifest structure (`instantiation.root` / `composition[]`), not by a request enum. Remove `BlueprintRepositoryLogicalType` from request/result DTOs. Minimal key-mapping on the existing list-based target payload is **in scope**; do not build multi-repo selection UX.
- **Root target repository for lineage (multi-key designation)**: Spec says lineage copy goes only to the root target designated at instantiation time; schema has no primary-key flag. → **Do not handle for now** (defer designation rules until multi-repo lineage is in scope). Phase-1 keeps writing lineage to the single supported root target as today.
- **Empty `root.targets` (orchestration parents)**: Spec allows `[]`; current monorepo render assumes parent content is copied. → **Model and validate as allowed**; runtime for empty-root/composition-only remains **out of scope** (unsupported scenario), consistent with “no new features.”
- **Where to keep cross-repo analysis**: Spec source of truth is blueprint-server; UI SDK mirrors it. → **Single analysis artifact under blueprint-server `spdd/analysis/`**, covering both codebases for REASONS Canvas inputs.

### Alternatives Considered

- **Keep `strategy` as a redundant hint alongside repositories**: Rejected — contradicts the updated specification and recreates dual sources of truth for topology.
- **Implement full polyrepo + composition instantiation now because the schema supports it**: Rejected — explicitly out of scope; code already marks those scenarios unsupported; schema alignment must not expand runtime capability.
- **UI-only adapter that rewrites new manifests into the old in-memory model**: Rejected — leaves the server unable to parse/validate the published contract and drifts the two stacks further apart.

## Risk & Gap Analysis

### Requirement Ambiguities

- *(Resolved)* **Stored blueprint versions with old manifests**: Do not handle. Treat as non-existent (service not in real use). No migration or dual-read.
- *(Resolved)* **Instantiate/update request contract**: Use `targetId` only — reconciles with manifest `repositories[].key`. Drop request `type` / `BlueprintRepositoryLogicalType`; root vs module stays in the manifest (`root` / `composition`).
- *(Resolved)* **“Root target repository” for lineage**: Do not handle for now (defer multi-key root designation).
- *(Resolved)* **“New features not yet implemented”**: Minimal key-mapping (`targetId` on existing target list) is in scope; composition/polyrepo runtime and polyrepo multi-picker UX remain out of scope.

### Edge Cases

- **Manifest with one repository key but multiple root target path splits**: Still monorepo topology; phase-1 render today copies whole tree—path-splitting behavior for a single repo may be partially new relative to current “copy all” monorepo path and needs a conscious in-scope vs defer decision.
- **`root.targets: []` with composition present**: Valid per spec; must validate structurally but reject at runtime as unsupported (composition not implemented).
- **Multiple `targets` without explicit `sourcePath`**: Must fail validation (default `./` not allowed when length > 1).
- **Composition parameterMapping literals vs parent key references**: Existing model stores `JsonNode`/object values; orchestration resolution rules stay as today for unsupported composition runtime, but parsing must not break on the new co-located `targets`.
- **Registration scaffolds and docs still showing `strategy: monorepo`**: Will produce invalid manifests the moment validators switch—must be updated in the same alignment effort.

### Technical Risks

- **Wide blast radius on visitors/tests**: Instantiation model change touches Java model, visitors, extension visitor, validator, autofiller, instantiate/update ports, render service, parser tests, example YAMLs, and the entire UI Manifest SDK + repository step + registration templates. Mitigation: treat schema/model/parser/validator/fixtures as one vertical slice; then wire scenario detection; then UI SDK + consumers.
- **Duplicate UI repository-step modules**: Historically both `commons/instantiation_modal/BlueprintInstantiationModalRepositoriesStep.jsx` and `.../repositories_step/...` existed. Mitigation applied: keep only `repositories_step/`; delete the root re-export/duplicate; branch on repository-key topology helpers (`isSingleRepositoryTopology` / `soleRepositoryKey`).
- **Scenario enum still named MONOREPO/POLYREPO**: Fine as internal runtime taxonomy if derived from repositories; risk if left coupled to removed JSON field. Mitigation: resolve scenario only from new fields.
- **Docs (`blueprint-process.md`) still describe strategy**: Drift risk for operators; update references as part of alignment (documentation adjacent to code contract).
- **No DB migration for manifest JSON**: Manifests live as version `content` documents, not normalized tables. Mitigation: **none required** — legacy old-shape content is treated as non-existent per product decision.

### Acceptance Criteria Coverage

| AC# | Description | Addressable? | Gaps/Notes |
|-----|-------------|--------------|------------|
| AC1 | Blueprint-service manifest model and parser bind the new schema (`repositories`, `root.targets`, `composition[].targets`; remove/stop requiring `strategy`, `compositionLayout`, postfix/`createPolicy` target fields) | Yes | Core alignment |
| AC2 | Manifest validator enforces new uniqueness, repository-reference, multi-target `sourcePath`, and relative-path rules from the specification | Yes | Replace strategy/compositionLayout/postfix rules |
| AC3 | Autofiller and example/test fixtures produce/consume the new example shapes (incl. monorepo single-key default) | Yes | Includes `src/test/resources/manifest/*` and instantiate fixtures |
| AC4 | Supported runtime path (single repository key, no composition) still instantiates and updates successfully when manifests use the new shape | Yes | Scenario detection rewrite; no composition/polyrepo enablement |
| AC5 | Unsupported scenarios (composition and/or multiple repository keys) remain explicitly unsupported (clear error), not partially implemented | Yes | Matches out-of-scope guardrail |
| AC6 | Blindata UI Manifest SDK model/parser/serializer/traverse mirrors the new schema | Yes | Drop InstantiationStrategy-centric constants or relegate to derived helpers if still useful |
| AC7 | UI instantiation repository step and validation use repository-key topology for the currently supported single-repo case (not `strategy`) | Yes | Polyrepo multi-picker UI remains out of scope; show unsupported for multi-key manifests |
| AC8 | Registration/init scaffold manifests emit the new instantiation block | Yes | `blueprintRepositoryInitTemplates` and similar |
| AC9 | Instantiate/update target entries carry `targetId` and reconcile with the sole manifest `repositories[].key` (no request `type`) | Yes | Root/module role is manifest-side; no multi-repo UX |
| AC10 | No delivery of previously unimplemented product features (composition instantiate, polyrepo instantiate/update UX, multi-key lineage designation, `schemaRef`, etc.) under this ticket | Yes | Explicit out-of-scope check during REASONS/generate; legacy old manifests ignored |

