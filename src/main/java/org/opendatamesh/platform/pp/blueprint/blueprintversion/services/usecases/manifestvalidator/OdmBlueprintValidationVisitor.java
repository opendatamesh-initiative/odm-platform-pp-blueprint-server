package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.manifestvalidator;

import com.fasterxml.jackson.databind.JsonNode;
import org.opendatamesh.platform.pp.blueprint.manifest.model.Manifest;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestComposition;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestInstantiation;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestParameter;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestProtectedResource;
import org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation.ManifestInstantiationCompositionLayout;
import org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation.ManifestInstantiationTarget;
import org.opendatamesh.platform.pp.blueprint.manifest.model.parameter.ManifestParameterUi;
import org.opendatamesh.platform.pp.blueprint.manifest.model.parameter.ManifestParameterValidation;
import org.opendatamesh.platform.pp.blueprint.manifest.model.protectedresource.ManifestProtectedResourceIntegrity;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestInstantiationVisitor;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestParameterVisitor;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestProtectedResourceVisitor;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestVisitor;

import java.util.List;
import java.util.regex.Pattern;

class OdmBlueprintValidationVisitor implements ManifestVisitor, ManifestParameterVisitor,
        ManifestProtectedResourceVisitor, ManifestInstantiationVisitor {

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
        state.currentInstantiationStrategy = null;
        state.compositionModules.clear();

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

        if (manifest.getComposition() != null) {
            for (int i = 0; i < manifest.getComposition().size(); i++) {
                ManifestComposition composition = manifest.getComposition().get(i);
                if (composition != null) {
                    state.currentCompositionFieldPath = "composition[" + i + "]";
                    composition.accept(this);
                }
            }
        }

        if (manifest.getInstantiation() == null) {
            context.addError("instantiation", "Manifest instantiation is required");
        } else {
            state.currentInstantiationFieldPath = "instantiation";
            manifest.getInstantiation().accept(this);
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

        if (manifestParameter.getRequired() != null) {
            if (!manifestParameter.getRequired().booleanValue()) {
                context.addError(state.currentParameterRequiredFieldPath, "Parameter required must be a boolean type (true or false)");
            }
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
    }

    @Override
    public void visit(ManifestInstantiation manifestInstantiation) {
        String fieldPath = state.currentInstantiationFieldPath != null
                ? state.currentInstantiationFieldPath
                : "instantiation";

        if (manifestInstantiation.getStrategy() == null) {
            context.addError(fieldPath + ".strategy", "Instantiation strategy is required");
        }

        state.currentInstantiationStrategy = manifestInstantiation.getStrategy();

        if (manifestInstantiation.getCompositionLayout() != null) {
            if (!manifestInstantiation.getCompositionLayout().isEmpty() && state.compositionModules.isEmpty()) {
                context.addError(fieldPath + ".compositionLayout",
                        "compositionLayout requires at least one composition module");
            }

            for (int i = 0; i < manifestInstantiation.getCompositionLayout().size(); i++) {
                ManifestInstantiationCompositionLayout layout = manifestInstantiation.getCompositionLayout().get(i);
                if (layout != null) {
                    state.currentCompositionLayoutFieldPath = fieldPath + ".compositionLayout[" + i + "]";
                    layout.accept(this);
                }
            }
        }

        if (state.currentInstantiationStrategy == ManifestInstantiation.InstantiationStrategy.POLYREPO) {
            if (manifestInstantiation.getTargets() == null || manifestInstantiation.getTargets().isEmpty()) {
                context.addError(fieldPath + ".targets", "polyrepo strategy requires targets");
            }
        }

        if (manifestInstantiation.getTargets() != null) {
            for (int i = 0; i < manifestInstantiation.getTargets().size(); i++) {
                ManifestInstantiationTarget target = manifestInstantiation.getTargets().get(i);
                if (target != null) {
                    state.currentTargetFieldPath = fieldPath + ".targets[" + i + "]";
                    target.accept(this);
                }
            }
        }
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

    @Override
    public void visit(ManifestInstantiationCompositionLayout compositionLayout) {
        String fieldPath = state.currentCompositionLayoutFieldPath != null
                ? state.currentCompositionLayoutFieldPath
                : "instantiation.compositionLayout[]";

        if (!hasText(compositionLayout.getModule())) {
            context.addError(fieldPath + ".module", "compositionLayout module is required");
        } else if (!state.compositionModules.contains(compositionLayout.getModule().trim())) {
            context.addError(fieldPath + ".module", "compositionLayout module must match a composition module");
        }

        validateRequiredString(compositionLayout.getTargetPath(), fieldPath + ".targetPath",
                "compositionLayout targetPath is required");
    }

    @Override
    public void visit(ManifestInstantiationTarget target) {
        String fieldPath = state.currentTargetFieldPath != null
                ? state.currentTargetFieldPath
                : "instantiation.targets[]";

        if (state.currentInstantiationStrategy != ManifestInstantiation.InstantiationStrategy.POLYREPO) {
            return;
        }

        validateRequiredString(target.getRepositoryNamePostfix(), fieldPath + ".repositoryNamePostfix",
                "polyrepo target requires repositoryNamePostfix");

        if (target.getCreatePolicy() == null) {
            context.addError(fieldPath + ".createPolicy", "polyrepo target requires createPolicy");
        }

        if (state.hasComposition) {
            if (!hasText(target.getModule())) {
                context.addError(fieldPath + ".module", "polyrepo target requires module when composition is defined");
            } else if (!state.compositionModules.contains(target.getModule().trim())) {
                context.addError(fieldPath + ".module", "polyrepo target module must match a composition module");
            }
        } else {
            validateRequiredString(target.getSourcePath(), fieldPath + ".sourcePath",
                    "polyrepo target requires sourcePath when composition is not defined");
            validateRequiredString(target.getTargetPath(), fieldPath + ".targetPath",
                    "polyrepo target requires targetPath when composition is not defined");
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
            case INTEGER -> defaultValue.isInt() || defaultValue.isLong() || defaultValue.isBigInteger();
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
