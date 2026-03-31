# Publish blueprint — proposal

## Goal

Expose a dedicated use case that **publish a new blueprint version**. The client sends one command body; the server creates the `BlueprintVersion` (including blueprint metadata) ONLY, returning the created **blueprintVersion.**

This complements the existing **hidden** CRUD endpoint on `BlueprintVersionsController` by offering a single public “publish” flow with **additional semantic validation** on blueprint version parameters (tag, spec, specVersion, versionNumber and content) that does **not** belong in generic CRUD `validate` hooks.

## REST contract


| Item           | Value                                                   |
| -------------- | ------------------------------------------------------- |
| Method / path  | `POST /api/v2/pp/blueprint/blueprints-versions/publish` |
| Success status | `201 Created`                                           |
| Request body   | `PublishBlueprintVersionCommandRes` (see below)         |
| Response body  | `PublishBlueprintVersionResponseRes`                    |


OpenAPI: document the endpoint on a dedicated `BlueprintVersionsUseCaseController`; use `@Schema` on the command and result types.

## Command resources (`PublishBlueprintVersionCommandRes` and `PublishBlueprintVersionResponseRes`)

PublishBlueprintVersionCommandRes:
{
  "blueprintVersion": {
    "name": "...",
    "description": "...",
    "readme": "...",
    "tag": "...",
    "spec": "...",
    "specVersion": "...",
    "content": { },
    "blueprint": {
      "name": "...",
      or "uuid", reconcile with findOne in the use case 
    }
  }
}

PublishBlueprintVersionResponseRes
Same envelope as above; uuid (and typically createdAt / updatedAt, audit ids if you set them) are populated on blueprintVersion and on the nested blueprint as persisted.

Package: `rest.v2.resources.blueprintversion.usecases.publish` (or equivalent under `blueprintversion` aggregate).

The command must carry **everything needed** to build:

1. A new `BlueprintVersion` — same information as `BlueprintVersionRes` used for create (name, displayName, description, tag, specVersion, content, versionNumber, `blueprint` nested object, etc.). Do **not** send a blueprintVersion `uuid` (server-generated). When publishing a new blueprint version, the blueprintVersion.versionNumber must be extracted from the manifest from the field `version`.

Shape (conceptual):

- `blueprintVersion`: `BlueprintVersionRes` (subset acceptable if documented; must match what `BlueprintVersionService.createResource` needs).
- Blueprint fields: either a nested object mirroring blueprint fields, or top-level fields grouped under a clear prefix — choose one structure and keep mappers straightforward.
- Blueprint repo fields: either a nested object mirroring blueprint repo fields, or top-level fields grouped under a clear prefix — choose one structure and keep mappers straightforward.

## Validation split (no duplication with CRUD)


| Layer                                                          | Responsibility                                                                                                                                                                                                                                                                                                                                                                                                              |
| -------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `**BlueprintServiceImpl` / `BlueprintVersionCrudServiceImpl`** | Keeps existing **required fields**, **length limits**, **enums**, **reconcile** (e.g. load parent blueprint), **natural keys** (unique name / unique version per blueprint). **Do not re-implement these rules** in the use case.                                                                                                                                                                                           |
| **Publish use case**                                           | **Additional semantic checks** only validate spec, specVersion, verify if already exists a version with the same name and versionNumber, verify if already exists version that has the same name and tag. Centralize these in a small validator or port so the use case stays clear and also verify the manifest content against the specification. These validation could be done in the BlueprintVersionPublisher.java |


If a semantic rule is already fully enforced by CRUD (same exception type and message), **do not** repeat it.

## Application architecture

Follow `agentspecs/guidelines/USE_CASE_IMPLEMENTATION.md`:

1. `**PublishBlueprintVersionCommand`** (record) — domain command built from `PublishBlueprintVersionCommandRes` via mappers.
2. `**PublishBlueprintVersionPresenter`** — e.g. `presentPublished(BueprintVersion)` or similar; result mapped to `PublishBlueprintVersionResponseRes`.
3. `**PublishBlueprintVersion`** (implements `UseCase`) — orchestration only: run semantic validation → call outbound port(s) that delegate to `**BlueprintVersionService.createResource`**  with `blueprintVersionUuid` set, inside `TransactionalOutboundPort` so create in one transaction.
4. `**PublishBlueprintVersionFactory`** — wires ports + `TransactionalOutboundPort`.
5. `**BlueprintVersionUseCasesService`** (or `PublishBlueprintVersionUseCasesService`) — maps REST DTO → command, runs factory + `execute()`, maps to `PublushBlueprintVersionResponseRes`.

### Manifest validator and auto-fill

Implements this classes following the Visitor pattern which split the pipeline of validation between auto-fill the missing required fields and after that validate the normalized content

Validator (new `package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.manifestvalidator`)

