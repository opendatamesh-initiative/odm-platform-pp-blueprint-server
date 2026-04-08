# Specs - Populate target repository from blueprint version

## Scope

This specification defines testable behavior for populating a **single** target repository from a selected blueprint version, matching the current use-case implementation.

- **In scope:** monorepo strategy, no composition, Apache Velocity rendering (`.vm` → output files), Git provider **clone** (`readRepository`) for source (tag) and target (branch), commit/push, lineage under `.odm/blueprint/`, REST command contract.
- **In scope:** target repositories are expected to exist already; creation is external.
- **Out of scope:** composition, polyrepo / multiple targets in one run, repository provisioning, advanced merge/conflict handling.

## Feature: Instantiate blueprint into target repository

### Scenario: Successful monorepo population and push

**Given** a persisted blueprint version with manifest `instantiation.strategy = monorepo` and no composition (or empty composition)  
**And** the request identifies the blueprint by **name** and **version number**  
**And** `targetRepositories` contains **exactly one** entry with `type` **root** and a valid repository reference  
**And** the request provides valid values for all required manifest parameters (or defaults where defined)  
**And** Git provider operations succeed for source (tag) and target (branch) materialization and push  
**When** the client calls `POST /api/v2/pp/blueprint/blueprints-versions/instantiate` with appropriate JSON body and Git auth headers  
**Then** the system loads the blueprint version, validates manifest and parameters, and initializes the Git provider from the blueprint’s repository settings and request headers  
**And** the system clones the blueprint source at the version **tag** and the target at the chosen **branch** (or default branch)  
**And** the system renders `*.vm` templates with Apache Velocity using resolved parameters and copies the result into the target working tree  
**And** the system writes manifest lineage to `.odm/blueprint/blueprint-manifest.yaml` and moves the blueprint README into `.odm/blueprint/` when configured in repository metadata  
**And** the system stages, commits, and pushes changes on the target  
**And** returns **`200 OK`** (response body may be minimal until presenters expose detailed metadata)

### Scenario: Target branch defaults to repository default

**Given** a valid instantiate request where the target entry omits `branch`  
**When** the Git adapter resolves the target pointer  
**Then** the target repository’s **default branch** is used for clone/commit/push

### Scenario: Target branch can be overridden

**Given** a valid instantiate request where the target entry includes `branch`  
**When** the Git adapter resolves the target pointer  
**Then** that branch is used instead of the default

### Scenario: Commit author can be customized with default fallback

**Given** a valid instantiate request with optional `commitAuthorName` and `commitAuthorEmail`  
**When** the endpoint runs and a commit is created on the target  
**Then** the commit uses the provided name and email when both are non-blank  
**And** when omitted or blank, the system uses the default author identity (`odm-blueprint-server` / `odm-blueprint-server@local`)

### Scenario: Instantiation strategy is derived from manifest metadata

**Given** a valid request payload **without** any client-provided instantiation method field  
**When** the endpoint is invoked  
**Then** monorepo vs unsupported modes are determined from the selected manifest  
**And** the request is not rejected merely for lacking an explicit instantiation method field

### Scenario: Exactly one root target is required in this phase

**Given** a request with zero target repositories, more than one target, or a single target whose `type` is not `root`  
**When** the endpoint is invoked  
**Then** the system returns **`400 Bad Request`**  
**And** no successful Git mutation completes for that request

### Scenario: Missing required parameters are rejected

**Given** the selected manifest declares required parameters without defaults  
**And** at least one such parameter is missing in the request  
**When** the endpoint is invoked  
**Then** the system returns **`400 Bad Request`**  
**And** no file write is performed in the target repository working tree

### Scenario: Invalid parameter types or constraints are rejected

**Given** parameter values violate declared manifest type or validation constraints  
**When** the endpoint is invoked  
**Then** the system returns **`400 Bad Request`**  
**And** no rendering or target write operation is executed

### Scenario: Unsupported composition manifests are rejected

**Given** the selected blueprint manifest declares a non-empty `composition` section  
**When** the endpoint is invoked  
**Then** the system returns **`400 Bad Request`**  
**And** no clone, render, copy, commit, or push is performed

### Scenario: Unsupported non-monorepo strategy is rejected

**Given** the selected blueprint manifest has an instantiation strategy other than **monorepo**  
**When** the endpoint is invoked  
**Then** the system returns **`400 Bad Request`**  
**And** no clone, render, copy, commit, or push is performed

### Scenario: Git operation failures are surfaced as client or server errors per global handling

**Given** the request payload is otherwise valid  
**And** a Git provider operation fails (e.g. clone, commit, push)  
**When** the endpoint is invoked  
**Then** the failure is mapped according to the application’s exception handling (e.g. `GitOperationException` and related types)  
**And** the target is not left in an undefined success state for that request

### Scenario: Blueprint README declared in repository metadata is not left at repository root

**Given** the blueprint repository metadata defines a README path  
**And** that README exists in the materialized source tree  
**When** population completes successfully  
**Then** that README is not present at its original path in the target output  
**And** it is placed under **`.odm/blueprint/`** (same filename) when the file existed in the temp tree

### Scenario: Manifest lineage snapshot is persisted under `.odm/blueprint/`

**Given** population runs successfully  
**When** generated files are written to the target repository tree  
**Then** a YAML snapshot is written to **`.odm/blueprint/blueprint-manifest.yaml`** containing the stored blueprint version manifest content (including blueprint identity and resolved parameter context as serialized in that snapshot)


## High-level acceptance requirements

- **Endpoint:** `POST /api/v2/pp/blueprint/blueprints-versions/instantiate`
- **Request:** `blueprintName`, `blueprintVersionNumber`, exactly one `targetRepositories` entry with `type: root`, optional `branch`, `parameters`, optional commit author fields; Git auth via request headers.
- **Contract:** `targetRepositories` remains a list for forward compatibility; validation enforces the single-`root` rule for this phase.
- **Repositories:** target repository references use the same repository model as other Git provider APIs (`RepositoryRes` → domain `Repository`).
- **Orchestration:** controller delegates to `BlueprintVersionUseCasesService` and the `InstantiateBlueprintVersion` use case; no orchestration logic in the controller beyond mapping.
