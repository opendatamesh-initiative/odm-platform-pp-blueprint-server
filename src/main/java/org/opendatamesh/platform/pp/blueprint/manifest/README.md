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
> keys (`targetRepositories[].key`). Created or pre-existing repositories are supplied to the instantiate endpoint,
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
- **Separation of concerns:** `composition[]` declares **what** child blueprints to include and how parent parameters
  map to child inputs. `instantiation[]` declares **where** generated output lands

#### 3.4. Instantiation Strategy

The manifest abstracts target destinations using logical repository identifiers declared in `targetRepositories`.

- **Monorepo Topology:** All generated output maps to a single repository key.
- **Polyrepo Topology:** Root contents and composed child modules are distributed across multiple repository keys.
- **Root designation:** Exactly one `targetRepositories[]` entry must set `isRoot: true`. That key is the data-product
  root repository (lineage, descriptor enrichment, registry primary pointer).
- **Typed instantiation entries:** `instantiation[]` is a list of routing directives, each with `type: root` (parent
  blueprint contents) or `type: module` (a composed child blueprint, referenced by `moduleName`).
- **Uniform routing:** Every `instantiation[].targets[]` entry uses the same route shape (`sourcePath` → `repo` +
  `destinationPath`). For `type: root`, `sourcePath` is relative to the parent blueprint repository; for `type: module`,
  `sourcePath` is relative to the child blueprint repository.
- **Path Splitting:** Multiple entries in a `targets[]` list route different source subdirectories to separate
  repository keys and destinations.

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
2. **Target Resolution:** The client orchestrator resolves each logical repository key declared in `targetRepositories`
   to an actual Git repository. Repository creation policies (`create_if_missing`, `must_exist`, and so on) are
   enforced **outside** the manifest; the orchestrator creates or selects repositories and passes them to the
   instantiate endpoint, each entry mapped to a repository key (`targetId`).
3. **Configuration:** The user provides values for all parameters declared in the Blueprint Manifest (facilitated by the
   UI/UX metadata).
4. **Generation & Copy:** The system resolves `composition[]` (child blueprint references and parameter mappings),
   then processes each `instantiation[]` entry: for `type: root`, routes parent blueprint files; for `type: module`,
   instantiates the referenced child and routes its output. Files are extracted from declared source directories,
   parameter variables are resolved, and generated code is written into the mapped target repositories and destinations.
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
- `composition` (Array of Objects, Optional): Declares child blueprints (modules) to be instantiated alongside the
  parent.
  - `module` (String, Required): A logical alias for the child module. Must be unique within the manifest.
  - `blueprintName` (String, Required): The identifier of the child blueprint.
  - `blueprintVersion` (String, Required): The target release version of the child blueprint.
  - `parameterMapping` (Object, Optional): Maps **child** parameter keys to values supplied at instantiation. Every
    entry **must** be an object with **exactly one** discriminant:
    - `{ $param: <parentKey> }` — dynamic reference resolved from the parent parameter set (request value, else parent
      default; fail if the parent key is undeclared or has neither value nor default). Extra properties besides
      `$param` are ignored.
    - `{ value: <actualValue> }` — fixed literal copied from the manifest (`actualValue` may be string, number,
      boolean, object, or array). Extra properties besides `value` are ignored; the literal is **not** looked up on
      the parent.
      Bare scalars, arrays, or objects with both/neither discriminants are **invalid**. This is the manifest analogue of
      Terraform’s explicit `module "x" { ... }` variable passing: only declared inputs are passed—there is no implicit
      global scope. Nested expressions (e.g., string concatenation) are out of scope; if a value must be derived, expose
      it as a parent parameter.
- `targetRepositories` (Array of Objects, Required): Abstract repository keys the client must resolve at instantiation
  time.
  - `key` (String, Required): Unique logical alias referenced by `instantiation[].targets[].repo` (e.g.,
    `main-repository`, `pipeline-repo`).
  - `description` (String, Optional): Human-readable guidance for UI selection.
  - `isRoot` (Boolean, Optional — defaults to `false`): When `true`, designates this key as the data-product root
    repository (lineage, descriptor enrichment, registry primary pointer). Exactly one entry must set `isRoot: true`.
- `instantiation` (Array of Objects, Required): Routes blueprint contents to destination repositories. Each entry
  targets either the parent blueprint (`type: root`) or a composed child module (`type: module`).
  - `type` (Enum: `root`, `module`, Required): Discriminant for the instantiation entry.
    - `root` — routes files from the **parent** blueprint repository.
    - `module` — instantiates and routes files from a **child** blueprint declared in `composition[]`.
  - `moduleName` (String, Required when `type: module`): Must match a `composition[].module` value. **Omitted** when
    `type: root`.
  - `targets` (Array of Objects, Required): **Must be non-empty**. Routes subdirectories of the relevant blueprint
    repository (parent for `type: root`, child for `type: module`) to destination repositories.
    - `sourcePath` (String, Optional — defaults to `./`): Directory path relative to the blueprint repository root
      (parent or child, depending on `type`).
    - `repo` (String, Required): Must match a `targetRepositories[].key`.
    - `destinationPath` (String, Optional — defaults to `./`): Directory path relative to the destination repository root.
      Destinations on the **same** repository key must be **siblings** (not nested path-prefixes of each other).