1. `**ManifestValidator`**: public interface (validateManifest(JsonNode))
2. `**ManifestValidatorFactory`**: choose the implementation for spec/version (now odm-blueprint-manifest)
3. `**OdmBlueprintManifestValidator`**: orchestrate -> deserialize with Manifest parser, controls on root and start visitors
4. `**OdmBlueprintManifestValidationContext`**: accumulate errors and check the state for the uniqueness on the keys
5. `**OdmBlueprintManifestValidationErrorMessage`**: structured message error
6. `**OdmBlueprintManifestVisitorState`**: mutable state shared during the visit
7. `**OdmBlueprintManifestVisitorHelpers`**: helper for required fields
8. `**OdmBlueprintManifestValidationVisitor`**: main visitor that implement which coordinate the rest
9. `**OdmBlueprintManifestInfoValidationVisitor`**: info visitor (spec, specVersion, name, displayName, version, description, parameters)
10. `**OdmBlueprintManifestParametersValidationVisitor`**: parameters visitor (key, type, required, default, validation)
11. `**OdmBlueprintManifestParametersValidationValidationVisitor`**: validation visitor inside parameter section (allowedValues, format, pattern, min, max)
12. `**OdmBlueprintManifestParametersUiValidationVisitor`**: ui visitor validation inside parameters section (group, label, description, formType). Don't implement semantic validation but set the class to works as minimum
13. `**OdmBlueprintManifestProtectedResourcesValidationVisitor`**: protected resources visitor (path, integrity). Don't implement semantic validation but set the class to works as minimum
14. `**OdmBlueprintManifestCompositionValidationVisitor`**: composition visitor (module, blueprintName, blueprintVersion, parameterMapping). Don't implement semantic validation but set the class to works as minimum
15. `**OdmBlueprintManifestInstantiationValidationVisitor`**: instantiation visitor (strategy, compositionLayout, targets). Don't implement semantic validation but set the class to works as minimum

To visit, use the classes contained in `src/main/java/org/opendatamesh/platform/pp/blueprint/manifest/visitors` and other classes contained in the same package if useful which will be implemented inside the Visitor classes (use `implements`)

The validation rules that must be followed in the correspective visitors are:

- `spec`: must be `odm-blueprint-manifest`.
- `specVersion`: must be `1.x.x`.
- `name`: required, must be string
- `version`: required, must be a string and follow semantic versioning -> follow this regex `^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$)`
- parameters: this field is optional, but if it's defined must follow these rules:
  - key: must be present and string
  - type: must be an enum with these following types: string, integer, boolean, array, object. 
  - required: must be a Boolean type (true or false)
  - default: must match the type defined in the type field. For example if I defined a type integer It can't be defined a "string" default
- protectedResources: this field is optional, but if it's defined must follow these rules:
  - path: must be a string and must be a path (e.g. infrastructure/*)
  - integrity: this field is optional, but if it's defined must follow these rules:
    - algorithm: must be a string 
    - value: must be a string
- composition: this field is optional, but if it's defined must follow these rules:
  - module: required, must be a string
  - blueprintName: required, must be a string
  - blueprintVersion: required, must be a string
- instantiation: must be defined and follow these rules:
  - strategy: must be an enum that can assumes value as monorepo or polyrepo
  - compositionLayout: this field is optional (array of object) but if it's defined must follow this rules:
    - module: required, must match a composition[].module value.
    - targetPath: required, must be string
  - targets: this field is mandatory only if the strategy defined is `polyrepo`
    - repositoryNamePostfix: if `polyrepo` required and must be string
    - createPolicy: if `polyrepo` required and must follow thise enum -> create_if_missing, must_exist
    - module: mandatory if `polyrepo` and `composition` are defined. Must be a string
    - sourcePath: mandatory if `polyrepo` is defined and `composition` is not defined. Must be a string
    - targetPath: mandatory if `polyrepo` is defined and `composition` is not defined. Must be a string

Auto fill (inside package `src/main/java/org/opendatamesh/platform/pp/blueprint/blueprintversion/usecases/publish`)

1. `**OdmBlueprintManifestFieldGenerationVisitor`**: a unique class that implements the visitor classes contained in `src/main/java/org/opendatamesh/platform/pp/blueprint/manifest/visitors` that only fills field if missing in manifest (DOES NOT perform validation)
2. `**OdmBlueprintManifestFieldGenerator`**: utils class for generating fields according to odm-blueprint-manifest specification `src/main/java/org/opendatamesh/platform/pp/blueprint/manifest/README.md`

The required fields that needs to be filled if missing will be the following:

- spec: default to `odm-blueprint-manifest`
- specVersion: default to `v1`
- name: default to BlueprintName
- version: default to `1.0.0`
- parameters: optional field but if it's present must follow these auto-fills if some fields are not present:
  - key: default to `parameterKey` + uuid
  - type: default to `string`
  - required: default to `false`
- instantiation: default to `monorepo`

### General information for application architecture

The general flow for the use case must follow these steps -> retrive blueprint (from name or uuid), validate the fields of the BlueprintVersion, autofill the manifest, validate the autofilled generated manifest, if everything is ok then could publish a version. These would be orchestrated inside the execute() method in doInTransaction.

**Controller** — thin: single method calling the use cases service; no business rules.

## Out of scope for this change

- Changing CRUD validation behavior except where needed to avoid duplication (prefer leaving CRUD as-is).
- Listing or search behavior.
- Replacing existing `POST /blueprints` or `POST /blueprints-versions` CRUD endpoints (they remain; registration is the documented public flow).
- Creating Blueprint and BlueprintRepo during the use case

## Testing

Integration tests:  `` dedicated `*UseCaseControllerIT` against `specs.md` (Gherkin). Trace each test to a scenario in `specs.md` via a comment on the test method.
Unit tests: `` dedicated `OdmBlueprintManifestValidatorTest` against `specs.md` (Gherkin). Trace each test to a scenario in `specs.md` via a comment on the test method that test the validation of the manifest. For these tests create specifically resources inside `src/test/resources/test-data` with .yaml files that could simulate the cases.

## References

- `BlueprintVersionController` base path: `/api/v2/pp/blueprint/blueprints-versions`
- `BlueprintServiceImpl.validate` / `BlueprintVersionCrudServiceImpl.validate` — existing CRUD rules
- `agentspecs/guidelines/USE_CASE_IMPLEMENTATION.md` — layers and naming
- `src/main/java/org/opendatamesh/platform/pp/blueprint/manifest/README.md` - rules for specification of the manifest blueprint