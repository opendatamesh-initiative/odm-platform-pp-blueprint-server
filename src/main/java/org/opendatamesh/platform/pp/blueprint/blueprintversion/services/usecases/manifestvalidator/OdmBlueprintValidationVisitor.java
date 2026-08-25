package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.manifestvalidator;

import com.fasterxml.jackson.databind.JsonNode;
import org.opendatamesh.platform.pp.blueprint.manifest.model.Manifest;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestComposition;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestInstantiation;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestParameter;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestProtectedResource;
import org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation.ManifestInstantiationRepository;
import org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation.ManifestInstantiationRoot;
import org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation.ManifestTarget;
import org.opendatamesh.platform.pp.blueprint.manifest.model.parameter.ManifestParameterUi;
import org.opendatamesh.platform.pp.blueprint.manifest.model.parameter.ManifestParameterValidation;
import org.opendatamesh.platform.pp.blueprint.manifest.model.protectedresource.ManifestProtectedResourceIntegrity;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestCompositionVisitor;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestInstantiationRootVisitor;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestInstantiationVisitor;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestParameterVisitor;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestProtectedResourceVisitor;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestVisitor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

class OdmBlueprintValidationVisitor implements ManifestVisitor, ManifestParameterVisitor,
        ManifestProtectedResourceVisitor, ManifestInstantiationVisitor, ManifestInstantiationRootVisitor,
        ManifestCompositionVisitor {

    private final OdmBlueprintManifestValidatorContext context;
    private final OdmBlueprintManifestValidatorState state;

    OdmBlueprintValidationVisitor(OdmBlueprintManifestValidatorContext context) {
        this.context = context;
        this.state = new OdmBlueprintManifestValidatorState();
    }

    private static final Pattern SEMVER = Pattern.compile(
            "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)"
                    + "(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?"
                    + "(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$"
    );

    @Override
    public void visit(Manifest manifest) {
        state.hasComposition = manifest.getComposition() != null && !manifest.getComposition().isEmpty();
        state.compositionModules.clear();
        state.repositoryKeys.clear();

        if (!Manifest.SPEC_NAME.equals(manifest.getSpec())) {
            context.addError("spec", "Manifest spec must be 'odm-blueprint-manifest'");
        }

        if (!hasText(manifest.getSpecVersion())) {
            context.addError("specVersion", "Manifest specVersion is required");
        } else if (!SEMVER.matcher(manifest.getSpecVersion().trim()).matches()) {
            context.addError("specVersion", "Manifest specVersion must follow semantic versioning");
        }

        validateRequiredString(manifest.getName(), "name", "Manifest name is required");

        if (!hasText(manifest.getVersion())) {
            context.addError("version", "Manifest version is required");
        } else if (!SEMVER.matcher(manifest.getVersion().trim()).matches()) {
            context.addError("version", "Manifest version must follow semantic versioning");
        }

        List<ManifestParameter> parameters = manifest.getParameters();
        if (parameters != null) {
            for (int i = 0; i < parameters.size(); i++) {
                ManifestParameter parameter = parameters.get(i);
                if (parameter != null) {
                    if (parameter.getKey() == null || parameter.getKey().isEmpty()) {
                        context.addError("parameters[" + i + "].", "Parameter key is required");
                    }
                    state.currentParameterFieldPath = "parameters[" + i + "]";
                    state.currentParameterTypeFieldPath = state.currentParameterFieldPath + ".type";
                    state.currentParameterRequiredFieldPath = state.currentParameterFieldPath + ".required";
                    state.currentParameterDefaultFieldPath = state.currentParameterFieldPath + ".default";
                    parameter.accept(this);
                }
            }
        }

        if (manifest.getProtectedResources() != null) {
            for (int i = 0; i < manifest.getProtectedResources().size(); i++) {
                ManifestProtectedResource resource = manifest.getProtectedResources().get(i);
                if (resource != null) {
                    state.currentProtectedResourceFieldPath = "protectedResources[" + i + "]";
                    state.currentProtectedResourceIntegrityFieldPath = state.currentProtectedResourceFieldPath + ".integrity";
                    resource.accept(this);
                }
            }
        }

        if (manifest.getInstantiation() == null) {
            context.addError("instantiation", "Manifest instantiation is required");
        } else {
            state.currentInstantiationFieldPath = "instantiation";
            manifest.getInstantiation().accept(this);
        }

        if (manifest.getComposition() != null) {
            Set<String> seenModules = new HashSet<>();
            for (int i = 0; i < manifest.getComposition().size(); i++) {
                ManifestComposition composition = manifest.getComposition().get(i);
                if (composition != null) {
                    state.currentCompositionFieldPath = "composition[" + i + "]";
                    if (hasText(composition.getModule())) {
                        String module = composition.getModule().trim();
                        if (!seenModules.add(module)) {
                            context.addError(state.currentCompositionFieldPath + ".module",
                                    "Composition module values must be unique");
                        }
                    }
                    composition.accept(this);
                }
            }
        }
    }

    @Override
    public void visit(ManifestParameter manifestParameter) {
        String fieldPath = state.currentParameterFieldPath != null
                ? state.currentParameterFieldPath
                : "parameters[]";

        context.addParameterKey(manifestParameter.getKey(), fieldPath + ".key");

        if (manifestParameter.getType() == null) {
            context.addError(state.currentParameterTypeFieldPath, "Parameter type is required");
        }

        Boolean required = manifestParameter.getRequired();
        if (required != null && !Boolean.TRUE.equals(required) && !Boolean.FALSE.equals(required)) {
            context.addError(state.currentParameterRequiredFieldPath,
                    "Parameter required must be the boolean true or false");
        }

        if (manifestParameter.getDefaultValue() != null && !manifestParameter.getDefaultValue().isNull()) {
            validateDefaultValueMatchesType(manifestParameter);
        }

        if (manifestParameter.getValidation() != null) {
            manifestParameter.getValidation().accept(this);
        }

        if (manifestParameter.getUi() != null) {
            manifestParameter.getUi().accept(this);
        }
    }

    @Override
    public void visit(ManifestProtectedResource manifestProtectedResource) {
        String fieldPath = state.currentProtectedResourceFieldPath != null
                ? state.currentProtectedResourceFieldPath
                : "protectedResources[]";

        validateRequiredString(manifestProtectedResource.getPath(), fieldPath + ".path",
                "Protected resource path must be a non-empty string");

        if (manifestProtectedResource.getIntegrity() != null) {
            manifestProtectedResource.getIntegrity().accept(this);
        }
    }

    @Override
    public void visit(ManifestComposition manifestComposition) {
        String fieldPath = state.currentCompositionFieldPath != null
                ? state.currentCompositionFieldPath
                : "composition[]";

        validateRequiredString(manifestComposition.getModule(), fieldPath + ".module",
                "Composition module is required");
        validateRequiredString(manifestComposition.getBlueprintName(), fieldPath + ".blueprintName",
                "Composition blueprintName is required");
        validateRequiredString(manifestComposition.getBlueprintVersion(), fieldPath + ".blueprintVersion",
                "Composition blueprintVersion is required");

        if (hasText(manifestComposition.getModule())) {
            state.compositionModules.add(manifestComposition.getModule().trim());
        }

        List<ManifestTarget> targets = manifestComposition.getTargets();
        if (targets == null || targets.isEmpty()) {
            context.addError(fieldPath + ".targets", "Composition targets are required");
            return;
        }
        validateTargetsList(targets, fieldPath + ".targets", true);
    }

    @Override
    public void visit(ManifestInstantiation manifestInstantiation) {
        String fieldPath = state.currentInstantiationFieldPath != null
                ? state.currentInstantiationFieldPath
                : "instantiation";

        List<ManifestInstantiationRepository> repositories = manifestInstantiation.getRepositories();
        if (repositories == null || repositories.isEmpty()) {
            context.addError(fieldPath + ".repositories", "Instantiation repositories are required");
        } else {
            Set<String> seenKeys = new HashSet<>();
            for (int i = 0; i < repositories.size(); i++) {
                ManifestInstantiationRepository repository = repositories.get(i);
                String repoPath = fieldPath + ".repositories[" + i + "]";
                if (repository == null) {
                    context.addError(repoPath, "Instantiation repository entry is required");
                    continue;
                }
                state.currentTargetFieldPath = repoPath;
                repository.accept(this);
                if (hasText(repository.getKey())) {
                    String key = repository.getKey().trim();
                    if (!seenKeys.add(key)) {
                        context.addError(repoPath + ".key", "Instantiation repository keys must be unique");
                    } else {
                        state.repositoryKeys.add(key);
                    }
                }
            }
        }

        if (manifestInstantiation.getRoot() == null) {
            context.addError(fieldPath + ".root", "Instantiation root is required");
        } else {
            state.currentTargetFieldPath = fieldPath + ".root";
            manifestInstantiation.getRoot().accept(this);
        }
    }

    @Override
    public void visit(ManifestInstantiationRepository repository) {
        String fieldPath = state.currentTargetFieldPath != null
                ? state.currentTargetFieldPath
                : "instantiation.repositories[]";
        validateRequiredString(repository.getKey(), fieldPath + ".key",
                "Instantiation repository key is required");
    }

    @Override
    public void visit(ManifestInstantiationRoot root) {
        String fieldPath = state.currentTargetFieldPath != null
                ? state.currentTargetFieldPath
                : "instantiation.root";
        List<ManifestTarget> targets = root.getTargets();
        if (targets == null) {
            context.addError(fieldPath + ".targets", "Instantiation root.targets is required");
            return;
        }
        // Empty array is allowed (pure orchestration parents).
        if (!targets.isEmpty()) {
            validateTargetsList(targets, fieldPath + ".targets", false);
        }
    }

    @Override
    public void visit(ManifestTarget target) {
        String fieldPath = state.currentTargetFieldPath != null
                ? state.currentTargetFieldPath
                : "targets[]";

        validateRequiredString(target.getRepository(), fieldPath + ".repository",
                "Target repository is required");
        if (hasText(target.getRepository()) && !state.repositoryKeys.contains(target.getRepository().trim())) {
            context.addError(fieldPath + ".repository",
                    "Target repository must match an instantiation.repositories[].key");
        }
        validateRelativePath(target.getSourcePath(), fieldPath + ".sourcePath");
        validateRelativePath(target.getPath(), fieldPath + ".path");
    }

    @Override
    public void visit(ManifestParameterValidation validation) {
        // Optional validation rules can be added here later.
    }

    @Override
    public void visit(ManifestParameterUi ui) {
        // Optional ui rules can be added here later.
    }

    @Override
    public void visit(ManifestProtectedResourceIntegrity integrity) {
        String fieldPath = state.currentProtectedResourceIntegrityFieldPath != null
                ? state.currentProtectedResourceIntegrityFieldPath
                : "protectedResources[].integrity";

        validateRequiredString(integrity.getAlgorithm(), fieldPath + ".algorithm",
                "Protected resource integrity algorithm must be a non-empty string");
        validateRequiredString(integrity.getValue(), fieldPath + ".value",
                "Protected resource integrity value must be a non-empty string");
    }

    private void validateTargetsList(List<ManifestTarget> targets, String fieldPath, boolean compositionContext) {
        boolean requireExplicitSourcePath = targets.size() > 1;
        for (int i = 0; i < targets.size(); i++) {
            ManifestTarget target = targets.get(i);
            String targetPath = fieldPath + "[" + i + "]";
            if (target == null) {
                context.addError(targetPath, "Target entry is required");
                continue;
            }
            if (requireExplicitSourcePath && !hasText(target.getSourcePath())) {
                context.addError(targetPath + ".sourcePath",
                        "sourcePath is required when targets contains more than one entry");
            }
            state.currentTargetFieldPath = targetPath;
            if (compositionContext) {
                target.accept((ManifestCompositionVisitor) this);
            } else {
                target.accept((ManifestInstantiationRootVisitor) this);
            }
        }
    }

    private void validateRelativePath(String path, String fieldPath) {
        if (!hasText(path)) {
            return;
        }
        String trimmed = path.trim();
        if (trimmed.startsWith("/") || trimmed.contains("..")) {
            context.addError(fieldPath, "Repository paths must be relative and must not contain '..'");
        }
    }

    private void validateDefaultValueMatchesType(ManifestParameter manifestParameter) {
        JsonNode defaultValue = manifestParameter.getDefaultValue();
        ManifestParameter.ManifestParameterType type = manifestParameter.getType();

        if (type == null) {
            context.addError(state.currentParameterTypeFieldPath,
                    "Parameter default type is set. Type is required to validate the default value");
            return;
        }

        boolean isValid = switch (type) {
            case STRING -> defaultValue.isTextual();
            case INTEGER -> defaultValue.isIntegralNumber();
            case BOOLEAN -> defaultValue.isBoolean();
            case ARRAY -> defaultValue.isArray();
            case OBJECT -> defaultValue.isObject();
        };

        if (!isValid) {
            context.addError(state.currentParameterDefaultFieldPath,
                    String.format("Default value for parameter '%s' must match type '%s'",
                            parameterIdentifier(manifestParameter.getKey()),
                            manifestParameter.getType().name().toLowerCase()));
        }
    }

    private String parameterIdentifier(String key) {
        return hasText(key) ? key.trim() : "<unknown>";
    }

    private void validateRequiredString(String value, String fieldPath, String message) {
        if (!hasText(value)) {
            context.addError(fieldPath, message);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
