# Software Requirements Specification: Blueprint Manifest

<!-- TOC -->

* [Software Requirements Specification: Blueprint Manifest](#software-requirements-specification-blueprint-manifest)
    * [Initial Context](#initial-context)
        * [1. Overview and Purpose](#1-overview-and-purpose)
        * [2. Format Recommendation: YAML](#2-format-recommendation-yaml)
        * [3. Core Responsibilities](#3-core-responsibilities)
            * [3.1. Parameter Management](#31-parameter-management)
            * [3.2. Resource Protection](#32-resource-protection)
            * [3.3. Blueprint Composition (Modularity)](#33-blueprint-composition-modularity)
            * [3.4. Instantiation Strategy](#34-instantiation-strategy)
            * [3.5. Versioning Control](#35-versioning-control)
        * [4. Instantiation Workflow](#4-instantiation-workflow)
    * [Specification](#specification)
        * [1. Specification Definition with Schema](#1-specification-definition-with-schema)
            * [Core Schema Objects](#core-schema-objects)
        * [2. Manifest Examples](#2-manifest-examples)
            * [2.1. Monorepo, no composition](#21-monorepo-no-composition)
            * [2.2. Monorepo + composition](#22-monorepo--composition)
            * [2.3. Polyrepo, no composition](#23-polyrepo-no-composition)
            * [2.4. Polyrepo + composition](#24-polyrepo--composition)
    * [Java API](#java-api)
        * [Using the parser](#using-the-parser)
        * [Extending the specification](#extending-the-specification)

<!-- TOC -->

## Initial Context

### 1. Overview and Purpose

The **Blueprint Manifest** is a core configuration file residing within a Blueprint repository.
A Blueprint is defined as a Git repository containing parameterized templates and infrastructure-as-code (IaC) files
necessary to provision a Data Product within a Data Mesh architecture (e.g., databases, S3 buckets, virtual machines).

The primary purpose of the manifest is to orchestrate the instantiation of the Data Product by defining required
parameters, protecting specific resources, managing blueprint composition, and declaring the deployment strategy.

> **Architectural Note**: The location of the Data Product descriptor template within the repository is intentionally
> not specified or tracked by this manifest. Instead, its location is registered and stored within the Platform's
> internal
> Blueprint model. This ensures the manifest remains strictly focused on provisioning and infrastructure orchestration.

A copy of this manifest is retained in the instantiated Data Product repository to maintain lineage and trace the
original Blueprint used.

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
- **UI/UX Presentation:** The specification must support presentation metadata for parameters to drive dynamic frontend
  forms during instantiation. This includes:
    - **Grouping:** Categorizing related parameters into logical sections or steps (e.g., "Networking", "Database
      Config").
    - **Labeling:** Defining human-readable display names distinct from the underlying variable keys.
    - **Descriptions/Tooltips:** Providing helper text to guide the user in supplying the correct values.
    - **Input Types:** Suggesting specific UI components (e.g., dropdowns for enums, password fields for secrets,
      toggles for booleans).
    - **Required parameters:** If the parameter is mandatory or not, used also for validation.

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

#### 3.4. Instantiation Strategy

The manifest must define how the output of the Blueprint should be distributed. It must support configurations such as:

- **Monorepo Strategy:** All instantiated files and infrastructure definitions are generated into a single target
  repository.
- **Polyrepo/Multi-repo Strategy:** Instantiated components are distributed across multiple target repositories (e.g.,
  separating application code from infrastructure code). The manifest declares **repository name postfixes** per target;
  the **parent repository name** is supplied **at instantiation** (not as a manifest parameter), and the platform
  derives each target repository from that runtime name plus each postfix.

#### 3.5. Versioning Control

To ensure stability, backward compatibility, and reliable lineage, the manifest must handle versioning at two distinct
levels:

- **Specification Versioning:** The manifest must declare the schema version of the manifest file itself (e.g.,
  `specVersion: 1.0.0`). This allows the orchestrating system to parse the file correctly and supports future iterations or
  breaking changes to the manifest schema.
- **Blueprint Versioning:** The manifest must declare the release version of the specific Blueprint it represents (e.g.,
  `version: 1.2.0`, adhering to Semantic Versioning). This allows users to instantiate specific, stable releases of a
  Blueprint and safely upgrade their Data Products over time.

### 4. Instantiation Workflow

The system orchestrating the Blueprint must support the following lifecycle:

1. **Selection:** The user selects a specific Blueprint and version for their Data Product.
2. **Configuration:** The user provides values for all parameters declared in the Blueprint Manifest (facilitated by the
   UI/UX metadata).
3. **Target Designation:** The user specifies the target repository (or repositories, based on the instantiation
   strategy). For **polyrepo**, the **parent blueprint’s repository name** is supplied at runtime to the platform (it is
   **not** a manifest parameter); the manifest only declares **per-target postfixes** that the platform combines with
   that name to derive each target repository identity, according to platform naming rules.
4. **Generation & Copy:** The system copies the Blueprint files to the target repository.

- All template parameters within the files are resolved/populated with the user-provided values.

5. **Lineage Preservation:** A version of the manifest, complete with the resolved parameter values and versioning
   metadata, is copied into the target repository to serve as a historical record of the Blueprint version and
   configuration used to create the Data Product.

## Specification

### 1. Specification Definition with Schema

The Blueprint Manifest represents the authoritative contract for a **Data Product template**. The schema is strictly
typed to ensure predictable parsing and validation by the orchestrating engine, while offering extensibility for custom
integrations.

#### Core Schema Objects

- `spec` (String, Required): The specification name, must be set to `odm-blueprint-manifest`.
- `specVersion` (String, Required): The version of the manifest schema itself (e.g., `1.0.0`). The orchestrator uses this
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
    - `ui` (Object, Optional): Metadata driving the frontend form generation.
        - `group` (String, Optional): Categorizes the parameter into logical sections for UI presentation. Supports
          nested groups using a forward slash (`/`) delimiter (e.g., `General Configuration/Product/Advanced`), allowing
          the frontend to render hierarchical menus, tabs, or wizard steps.
        - `label` (String, Optional): A human-readable display name for the parameter distinct from the programmatic
          key.
        - `description` (String, Optional): Helper text or tooltip to guide the user in supplying the correct value.
        - `formType` (String, Optional): Suggests the specific UI component for the frontend to render (e.g., `text`,
          `number`, `dropdown`, `tags`, `json-editor`).
- `protectedResources` (Array of Objects, Optional): Files, directories, or globs marked immutable after initial
  generation. Each item:
    - `path` (String, Required): Path relative to the repository root, or a glob (e.g., `infrastructure/`*).
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
- `instantiation` (Object, Required): Defines where generated output is written and how multi-repo or multi-module
  layouts are resolved.
    - `strategy` (Enum: `monorepo`, `polyrepo`, Required) — Single target repository versus multiple.
    - `compositionLayout` (Array of Objects, Optional): Where each child module’s output is merged when using **monorepo
      with composition**.
        - `module` (String, Required): Must match a `composition[].module` value.
        - `targetPath` (String, Required): Directory relative to the target repo root for that module’s generated tree.
    - `targets` (Array of Objects, Optional): **Required when strategy is `polyrepo`.** Each entry is one **deployment
      slice**: which logical target repository to use (via `repositoryNamePostfix` only—no full URL or host in the
      manifest), plus either which composed `module` the slice belongs to or which paths to copy inside that repository.
      The **parent blueprint repository name** is provided **at runtime** by the user or platform when instantiating; it
      is **not** declared as a parameter in the manifest. The platform derives each target repository’s name (and
      location) by combining that runtime parent name with `repositoryNamePostfix` (and any platform-specific separators
      or conventions). Repeat the same `repositoryNamePostfix` on multiple entries if several slices land in the same
      derived repository. With composition, each `composition[].module` should appear on **exactly one** entry that uses
      the `module` shape (unless the platform documents a different rule).
        - `repositoryNamePostfix` (String, Required): Suffix appended (per platform rules) to the **runtime** parent
          repository name to form the target repository identity for this slice. This is the **only** repository locator
          the manifest carries for polyrepo; URLs and org/project identifiers are out of scope here.
        - `createPolicy` (Enum: `create_if_missing`, `must_exist`, Optional): Whether the orchestrator may create a
          missing repository.
        - **Polyrepo + composition:** set `module` (String, Required): must match `composition[].module`; that module’s
          output is written to the repository identified by parent name (runtime) + `repositoryNamePostfix`.
        - **Polyrepo without composition:** set `sourcePath` (String, Required) and `targetPath` (String, Required): map
          a path within the blueprint checkout to a path **within** the repository identified by parent name (runtime) +
          `repositoryNamePostfix`.
        - An entry uses **either** the `module` shape **or** the `sourcePath` / `targetPath` shape, matching whether
          `composition` is in use; do not mix both on the same object.

---

### 2. Manifest Examples

All examples use `spec: odm-blueprint-manifest` and `specVersion: 1.0.0`. They are **source** Blueprint manifests unless
noted. Parameter lists are abbreviated; real manifests would declare every input the templates need.

#### 2.1. Monorepo, no composition

Single repository; parent output only. No `composition` section and no extra `instantiation` keys beyond `strategy`.

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
      allowedValues: [ dev, staging, prod ]
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
  strategy: monorepo

```

#### 2.2. Monorepo + composition

Child modules are merged into **one** target repo using `compositionLayout`. Parent name is chosen at runtime; only
directory layout is fixed in the manifest.

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
      formType: toggle

composition:
  - module: storage
    blueprintName: odm-blueprint-s3-lake
    blueprintVersion: 3.0.1
    parameterMapping:
      bucketPrefix: projectSlug
      encryptAtRest: enablePiiMasking

  - module: serving
    blueprintName: odm-blueprint-api-skeleton
    blueprintVersion: 1.4.0
    parameterMapping:
      serviceName: projectSlug

instantiation:
  strategy: monorepo
  compositionLayout:
    - module: storage
      targetPath: data-plane/storage
    - module: serving
      targetPath: app/serving
```

#### 2.3. Polyrepo, no composition

Multiple repositories derived from **runtime parent name + `repositoryNamePostfix`**. Each `targets` row uses
`sourcePath` / `targetPath` (no `module`).

Suppose the user instantiates with runtime parent repository name `acme-customer-360`. The platform derives
`acme-customer-360-infra` and `acme-customer-360-apps` from the postfixes below.

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
      allowedValues: [ eu-west-1, eu-central-1, us-east-1 ]
    ui:
      label: AWS region
      formType: dropdown

instantiation:
  strategy: polyrepo
  targets:
    - repositoryNamePostfix: "-infra"
      createPolicy: create_if_missing
      sourcePath: terraform/
      targetPath: ./

    - repositoryNamePostfix: "-apps"
      createPolicy: create_if_missing
      sourcePath: application/
      targetPath: ./

    - repositoryNamePostfix: "-infra"
      sourcePath: policies/
      targetPath: policies/
```

The first and third entries share the `-infra` postfix to show multiple `sourcePath` / `targetPath` slices landing in
the same derived repository (per the schema note).

#### 2.4. Polyrepo + composition

Each composed module is deployed to a repository identified by **parent name (runtime) + postfix**. Each `targets` row
sets `module` (no `sourcePath` / `targetPath` on that row).

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

composition:
  - module: ingest
    blueprintName: odm-blueprint-ingest-batch
    blueprintVersion: 2.0.0
    parameterMapping:
      domain: dataDomain

  - module: consume
    blueprintName: odm-blueprint-consumer-api
    blueprintVersion: 1.1.0
    parameterMapping:
      domain: dataDomain

instantiation:
  strategy: polyrepo
  targets:
    - repositoryNamePostfix: "-pipeline"
      createPolicy: must_exist
      module: ingest

    - repositoryNamePostfix: "-api"
      createPolicy: create_if_missing
      module: consume
```

## Java API

The Blueprint Server ships a Jackson-based parser for the manifest model (`org.opendatamesh.platform.pp.blueprint.manifest`).

### Using the parser

1. **Obtain a parser** — `ManifestParserFactory.getParser()` builds a default `ObjectMapper` with empty values omitted on write (`JsonInclude.Include.NON_EMPTY`). Use `ManifestParserFactory.getParser(ObjectMapper)` if you need a custom mapper (modules, YAML at the root, etc.).

2. **Load the document to a `JsonNode`** — The parser API is **tree in, tree out** (`deserialize` / `serialize`). You choose the format when reading:
   - **JSON:** `new ObjectMapper().readTree(inputStream)` or `readTree(jsonString)`.
   - **YAML:** use `new ObjectMapper(new YAMLFactory())` from `jackson-dataformat-yaml` and call `readTree` on the manifest file or string. Ensure that artifact is on your **runtime** classpath if the service loads YAML manifests (it is not always pulled in transitively).

3. **Parse and emit:**

   ```java
   ManifestParser parser = ManifestParserFactory.getParser();
   JsonNode root = /* ObjectMapper.readTree(...) */;
   Manifest manifest = parser.deserialize(root);
   JsonNode out = parser.serialize(manifest);
   ```

Invalid or unsupported shapes fail during binding (Jackson), similar to the descriptor parser.

### Extending the specification

Every schema object in the manifest model inherits from `ManifestComponentBase`. **Standard fields** map to typed Java properties; **any other property** in the document is captured as raw JSON in `additionalProperties` (forward compatibility).

To give a **vendor- or platform-specific** key a typed representation:

1. **Define a POJO** extending `ManifestComponentBase` with the fields you need (Jackson will bind nested content).

2. **Implement `ManifestComponentBaseExtendedConverter<T>`** (`org.opendatamesh.platform.pp.blueprint.manifest.extensions`):
   - `supports(String key, Class<? extends ManifestComponentBase> parentClass)` — return `true` for the extension property name and the parent node type (for example root manifest: `Manifest.class` and your top-level key).
   - `deserialize(ObjectMapper, JsonNode)` — produce your subtype (typically `mapper.treeToValue(jsonNode, MyExtension.class)`).
   - `serialize(ObjectMapper, T)` — produce a `JsonNode` for that property (typically `mapper.valueToTree(value)`).

3. **Register the converter on the parser** (fluent), then deserialize or serialize as usual:

   ```java
   ManifestParser parser = ManifestParserFactory.getParser()
       .register(new MyExtensionConverter());
   ```

On **deserialization**, matching keys are removed from `additionalProperties` and the typed instance is stored in `parsedProperties`. On **serialization**, parsed extensions are written back into the JSON tree for those keys. Keys that are **not** covered by a registered converter remain in `additionalProperties` only.

You can register multiple converters; the first converter whose `supports` method matches wins. Extension handling walks the full manifest tree (root, parameters, composition, instantiation, nested objects), so you can target extension fields on child nodes by returning the appropriate `parentClass` from `supports`.