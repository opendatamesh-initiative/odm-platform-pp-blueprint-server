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
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Structural validator for an odm-blueprint-manifest.
 *
 * <p><b>Navigation vs validation (do not mix these up):</b>
 * <ul>
 *   <li><b>Navigate</b> the manifest object graph only via {@code child.accept(this)} (or the
 *       typed visitor interface) from inside {@code visit(...)} methods. Do not factor tree walking
 *       into private procedural navigators.</li>
 *   <li><b>Validate locally</b> field / shape rules for the current node inside its {@code visit}.
 *       Private helpers may implement those checks, but must not walk visitable children.
 *       Non-visitable leaf data (e.g. {@code parameterMapping} {@code Map<String, JsonNode>}) is
 *       validated here until it has its own model + {@code accept}/{@code visit} types.</li>
 *   <li><b>Validate globally</b> cross-node invariants after the walk: collect facts on
 *       {@link OdmBlueprintManifestValidatorState} during visits, then run post-pass checks at the
 *       end of {@link #visit(Manifest)} (unused repository keys, duplicate destinations, nested
 *       path prefixes).</li>
 * </ul>
 */
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
                    + "(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$");

    @Override
    public void visit(Manifest manifest) {
        state.hasComposition = manifest.getComposition() != null && !manifest.getComposition().isEmpty();
        state.compositionModules.clear();
        state.repositoryKeys.clear();
        state.usedRepositoryKeys.clear();
        state.routeDestinations.clear();

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

        validateUnusedRepositoryKeys();
        validateDuplicateDestinations();
        validateNestedPathPrefixes();
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

        validateParameterMapping(manifestComposition.getParameterMapping(), fieldPath);

        List<ManifestTarget> targets = manifestComposition.getTargets();
        if (targets == null || targets.isEmpty()) {
            context.addError(fieldPath + ".targets", "Composition targets are required");
            return;
        }
        String targetsPath = fieldPath + ".targets";
        boolean requireExplicitSourcePath = targets.size() > 1;
        ManifestCompositionVisitor compositionVisitor = this;
        for (int i = 0; i < targets.size(); i++) {
            ManifestTarget target = targets.get(i);
            String targetPath = targetsPath + "[" + i + "]";
            if (target == null) {
                context.addError(targetPath, "Target entry is required");
                continue;
            }
            if (requireExplicitSourcePath && !hasText(target.getSourcePath())) {
                context.addError(targetPath + ".sourcePath",
                        "sourcePath is required when targets contains more than one entry");
            }
            state.currentTargetFieldPath = targetPath;
            target.accept(compositionVisitor);
        }
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

        if (!hasText(root.getRepository())) {
            context.addError(fieldPath + ".repository",
                    "instantiation.root.repository is required",
                    "Set instantiation.root.repository to a declared instantiation.repositories[].key.");
        } else {
            String rootKey = root.getRepository().trim();
            if (!state.repositoryKeys.contains(rootKey)) {
                context.addError(fieldPath + ".repository",
                        "instantiation.root.repository must match an instantiation.repositories[].key",
                        "Use a key declared in instantiation.repositories[].key.");
            }
        }

        List<ManifestTarget> targets = root.getTargets();
        if (targets == null || targets.isEmpty()) {
            context.addError(fieldPath + ".targets", "instantiation.root.targets must be non-empty",
                    "Add at least one instantiation.root.targets entry that references a declared repository key.");
            return;
        }
        String targetsPath = fieldPath + ".targets";
        boolean requireExplicitSourcePath = targets.size() > 1;
        ManifestInstantiationRootVisitor rootVisitor = this;
        for (int i = 0; i < targets.size(); i++) {
            ManifestTarget target = targets.get(i);
            String targetPath = targetsPath + "[" + i + "]";
            if (target == null) {
                context.addError(targetPath, "Target entry is required");
                continue;
            }
            if (requireExplicitSourcePath && !hasText(target.getSourcePath())) {
                context.addError(targetPath + ".sourcePath",
                        "sourcePath is required when targets contains more than one entry");
            }
            state.currentTargetFieldPath = targetPath;
            target.accept(rootVisitor);
        }
    }

    @Override
    public void visit(ManifestTarget target) {
        String fieldPath = state.currentTargetFieldPath != null
                ? state.currentTargetFieldPath
                : "targets[]";

        validateRequiredString(target.getRepository(), fieldPath + ".repository",
                "Target repository is required");
        if (hasText(target.getRepository())) {
            String repositoryKey = target.getRepository().trim();
            if (!state.repositoryKeys.contains(repositoryKey)) {
                context.addError(fieldPath + ".repository",
                        "Target repository must match an instantiation.repositories[].key",
                        "Use a key declared in instantiation.repositories[].key.");
            } else {
                state.usedRepositoryKeys.add(repositoryKey);
                state.routeDestinations.add(new OdmBlueprintManifestValidatorState.RouteDestination(
                        repositoryKey,
                        normalizePath(target.getPath()),
                        fieldPath));
            }
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

    /**
     * Local shape check for non-visitable {@code parameterMapping} entries (JsonNode values).
     * Not tree navigation — do not treat this as a substitute for {@code accept}/{@code visit}.
     */
    private void validateParameterMapping(Map<String, JsonNode> parameterMapping, String fieldPath) {
        if (parameterMapping == null || parameterMapping.isEmpty()) {
            return;
        }
        for (Map.Entry<String, JsonNode> entry : parameterMapping.entrySet()) {
            String entryPath = fieldPath + ".parameterMapping[" + entry.getKey() + "]";
            JsonNode mappingNode = entry.getValue();
            if (mappingNode == null || !mappingNode.isObject()) {
                context.addError(entryPath, "parameterMapping entry must be a JSON object",
                        "Use { $param: parentKey } or { value: actualValue }.");
                continue;
            }

            boolean hasParam = mappingNode.has("$param");
            boolean hasValue = mappingNode.has("value");

            if (hasParam && hasValue) {
                context.addError(entryPath,
                        "The discriminants $param and value are mutually exclusive; keep only one.");
                continue;
            }
            if (!hasParam && !hasValue) {
                context.addError(entryPath, "parameterMapping entry must declare $param or value",
                        "Use { $param: key } or { value: actualValue }.");
                continue;
            }

            if (hasParam) {
                JsonNode paramNode = mappingNode.get("$param");
                if (paramNode == null || !paramNode.isTextual() || !hasText(paramNode.asText())) {
                    context.addError(entryPath + ".$param",
                            "$param must be a non-empty textual string",
                            "Use { $param: parentKey } or { value: actualValue }.");
                    continue;
                }
                String parentKey = paramNode.asText();
                if (!context.containsParameterKey(parentKey)) {
                    context.addError(entryPath + ".$param",
                            "Parent parameter key '" + parentKey + "' is not declared in parameters",
                            "Fix the mapping or declare the parameter on the parent manifest.");
                }
            }
            // value: any JsonNode (including null) is accepted; extra keys are ignored
        }
    }

    // --- Post-pass global invariants (after accept/visit walk; operate on collected state only) ---

    private void validateUnusedRepositoryKeys() {
        for (String key : state.repositoryKeys) {
            if (!state.usedRepositoryKeys.contains(key)) {
                context.addError("instantiation.repositories",
                        "Repository key '" + key + "' is not referenced by any target",
                        "Declare a route in instantiation.root.targets or composition[].targets that uses this key, or remove the unused key.");
            }
        }
    }

    private void validateDuplicateDestinations() {
        Set<String> seen = new HashSet<>();
        for (OdmBlueprintManifestValidatorState.RouteDestination destination : state.routeDestinations) {
            String signature = destination.repositoryKey() + '\0' + destination.normalizedPath();
            if (!seen.add(signature)) {
                context.addError(destination.fieldPath(),
                        "Duplicate destination (repository, path) pair",
                        "Make destination (repository, path) pairs unique.");
            }
        }
    }

    private void validateNestedPathPrefixes() {
        List<OdmBlueprintManifestValidatorState.RouteDestination> destinations = state.routeDestinations;
        for (int i = 0; i < destinations.size(); i++) {
            OdmBlueprintManifestValidatorState.RouteDestination left = destinations.get(i);
            String leftCompare = pathForPrefixCompare(left.normalizedPath());
            for (int j = i + 1; j < destinations.size(); j++) {
                OdmBlueprintManifestValidatorState.RouteDestination right = destinations.get(j);
                if (!left.repositoryKey().equals(right.repositoryKey())) {
                    continue;
                }
                String rightCompare = pathForPrefixCompare(right.normalizedPath());
                if (leftCompare.equals(rightCompare)) {
                    continue;
                }
                if (isPathPrefix(leftCompare, rightCompare) || isPathPrefix(rightCompare, leftCompare)) {
                    context.addError(right.fieldPath(),
                            "Destination paths nest under each other on the same repository key",
                            "Use sibling destinations that do not nest under each other on the same repository key.");
                }
            }
        }
    }

    private void validateRelativePath(String path, String fieldPath) {
        if (!hasText(path)) {
            return;
        }
        String trimmed = path.trim();
        if (trimmed.startsWith("/") || trimmed.contains("..")) {
            context.addError(fieldPath, "Repository paths must be relative and must not contain '..'",
                    "Use a relative path without '..' or a leading '/'.");
        }
    }

    private String normalizePath(String path) {
        if (!hasText(path)) {
            return "./";
        }
        String normalized = path.trim().replace('\\', '/');
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isEmpty() || ".".equals(normalized)) {
            return "./";
        }
        return normalized;
    }

    private String pathForPrefixCompare(String normalizedPath) {
        String path = normalizedPath;
        if (path.startsWith("./")) {
            path = path.substring(2);
        }
        if (path.isEmpty() || ".".equals(path)) {
            return "";
        }
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private boolean isPathPrefix(String prefix, String path) {
        if (prefix.isEmpty()) {
            return true;
        }
        return path.equals(prefix) || path.startsWith(prefix + "/");
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