#### Validation constraints

The orchestrator must enforce the following rules when validating a manifest:

- `targetRepositories[].key` values must be **unique**.
- Exactly one `targetRepositories[]` entry must set `isRoot: true`.
- `composition[].module` values must be **unique** (when `composition` is present).
- `instantiation` must be **non-empty** and contain exactly one entry with `type: root`.
- Every `instantiation[]` entry with `type: module` must declare `moduleName` matching an existing
  `composition[].module`. Every `composition[].module` must have a corresponding `instantiation[]` entry with
  `type: module`.
- Every `instantiation[].targets` array must be **non-empty**.
- When `BlueprintRepo.descriptorTemplatePath` is configured, the platform **always** renders that template onto the
  designated root target (`targetRepositories[]` entry with `isRoot: true`) at the path derived from the template
  (same relative path with `.vm` stripped). Authors do **not** declare an `instantiation` route for the descriptor.
- Every declared `targetRepositories[].key` must appear on at least one route (`instantiation[].targets[].repo`);
  unused keys are rejected.
- Every `repo` reference in `instantiation[].targets[]` must match an existing `targetRepositories[].key`.
- Exact duplicate `(repo, normalized destinationPath)` destinations across all routes are rejected.
- Nested path-prefix destinations on the **same** repository key (e.g. `./` together with `data-plane/storage`) are
  rejected; use sibling destinations.
- `parameterMapping` entries must be `{ $param: key }` or `{ value: actualValue }` objects; bare scalars are invalid.
- At parent **publish**, `parameterMapping` must include an entry for every parameter declared by the referenced
  published module that has **no default**. Module parameters that declare a default may be omitted.
- Composition modules must be published versions that are themselves **monorepo with no composition**.
- When a `targets` array contains **more than one** entry, each entry must declare an explicit `sourcePath` (the `./`
  default must not be relied upon implicitly, to avoid accidental duplication of the entire source repository).
- Repository paths must be **relative** to the repository root; absolute paths and path traversal segments (`..`) are
  rejected.
- Path normalization (leading `./`, trailing slashes) is implementation-defined but must be applied consistently.
- Validation reports **all** problems found, each with a short how-to-fix hint.

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

targetRepositories:
  - key: main-repository
    description: Target repository for all data product assets
    isRoot: true

instantiation:
  - type: root
    targets:
      - sourcePath: ./
        repo: main-repository
        destinationPath: ./
```

#### 2.2. Monorepo + composition

Single target repository (`main-repository`). Child modules write into subdirectories within `main-repository`.

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

targetRepositories:
  - key: main-repository
    description: Single repository containing application and infrastructure
    isRoot: true

composition:
  - module: storage
    blueprintName: odm-blueprint-s3-lake
    blueprintVersion: 3.0.1
    parameterMapping:
      bucketPrefix: { $param: projectSlug }
      encryptAtRest: { $param: enablePiiMasking }
      region: { value: eu-west-1 }

  - module: serving
    blueprintName: odm-blueprint-api-skeleton
    blueprintVersion: 1.4.0
    parameterMapping:
      serviceName: { $param: projectSlug }

instantiation:
  - type: root
    targets:
      - sourcePath: ./
        repo: main-repository
        destinationPath: core/

  - type: module
    moduleName: storage
    targets:
      - sourcePath: ./
        repo: main-repository
        destinationPath: data-plane/storage

  - type: module
    moduleName: serving
    targets:
      - sourcePath: ./
        repo: main-repository
        destinationPath: app/serving
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

targetRepositories:
  - key: infra-repo
    description: Target repository for infrastructure code and policies
  - key: app-repo
    description: Target repository for application code
    isRoot: true

instantiation:
  - type: root
    targets:
      - sourcePath: terraform/
        repo: infra-repo
        destinationPath: terraform/
      - sourcePath: policies/
        repo: infra-repo
        destinationPath: governance/policies
      - sourcePath: application/
        repo: app-repo
        destinationPath: ./
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

targetRepositories:
  - key: pipeline-repo
    description: Target repository for data pipeline components
    isRoot: true
  - key: api-repo
    description: Target repository for API serving components

composition:
  - module: ingest
    blueprintName: odm-blueprint-ingest-batch
    blueprintVersion: 2.0.0
    parameterMapping:
      domain: { $param: dataDomain }

  - module: consume
    blueprintName: odm-blueprint-consumer-api
    blueprintVersion: 1.1.0
    parameterMapping:
      domain: { $param: dataDomain }

instantiation:
  - type: module
    moduleName: ingest
    targets:
      - sourcePath: ./
        repo: pipeline-repo
        destinationPath: ./

  - type: module
    moduleName: consume
    targets:
      - sourcePath: ./
        repo: api-repo
        destinationPath: ./

  - type: root
    targets:
      - sourcePath: ./
        repo: pipeline-repo
        destinationPath: ./core
```

Exactly one `targetRepositories[]` entry must set `isRoot: true` (the data-product root).
`instantiation` must contain exactly one entry with `type: root`. Every `composition[].module` must have a
corresponding `instantiation[]` entry with `type: module`.

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
