package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.Email;
import org.opendatamesh.platform.git.model.Repository;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.BlueprintRepo;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.BlueprintRepoProviderType;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.InstantiationScenarioResolver;
import org.opendatamesh.platform.pp.blueprint.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.blueprint.exceptions.InternalException;
import org.opendatamesh.platform.pp.blueprint.manifest.model.Manifest;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestComposition;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestInstantiation;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestParameter;
import org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation.ManifestInstantiationRepository;
import org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation.ManifestInstantiationRoot;
import org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation.ManifestTarget;
import org.opendatamesh.platform.pp.blueprint.manifest.model.parameter.ManifestParameterValidation;
import org.opendatamesh.platform.pp.blueprint.manifest.parser.ManifestParserFactory;
import org.springframework.util.StringUtils;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.lang.reflect.Array;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

class InstantiateBlueprintVersionOdmBlueprintManifestOutboundPortImpl
        implements InstantiateBlueprintVersionManifestOutboundPort {

    /**
     * Sentinel {@code sourceId} for the parent blueprint repo in {@link InstantiationRoute}
     * and {@link SourceRepositoryDto}, so it never collides with composition module aliases.
     */
    static final String PARENT_SOURCE_ID = "__parent__";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    /**
     * Matches manifest schema versions used in the platform (e.g. {@code v1},
     * {@code 1.0.0}).
     */
    private static final String VERSION = "(v1|1\\.\\d+\\.\\d+.*)";
    private static final LocalValidatorFactoryBean SPRING_VALIDATOR = buildValidator();

    private static final String HINT_RELATIVE_PATH = "Use a relative path without '..' or a leading '/'.";
    private static final String HINT_NON_EMPTY_ROOT_TARGETS =
            "Add at least one instantiation.root.targets entry that references a declared repository key.";
    private static final String HINT_UNUSED_KEY =
            "Declare a route in instantiation.root.targets or composition[].targets that uses this key, or remove the unused key.";
    private static final String HINT_UNKNOWN_REPOSITORY = "Use a key declared in instantiation.repositories[].key.";
    private static final String HINT_DUPLICATE_DESTINATION = "Make destination (repository, path) pairs unique.";
    private static final String HINT_NESTED_PATH =
            "Use sibling destinations that do not nest under each other on the same repository key.";
    private static final String HINT_SUPPLY_ALL_TARGETS =
            "Supply targetRepositories for every instantiation.repositories[].key.";
    private static final String HINT_SEND_EACH_TARGET_ONCE = "Send each targetId once.";
    private static final String HINT_MATCH_TARGET_ID = "Match targetId to instantiation.repositories[].key.";
    private static final String HINT_PROVIDER_MISMATCH =
            "Composition modules must use the same Git provider type and base URL as the parent.";
    private static final String HINT_ROOT_REPOSITORY =
            "Set instantiation.root.repository to a declared instantiation.repositories[].key.";

    private static LocalValidatorFactoryBean buildValidator() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return validator;
    }

    @Override
    public List<InstantiationValidationIssue> collectValidationIssues(
            String spec,
            String specVersion,
            JsonNode content,
            Map<String, JsonNode> parameters,
            List<TargetRepositoryDto> targetRepositories) {
        List<InstantiationValidationIssue> issues = new ArrayList<>();

        if (!(Manifest.SPEC_NAME.equalsIgnoreCase(spec)
                && specVersion != null
                && specVersion.matches(VERSION))) {
            issues.add(new InstantiationValidationIssue(
                    "spec",
                    "Unsupported blueprint manifest spec '%s' / version '%s'".formatted(spec, specVersion),
                    "Use spec '%s' with a supported specVersion matching %s."
                            .formatted(Manifest.SPEC_NAME, VERSION)));
        }

        Manifest manifest;
        try {
            manifest = deserialize(content);
        } catch (IOException e) {
            return List.of(new InstantiationValidationIssue(
                    "content",
                    "Unable to parse blueprint manifest content",
                    "Provide a valid odm-blueprint-manifest JSON/YAML document."));
        }

        collectStructuralIssues(manifest, issues);
        validateParameters(manifest, parameters == null ? Map.of() : parameters, issues);
        validateTargetRepositoryMap(manifest, targetRepositories, issues);
        return issues;
    }

    @Override
    public List<SourceRepositoryDto> retrieveAllSourceRepositories(
            BlueprintVersion parentVersion,
            JsonNode manifest,
            Map<String, BlueprintVersion> modulesByAlias) {
        List<SourceRepositoryDto> sources = new ArrayList<>();

        BlueprintRepo parentRepo = parentVersion.getBlueprint().getBlueprintRepo();
        sources.add(new SourceRepositoryDto(
                PARENT_SOURCE_ID,
                parentVersion.getTag(),
                toGitRepository(parentRepo)));

        if (modulesByAlias == null || modulesByAlias.isEmpty()) {
            return sources;
        }

        String parentProviderType = providerTypeName(parentRepo);
        String parentBaseUrl = trimToEmpty(parentRepo == null ? null : parentRepo.getProviderBaseUrl());

        for (Map.Entry<String, BlueprintVersion> entry : modulesByAlias.entrySet()) {
            String alias = entry.getKey();
            BlueprintVersion moduleVersion = entry.getValue();
            BlueprintRepo moduleRepo = moduleVersion.getBlueprint().getBlueprintRepo();
            sources.add(new SourceRepositoryDto(
                    alias,
                    moduleVersion.getTag(),
                    toGitRepository(moduleRepo)));

            String moduleProviderType = providerTypeName(moduleRepo);
            String moduleBaseUrl = trimToEmpty(moduleRepo == null ? null : moduleRepo.getProviderBaseUrl());
            if (!Objects.equals(parentProviderType, moduleProviderType) || !Objects.equals(parentBaseUrl, moduleBaseUrl)) {
                throw new BadRequestException("composition[module=%s]".formatted(alias) + "Composition module Git provider type or base URL does not match the parent" + HINT_PROVIDER_MISMATCH);
            }
        }
        return sources;
    }

    @Override
    public List<InstantiationRoute> flattenRoutes(JsonNode content) {
        Manifest manifest = parse(content);
        List<InstantiationRoute> routes = new ArrayList<>();
        for (ManifestTarget target : manifest.getInstantiation().getRoot().getTargets()) {
            routes.add(new InstantiationRoute(
                    PARENT_SOURCE_ID,
                    defaultPath(target.getSourcePath()),
                    target.getRepository(),
                    defaultPath(target.getPath()),
                    true));
        }
        if (manifest.getComposition() != null) {
            for (ManifestComposition composition : manifest.getComposition()) {
                String alias = composition.getModule();
                for (ManifestTarget target : composition.getTargets()) {
                    routes.add(new InstantiationRoute(
                            alias,
                            defaultPath(target.getSourcePath()),
                            target.getRepository(),
                            defaultPath(target.getPath()),
                            false));
                }
            }
        }
        return routes;
    }

    @Override
    public String retrieveRootTargetRepositoryKey(JsonNode content) {
        Manifest manifest = parse(content);
        ManifestInstantiationRoot root = manifest.getInstantiation().getRoot();
        if (root == null || !StringUtils.hasText(root.getRepository())) {
            throw new InternalException(
                    "Cannot designate root key: instantiation.root.repository is missing after validation");
        }
        return root.getRepository().trim();
    }

    @Override
    public Map<String, JsonNode> enrichRequestParametersWithDefaultsIfNeeded(JsonNode content, Map<String, JsonNode> requestParameters) {
        Manifest manifest = parse(content);
        Map<String, JsonNode> out = new LinkedHashMap<>();
        if (manifest.getParameters() == null) {
            return out;
        }
        Map<String, JsonNode> request = requestParameters == null ? Map.of() : requestParameters;
        for (ManifestParameter parameter : manifest.getParameters()) {
            if (parameter == null || !StringUtils.hasText(parameter.getKey())) {
                continue;
            }
            String key = parameter.getKey();
            JsonNode fromRequest = request.get(key);
            if (fromRequest != null && !fromRequest.isNull()) {
                out.put(key, fromRequest);
            } else if (parameter.getDefaultValue() != null && !parameter.getDefaultValue().isNull()) {
                out.put(key, parameter.getDefaultValue());
            }
        }
        return out;
    }

    @Override
    public List<InstantiationCompositionIdentity> listCompositionIdentities(JsonNode content) {
        Manifest manifest = parse(content);
        List<InstantiationCompositionIdentity> identities = new ArrayList<>();
        if (manifest.getComposition() == null) {
            return identities;
        }
        for (int i = 0; i < manifest.getComposition().size(); i++) {
            ManifestComposition composition = manifest.getComposition().get(i);
            if (composition == null) {
                continue;
            }
            identities.add(new InstantiationCompositionIdentity(
                    composition.getModule(),
                    composition.getBlueprintName(),
                    composition.getBlueprintVersion(),
                    "composition[" + i + "]"));
        }
        return identities;
    }

    @Override
    public boolean isMonorepoNoComposition(JsonNode content) {
        return InstantiationScenarioResolver.isMonorepoNoComposition(parse(content));
    }

    private Manifest parse(JsonNode content) {
        try {
            return deserialize(content);
        } catch (IOException e) {
            throw new InternalException("Unable to parse blueprint manifest content", e);
        }
    }

    private String defaultPath(String path) {
        return StringUtils.hasText(path) ? path : "./";
    }

    private void collectStructuralIssues(
            Manifest manifest,
            List<InstantiationValidationIssue> issues) {
        ManifestInstantiation instantiation = manifest.getInstantiation();
        if (instantiation == null) {
            issues.add(new InstantiationValidationIssue(
                    "instantiation",
                    "Manifest instantiation is required",
                    "Declare instantiation.repositories and instantiation.root.targets."));
            validateCompositionStructure(
                    manifest, new LinkedHashSet<>(), new LinkedHashSet<>(), new ArrayList<>(), issues);
            return;
        }

        Set<String> declaredKeys = new LinkedHashSet<>();
        List<ManifestInstantiationRepository> repositories = instantiation.getRepositories();
        if (repositories == null || repositories.isEmpty()) {
            issues.add(new InstantiationValidationIssue(
                    "instantiation.repositories",
                    "Instantiation repositories are required",
                    "Declare at least one instantiation.repositories[].key."));
        } else {
            Set<String> seenKeys = new HashSet<>();
            for (int i = 0; i < repositories.size(); i++) {
                ManifestInstantiationRepository repository = repositories.get(i);
                String fieldPath = "instantiation.repositories[" + i + "]";
                if (repository == null) {
                    issues.add(new InstantiationValidationIssue(
                            fieldPath,
                            "Instantiation repository entry is required",
                            "Provide a repository object with a unique key."));
                    continue;
                }
                if (!StringUtils.hasText(repository.getKey())) {
                    issues.add(new InstantiationValidationIssue(
                            fieldPath + ".key",
                            "Instantiation repository key is required",
                            "Set a non-empty unique key for this repository."));
                    continue;
                }
                String key = repository.getKey().trim();
                if (!seenKeys.add(key)) {
                    issues.add(new InstantiationValidationIssue(
                            fieldPath + ".key",
                            "Instantiation repository keys must be unique",
                            "Use a distinct key for each instantiation.repositories entry."));
                } else {
                    declaredKeys.add(key);
                }
            }
        }

        Set<String> usedKeys = new LinkedHashSet<>();
        List<RouteDestination> destinations = new ArrayList<>();

        ManifestInstantiationRoot root = instantiation.getRoot();
        if (root == null) {
            issues.add(new InstantiationValidationIssue(
                    "instantiation.root",
                    "Instantiation root is required",
                    HINT_NON_EMPTY_ROOT_TARGETS));
        } else {
            if (!StringUtils.hasText(root.getRepository())) {
                issues.add(new InstantiationValidationIssue(
                        "instantiation.root.repository",
                        "instantiation.root.repository is required",
                        HINT_ROOT_REPOSITORY));
            } else {
                String rootKey = root.getRepository().trim();
                if (!declaredKeys.contains(rootKey)) {
                    issues.add(new InstantiationValidationIssue(
                            "instantiation.root.repository",
                            "instantiation.root.repository must match an instantiation.repositories[].key",
                            HINT_ROOT_REPOSITORY));
                }
            }

            List<ManifestTarget> rootTargets = root.getTargets();
            if (rootTargets == null || rootTargets.isEmpty()) {
                issues.add(new InstantiationValidationIssue(
                        "instantiation.root.targets",
                        "Instantiation root.targets must be non-empty",
                        HINT_NON_EMPTY_ROOT_TARGETS));
            } else {
                validateTargetsList(
                        rootTargets,
                        "instantiation.root.targets",
                        declaredKeys,
                        usedKeys,
                        destinations,
                        issues);
            }
        }

        validateCompositionStructure(manifest, declaredKeys, usedKeys, destinations, issues);

        for (String key : declaredKeys) {
            if (!usedKeys.contains(key)) {
                issues.add(new InstantiationValidationIssue(
                        "instantiation.repositories[key=%s]".formatted(key),
                        "Instantiation repository key '%s' is unused".formatted(key),
                        HINT_UNUSED_KEY));
            }
        }

        detectDuplicateAndNestedDestinations(destinations, issues);
    }

    private void validateCompositionStructure(
            Manifest manifest,
            Set<String> declaredKeys,
            Set<String> usedKeys,
            List<RouteDestination> destinations,
            List<InstantiationValidationIssue> issues) {
        List<ManifestComposition> compositions = manifest.getComposition();
        if (compositions == null || compositions.isEmpty()) {
            return;
        }

        Set<String> parentParameterKeys = collectParentParameterKeys(manifest);
        Set<String> seenModules = new HashSet<>();
        for (int i = 0; i < compositions.size(); i++) {
            validateCompositionEntry(
                    compositions.get(i),
                    "composition[" + i + "]",
                    seenModules,
                    parentParameterKeys,
                    declaredKeys,
                    usedKeys,
                    destinations,
                    issues);
        }
    }

    private void validateCompositionEntry(
            ManifestComposition composition,
            String fieldPath,
            Set<String> seenModules,
            Set<String> parentParameterKeys,
            Set<String> declaredKeys,
            Set<String> usedKeys,
            List<RouteDestination> destinations,
            List<InstantiationValidationIssue> issues) {
        if (composition == null) {
            issues.add(new InstantiationValidationIssue(
                    fieldPath,
                    "Composition entry is required",
                    "Provide a composition object with module identity and targets."));
            return;
        }

        validateCompositionModule(composition, fieldPath, seenModules, issues);
        validateCompositionBlueprintIdentity(composition, fieldPath, issues);
        validateParameterMapping(
                composition.getParameterMapping(),
                fieldPath + ".parameterMapping",
                parentParameterKeys,
                issues);
        validateCompositionTargets(composition, fieldPath, declaredKeys, usedKeys, destinations, issues);
    }

    private void validateCompositionModule(
            ManifestComposition composition,
            String fieldPath,
            Set<String> seenModules,
            List<InstantiationValidationIssue> issues) {
        if (!StringUtils.hasText(composition.getModule())) {
            issues.add(new InstantiationValidationIssue(
                    fieldPath + ".module",
                    "Composition module is required",
                    "Set a unique non-empty module alias."));
            return;
        }
        String module = composition.getModule().trim();
        if (!seenModules.add(module)) {
            issues.add(new InstantiationValidationIssue(
                    fieldPath + ".module",
                    "Composition module values must be unique",
                    "Use a distinct alias for each composition[].module."));
        }
    }

    private void validateCompositionBlueprintIdentity(
            ManifestComposition composition,
            String fieldPath,
            List<InstantiationValidationIssue> issues) {
        if (!StringUtils.hasText(composition.getBlueprintName())) {
            issues.add(new InstantiationValidationIssue(
                    fieldPath + ".blueprintName",
                    "Composition blueprintName is required",
                    "Set the published blueprint name for this module."));
        }
        if (!StringUtils.hasText(composition.getBlueprintVersion())) {
            issues.add(new InstantiationValidationIssue(
                    fieldPath + ".blueprintVersion",
                    "Composition blueprintVersion is required",
                    "Set the published blueprint version for this module."));
        }
    }

    private void validateCompositionTargets(
            ManifestComposition composition,
            String fieldPath,
            Set<String> declaredKeys,
            Set<String> usedKeys,
            List<RouteDestination> destinations,
            List<InstantiationValidationIssue> issues) {
        List<ManifestTarget> targets = composition.getTargets();
        if (targets == null || targets.isEmpty()) {
            issues.add(new InstantiationValidationIssue(
                    fieldPath + ".targets",
                    "Composition targets are required",
                    "Add at least one composition[].targets entry that references a declared repository key."));
            return;
        }
        validateTargetsList(
                targets,
                fieldPath + ".targets",
                declaredKeys,
                usedKeys,
                destinations,
                issues);
    }

    private void validateTargetsList(
            List<ManifestTarget> targets,
            String fieldPath,
            Set<String> declaredKeys,
            Set<String> usedKeys,
            List<RouteDestination> destinations,
            List<InstantiationValidationIssue> issues) {
        for (int i = 0; i < targets.size(); i++) {
            ManifestTarget target = targets.get(i);
            String targetPath = fieldPath + "[" + i + "]";
            if (target == null) {
                issues.add(new InstantiationValidationIssue(
                        targetPath,
                        "Target entry is required",
                        "Provide a target with repository and path."));
                continue;
            }

            validateRelativePath(target.getSourcePath(), targetPath + ".sourcePath", issues);
            validateRelativePath(target.getPath(), targetPath + ".path", issues);

            if (!StringUtils.hasText(target.getRepository())) {
                issues.add(new InstantiationValidationIssue(
                        targetPath + ".repository",
                        "Target repository is required",
                        HINT_UNKNOWN_REPOSITORY));
                continue;
            }

            String repositoryKey = target.getRepository().trim();
            usedKeys.add(repositoryKey);
            if (!declaredKeys.isEmpty() && !declaredKeys.contains(repositoryKey)) {
                issues.add(new InstantiationValidationIssue(
                        targetPath + ".repository",
                        "Target repository '%s' is not a declared instantiation.repositories[].key"
                                .formatted(repositoryKey),
                        HINT_UNKNOWN_REPOSITORY));
            }

            destinations.add(new RouteDestination(
                    repositoryKey,
                    normalizeDestinationPath(target.getPath()),
                    targetPath));
        }
    }

    private void validateRelativePath(String path, String fieldPath, List<InstantiationValidationIssue> issues) {
        if (!StringUtils.hasText(path)) {
            return;
        }
        String trimmed = path.trim();
        if (trimmed.startsWith("/") || trimmed.contains("..")) {
            issues.add(new InstantiationValidationIssue(
                    fieldPath,
                    "Repository paths must be relative and must not contain '..'",
                    HINT_RELATIVE_PATH));
        }
    }

    private void detectDuplicateAndNestedDestinations(
            List<RouteDestination> destinations,
            List<InstantiationValidationIssue> issues) {
        Set<String> reportedExact = new HashSet<>();
        Set<String> reportedNested = new HashSet<>();

        for (int i = 0; i < destinations.size(); i++) {
            RouteDestination left = destinations.get(i);
            for (int j = i + 1; j < destinations.size(); j++) {
                RouteDestination right = destinations.get(j);
                if (!left.repositoryKey().equals(right.repositoryKey())) {
                    continue;
                }
                if (left.normalizedPath().equals(right.normalizedPath())) {
                    String exactKey = left.repositoryKey() + "|" + left.normalizedPath();
                    if (reportedExact.add(exactKey)) {
                        issues.add(new InstantiationValidationIssue(
                                left.fieldPath() + ".path",
                                "Duplicate destination (repository='%s', path='%s')"
                                        .formatted(left.repositoryKey(), left.normalizedPath()),
                                HINT_DUPLICATE_DESTINATION));
                    }
                    continue;
                }
                if (isNestedPathPrefix(left.normalizedPath(), right.normalizedPath())) {
                    String nestedKey = left.repositoryKey() + "|"
                            + orderedPair(left.normalizedPath(), right.normalizedPath());
                    if (reportedNested.add(nestedKey)) {
                        issues.add(new InstantiationValidationIssue(
                                left.fieldPath() + ".path",
                                "Nested path-prefix destinations on repository key '%s' ('%s' and '%s')"
                                        .formatted(
                                                left.repositoryKey(),
                                                left.normalizedPath(),
                                                right.normalizedPath()),
                                HINT_NESTED_PATH));
                    }
                }
            }
        }
    }

    private void validateParameterMapping(
            Map<String, JsonNode> parameterMapping,
            String fieldPath,
            Set<String> parentParameterKeys,
            List<InstantiationValidationIssue> issues) {
        if (parameterMapping == null || parameterMapping.isEmpty()) {
            return;
        }
        for (Map.Entry<String, JsonNode> entry : parameterMapping.entrySet()) {
            validateParameterMappingEntry(
                    entry.getValue(),
                    fieldPath + "." + entry.getKey(),
                    parentParameterKeys,
                    issues);
        }
    }

    private void validateParameterMappingEntry(
            JsonNode mappingValue,
            String entryPath,
            Set<String> parentParameterKeys,
            List<InstantiationValidationIssue> issues) {
        if (mappingValue == null || mappingValue.isNull() || !mappingValue.isObject()) {
            issues.add(new InstantiationValidationIssue(
                    entryPath,
                    "parameterMapping entry must be an object with exactly one of '$param' or 'value'",
                    "Use { $param: <parentKey> } or { value: <actualValue> }."));
            return;
        }

        boolean hasParam = mappingValue.has("$param");
        boolean hasValue = mappingValue.has("value");
        if (hasParam && hasValue) {
            issues.add(new InstantiationValidationIssue(
                    entryPath,
                    "parameterMapping entry must not declare both '$param' and 'value'",
                    "Keep only one discriminant: { $param: <parentKey> } or { value: <actualValue> }."));
            return;
        }
        if (!hasParam && !hasValue) {
            issues.add(new InstantiationValidationIssue(
                    entryPath,
                    "parameterMapping entry must declare exactly one of '$param' or 'value'",
                    "Use { $param: <parentKey> } or { value: <actualValue> }."));
            return;
        }
        if (hasParam) {
            validateParentParamReference(mappingValue.get("$param"), entryPath, parentParameterKeys, issues);
        }
    }

    private void validateParentParamReference(
            JsonNode paramNode,
            String entryPath,
            Set<String> parentParameterKeys,
            List<InstantiationValidationIssue> issues) {
        if (paramNode == null || !paramNode.isTextual() || !StringUtils.hasText(paramNode.asText())) {
            issues.add(new InstantiationValidationIssue(
                    entryPath + ".$param",
                    "parameterMapping '$param' must be a non-empty string parent parameter key",
                    "Set $param to a declared parent parameter key."));
            return;
        }
        String parentKey = paramNode.asText().trim();
        if (!parentParameterKeys.contains(parentKey)) {
            issues.add(new InstantiationValidationIssue(
                    entryPath + ".$param",
                    "parameterMapping '$param' references undeclared parent parameter '%s'"
                            .formatted(parentKey),
                    "Declare the parent parameter or fix the $param reference."));
        }
    }

    private void validateTargetRepositoryMap(
            Manifest manifest,
            List<TargetRepositoryDto> targetRepositories,
            List<InstantiationValidationIssue> issues) {
        Set<String> declaredKeys = new LinkedHashSet<>();
        ManifestInstantiation instantiation = manifest.getInstantiation();
        if (instantiation != null && instantiation.getRepositories() != null) {
            for (ManifestInstantiationRepository repository : instantiation.getRepositories()) {
                if (repository != null && StringUtils.hasText(repository.getKey())) {
                    declaredKeys.add(repository.getKey().trim());
                }
            }
        }
        if (declaredKeys.isEmpty()) {
            return;
        }

        List<TargetRepositoryDto> targets =
                targetRepositories == null ? List.of() : targetRepositories;
        Map<String, Integer> seenTargetIds = new LinkedHashMap<>();
        Set<String> unknownReported = new HashSet<>();

        for (int i = 0; i < targets.size(); i++) {
            TargetRepositoryDto target = targets.get(i);
            String fieldPath = "targetRepositories[" + i + "]";
            if (target == null || !StringUtils.hasText(target.targetId())) {
                issues.add(new InstantiationValidationIssue(
                        fieldPath + ".targetId",
                        "Target repository targetId is required",
                        HINT_MATCH_TARGET_ID));
                continue;
            }
            String targetId = target.targetId().trim();
            Integer previousIndex = seenTargetIds.putIfAbsent(targetId, i);
            if (previousIndex != null) {
                issues.add(new InstantiationValidationIssue(
                        fieldPath + ".targetId",
                        "Duplicate targetId '%s'".formatted(targetId),
                        HINT_SEND_EACH_TARGET_ONCE));
            }
            if (!declaredKeys.contains(targetId) && unknownReported.add(targetId)) {
                issues.add(new InstantiationValidationIssue(
                        fieldPath + ".targetId",
                        "Unknown targetId '%s'".formatted(targetId),
                        HINT_MATCH_TARGET_ID));
            }
        }

        for (String key : declaredKeys) {
            if (!seenTargetIds.containsKey(key)) {
                issues.add(new InstantiationValidationIssue(
                        "targetRepositories",
                        "Missing targetRepository for instantiation key '%s'".formatted(key),
                        HINT_SUPPLY_ALL_TARGETS));
            }
        }
    }

    private void validateParameters(
            Manifest manifest,
            Map<String, JsonNode> parameters,
            List<InstantiationValidationIssue> issues) {
        if (manifest.getParameters() == null) {
            return;
        }
        for (ManifestParameter parameter : manifest.getParameters()) {
            if (parameter == null || !StringUtils.hasText(parameter.getKey())) {
                continue;
            }
            String key = parameter.getKey().trim();
            String fieldPath = "parameters[" + key + "]";
            JsonNode value = parameters.get(parameter.getKey());
            if (value == null) {
                value = parameters.get(key);
            }
            if (value == null) {
                if (Boolean.TRUE.equals(parameter.getRequired()) && parameter.getDefaultValue() == null) {
                    issues.add(new InstantiationValidationIssue(
                            fieldPath,
                            "Missing required parameter '%s'".formatted(key),
                            "Supply the parameter in the request or declare a default in the manifest."));
                }
                continue;
            }
            validateParameterType(parameter, value, fieldPath, issues);
            validateParameterConstraints(parameter, toJavaValue(value), fieldPath, issues);
        }
    }

    private void validateParameterType(
            ManifestParameter parameter,
            JsonNode value,
            String fieldPath,
            List<InstantiationValidationIssue> issues) {
        ManifestParameter.ManifestParameterType type = parameter.getType();
        if (type == null) {
            return;
        }
        boolean isValid = switch (type) {
            case STRING -> value.isTextual();
            case INTEGER -> value.isNumber();
            case BOOLEAN -> value.isBoolean();
            case ARRAY -> value.isArray();
            case OBJECT -> value.isObject();
        };
        if (!isValid) {
            issues.add(new InstantiationValidationIssue(
                    fieldPath,
                    "Parameter '%s' must be of type %s".formatted(parameter.getKey(), type.name().toLowerCase()),
                    "Provide a JSON value matching the declared parameter type."));
        }
    }

    private void validateParameterConstraints(
            ManifestParameter parameter,
            Object value,
            String fieldPath,
            List<InstantiationValidationIssue> issues) {
        ManifestParameterValidation validation = parameter.getValidation();
        if (validation == null) {
            return;
        }

        validateAllowedValues(parameter, value, fieldPath, issues, validation);
        validatePattern(parameter, value, fieldPath, issues, validation);
        validateFormat(parameter, value, fieldPath, issues, validation);
        validateMinMax(parameter.getKey(), validation.getMin(), validation.getMax(), value, fieldPath, issues);
    }

    private void validateFormat(
            ManifestParameter parameter,
            Object value,
            String fieldPath,
            List<InstantiationValidationIssue> issues,
            ManifestParameterValidation validation) {
        if (!StringUtils.hasText(validation.getFormat()) || !(value instanceof String textValue)) {
            return;
        }
        String format = validation.getFormat().trim().toLowerCase();
        boolean isValid = switch (format) {
            case "email" -> SPRING_VALIDATOR.validate(new EmailFormatValue(textValue)).isEmpty();
            case "uri" -> isValidUri(textValue);
            case "uuid" -> isValidUuid(textValue);
            default -> true;
        };
        if (!isValid) {
            issues.add(new InstantiationValidationIssue(
                    fieldPath,
                    "Parameter '%s' does not match required format '%s'".formatted(parameter.getKey(), format),
                    "Provide a value that matches the declared format constraint."));
        }
    }

    private boolean isValidUri(String textValue) {
        try {
            URI uri = UriComponentsBuilder.fromUriString(textValue).build().toUri();
            return StringUtils.hasText(uri.getScheme());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isValidUuid(String textValue) {
        try {
            UUID.fromString(textValue);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void validatePattern(
            ManifestParameter parameter,
            Object value,
            String fieldPath,
            List<InstantiationValidationIssue> issues,
            ManifestParameterValidation validation) {
        if (StringUtils.hasText(validation.getPattern()) && value instanceof String textValue) {
            try {
                if (!Pattern.compile(validation.getPattern()).matcher(textValue).matches()) {
                    issues.add(new InstantiationValidationIssue(
                            fieldPath,
                            "Parameter '%s' does not match required pattern".formatted(parameter.getKey()),
                            "Provide a value that matches the declared pattern constraint."));
                }
            } catch (PatternSyntaxException e) {
                issues.add(new InstantiationValidationIssue(
                        fieldPath,
                        "Parameter '%s' has invalid pattern constraint in manifest".formatted(parameter.getKey()),
                        "Fix the validation.pattern regular expression in the manifest."));
            }
        }
    }

    private void validateAllowedValues(
            ManifestParameter parameter,
            Object value,
            String fieldPath,
            List<InstantiationValidationIssue> issues,
            ManifestParameterValidation validation) {
        if (validation.getAllowedValues() != null && !validation.getAllowedValues().isEmpty()) {
            JsonNode valueNode = OBJECT_MAPPER.valueToTree(value);
            boolean match = validation.getAllowedValues().stream().filter(Objects::nonNull).anyMatch(valueNode::equals);
            if (!match) {
                issues.add(new InstantiationValidationIssue(
                        fieldPath,
                        "Parameter '%s' value is not among allowedValues".formatted(parameter.getKey()),
                        "Choose a value listed in the parameter validation.allowedValues."));
            }
        }
    }

    private void validateMinMax(
            String key,
            Number min,
            Number max,
            Object value,
            String fieldPath,
            List<InstantiationValidationIssue> issues) {
        Double measured = switch (value) {
            case Number number -> number.doubleValue();
            case String text -> (double) text.length();
            case Collection<?> collection -> (double) collection.size();
            default -> value != null && value.getClass().isArray() ? (double) Array.getLength(value) : null;
        };
        if (measured == null) {
            return;
        }
        if (min != null && measured < min.doubleValue()) {
            issues.add(new InstantiationValidationIssue(
                    fieldPath,
                    "Parameter '%s' value is below min=%s".formatted(key, min),
                    "Provide a value that satisfies the declared min constraint."));
        }
        if (max != null && measured > max.doubleValue()) {
            issues.add(new InstantiationValidationIssue(
                    fieldPath,
                    "Parameter '%s' value exceeds max=%s".formatted(key, max),
                    "Provide a value that satisfies the declared max constraint."));
        }
    }

    private Manifest deserialize(JsonNode content) throws IOException {
        if (content == null) {
            throw new IOException("Blueprint manifest content is required");
        }
        return ManifestParserFactory.getParser().deserialize(content);
    }

    private Repository toGitRepository(BlueprintRepo blueprintRepo) {
        Repository sourceRepository = new Repository();
        if (blueprintRepo == null) {
            return sourceRepository;
        }
        sourceRepository.setId(blueprintRepo.getExternalIdentifier());
        sourceRepository.setName(blueprintRepo.getName());
        sourceRepository.setDescription(blueprintRepo.getDescription());
        sourceRepository.setDefaultBranch(blueprintRepo.getDefaultBranch());
        sourceRepository.setOwnerId(blueprintRepo.getOwnerId());
        sourceRepository.setCloneUrlHttp(blueprintRepo.getRemoteUrlHttp());
        sourceRepository.setCloneUrlSsh(blueprintRepo.getRemoteUrlSsh());
        return sourceRepository;
    }

    private String providerTypeName(BlueprintRepo repo) {
        if (repo == null) {
            return null;
        }
        BlueprintRepoProviderType providerType = repo.getProviderType();
        return providerType == null ? null : providerType.name();
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private Set<String> collectParentParameterKeys(Manifest manifest) {
        Set<String> keys = new HashSet<>();
        if (manifest.getParameters() == null) {
            return keys;
        }
        for (ManifestParameter parameter : manifest.getParameters()) {
            if (parameter != null && StringUtils.hasText(parameter.getKey())) {
                keys.add(parameter.getKey().trim());
            }
        }
        return keys;
    }

    private Object toJavaValue(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isTextual()) {
            return value.asText();
        }
        if (value.isNumber()) {
            return value.numberValue();
        }
        if (value.isBoolean()) {
            return value.booleanValue();
        }
        if (value.isArray()) {
            List<JsonNode> elements = new ArrayList<>();
            value.forEach(elements::add);
            return elements;
        }
        return value;
    }

    private String normalizeDestinationPath(String path) {
        if (path == null || path.isBlank()) {
            return "./";
        }
        String normalized = path.trim().replace('\\', '/');
        while (normalized.endsWith("/") && !"./".equals(normalized)) {
            normalized = normalized.substring(0, normalized.length() - 1);
            if (normalized.isEmpty()) {
                return "./";
            }
        }
        return normalized;
    }

    private boolean isNestedPathPrefix(String normalizedA, String normalizedB) {
        String a = stripLeadingDotSlash(normalizedA);
        String b = stripLeadingDotSlash(normalizedB);
        boolean aRoot = a.isEmpty() || ".".equals(a);
        boolean bRoot = b.isEmpty() || ".".equals(b);
        if (aRoot && bRoot) {
            return false;
        }
        if (aRoot || bRoot) {
            return true;
        }
        return a.startsWith(b + "/") || b.startsWith(a + "/");
    }

    private String stripLeadingDotSlash(String path) {
        String result = path;
        while (result.startsWith("./")) {
            result = result.substring(2);
        }
        return result;
    }

    private String orderedPair(String left, String right) {
        return left.compareTo(right) <= 0 ? left + "<>" + right : right + "<>" + left;
    }

    private record RouteDestination(String repositoryKey, String normalizedPath, String fieldPath) {
    }

    private record EmailFormatValue(@Email String value) {
    }
}
