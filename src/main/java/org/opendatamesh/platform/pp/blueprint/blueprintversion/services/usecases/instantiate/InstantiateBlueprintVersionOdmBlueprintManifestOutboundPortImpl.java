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
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestParameter;
import org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation.ManifestInstantiationEntry;
import org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation.ManifestInstantiationType;
import org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation.ManifestTargetRepository;
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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    /**
     * Matches manifest schema versions used in the platform (e.g. {@code v1},
     * {@code 1.0.0}).
     */
    private static final String VERSION = "(v1|1\\.\\d+\\.\\d+.*)";
    private static final LocalValidatorFactoryBean SPRING_VALIDATOR = buildValidator();

    private static final String HINT_RELATIVE_PATH = "Use a relative path without '..' or a leading '/'.";
    private static final String HINT_NON_EMPTY_INSTANTIATION =
            "Add at least one instantiation[] entry with type: root and non-empty targets.";
    private static final String HINT_UNUSED_KEY =
            "Declare a route in instantiation[].targets that uses this key, or remove the unused key.";
    private static final String HINT_UNKNOWN_REPOSITORY = "Use a key declared in targetRepositories[].key.";
    private static final String HINT_DUPLICATE_DESTINATION = "Make destination (repo, destinationPath) pairs unique.";
    private static final String HINT_NESTED_PATH =
            "Use sibling destinations that do not nest under each other on the same repository key.";
    private static final String HINT_SUPPLY_ALL_TARGETS =
            "Supply targetRepositories for every targetRepositories[].key.";
    private static final String HINT_SEND_EACH_TARGET_ONCE = "Send each targetId once.";
    private static final String HINT_MATCH_TARGET_ID = "Match targetId to targetRepositories[].key.";
    private static final String HINT_PROVIDER_MISMATCH =
            "Composition modules must use the same Git provider type and base URL as the parent.";
    private static final String HINT_ROOT_REPOSITORY =
            "Set isRoot: true on exactly one targetRepositories[] entry.";
    private static final String HINT_RESOLVE_PARENT_PARAM =
            "Supply the parent parameter in the request or declare a default in the manifest.";

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
                InstantiateBlueprintVersion.PARENT_SOURCE_ID,
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
        if (manifest.getInstantiation() == null) {
            return routes;
        }
        for (ManifestInstantiationEntry entry : manifest.getInstantiation()) {
            if (entry == null || entry.getType() == null || entry.getTargets() == null) {
                continue;
            }
            String sourceId = entry.getType() == ManifestInstantiationType.ROOT
                    ? InstantiateBlueprintVersion.PARENT_SOURCE_ID
                    : entry.getModuleName();
            for (ManifestTarget target : entry.getTargets()) {
                routes.add(new InstantiationRoute(
                        sourceId,
                        defaultPath(target.getSourcePath()),
                        target.getRepo(),
                        defaultPath(target.getDestinationPath())));
            }
        }
        return routes;
    }

    @Override
    public String retrieveRootTargetRepositoryKey(JsonNode content) {
        Manifest manifest = parse(content);
        if (manifest.getTargetRepositories() == null) {
            throw new InternalException(
                    "Cannot designate root key: targetRepositories is missing after validation");
        }
        for (ManifestTargetRepository repository : manifest.getTargetRepositories()) {
            if (repository != null && Boolean.TRUE.equals(repository.getIsRoot())
                    && StringUtils.hasText(repository.getKey())) {
                return repository.getKey().trim();
            }
        }
        throw new InternalException(
                "Cannot designate root key: no targetRepositories entry has isRoot: true after validation");
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
    public List<InstantiationValidationIssue> collectModuleParameterResolutionIssues(
            JsonNode content,
            Map<String, JsonNode> parentResolvedParameters) {
        List<InstantiationValidationIssue> issues = new ArrayList<>();
        Manifest manifest = parse(content);
        if (manifest.getComposition() == null || manifest.getComposition().isEmpty()) {
            return issues;
        }

        Map<String, JsonNode> parentParams =
                parentResolvedParameters == null ? Map.of() : parentResolvedParameters;

        for (int i = 0; i < manifest.getComposition().size(); i++) {
            ManifestComposition composition = manifest.getComposition().get(i);
            if (composition == null
                    || composition.getParameterMapping() == null
                    || composition.getParameterMapping().isEmpty()) {
                continue;
            }
            for (Map.Entry<String, JsonNode> entry : composition.getParameterMapping().entrySet()) {
                JsonNode mappingValue = entry.getValue();
                if (mappingValue == null || !mappingValue.isObject() || !mappingValue.has("$param")) {
                    continue;
                }
                JsonNode paramNode = mappingValue.get("$param");
                if (paramNode == null || !paramNode.isTextual() || !StringUtils.hasText(paramNode.asText())) {
                    continue;
                }
                String parentKey = paramNode.asText().trim();
                JsonNode resolved = parentParams.get(parentKey);
                if (resolved == null || resolved.isNull()) {
                    issues.add(new InstantiationValidationIssue(
                            "composition[%d].parameterMapping.%s.$param".formatted(i, entry.getKey()),
                            "parameterMapping '$param' references parent parameter '%s' that cannot be resolved"
                                    .formatted(parentKey),
                            HINT_RESOLVE_PARENT_PARAM));
                }
            }
        }
        return issues;
    }

    @Override
    public Map<String, Map<String, JsonNode>> resolveModuleParameters(
            JsonNode content,
            Map<String, JsonNode> parentResolvedParameters) {
        Manifest manifest = parse(content);
        Map<String, Map<String, JsonNode>> result = new LinkedHashMap<>();
        if (manifest.getComposition() == null || manifest.getComposition().isEmpty()) {
            return result;
        }

        Map<String, JsonNode> parentParams =
                parentResolvedParameters == null ? Map.of() : parentResolvedParameters;

        for (ManifestComposition composition : manifest.getComposition()) {
            if (composition == null || !StringUtils.hasText(composition.getModule())) {
                continue;
            }
            Map<String, JsonNode> childParams = new LinkedHashMap<>();
            Map<String, JsonNode> parameterMapping = composition.getParameterMapping();
            if (parameterMapping != null && !parameterMapping.isEmpty()) {
                for (Map.Entry<String, JsonNode> entry : parameterMapping.entrySet()) {
                    JsonNode mappingValue = entry.getValue();
                    if (mappingValue == null || !mappingValue.isObject()) {
                        continue;
                    }
                    if (mappingValue.has("$param")) {
                        JsonNode paramNode = mappingValue.get("$param");
                        if (paramNode != null && paramNode.isTextual() && StringUtils.hasText(paramNode.asText())) {
                            JsonNode parentValue = parentParams.get(paramNode.asText().trim());
                            if (parentValue != null && !parentValue.isNull()) {
                                childParams.put(entry.getKey(), parentValue);
                            }
                        }
                    } else if (mappingValue.has("value")) {
                        childParams.put(entry.getKey(), mappingValue.get("value"));
                    }
                }
            }
            result.put(composition.getModule().trim(), childParams);
        }
        return result;
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
        Set<String> declaredKeys = new LinkedHashSet<>();
        List<ManifestTargetRepository> targetRepositories = manifest.getTargetRepositories();
        int rootTargetRepositoryCount = 0;
        if (targetRepositories == null || targetRepositories.isEmpty()) {
            issues.add(new InstantiationValidationIssue(
                    "targetRepositories",
                    "Manifest targetRepositories is required",
                    "Declare at least one targetRepositories[].key."));
        } else {
            Set<String> seenKeys = new HashSet<>();
            for (int i = 0; i < targetRepositories.size(); i++) {
                ManifestTargetRepository repository = targetRepositories.get(i);
                String fieldPath = "targetRepositories[" + i + "]";
                if (repository == null) {
                    issues.add(new InstantiationValidationIssue(
                            fieldPath,
                            "Target repository entry is required",
                            "Provide a repository object with a unique key."));
                    continue;
                }
                if (!StringUtils.hasText(repository.getKey())) {
                    issues.add(new InstantiationValidationIssue(
                            fieldPath + ".key",
                            "Target repository key is required",
                            "Set a non-empty unique key for this repository."));
                    continue;
                }
                String key = repository.getKey().trim();
                if (!seenKeys.add(key)) {
                    issues.add(new InstantiationValidationIssue(
                            fieldPath + ".key",
                            "Target repository keys must be unique",
                            "Use a distinct key for each targetRepositories entry."));
                } else {
                    declaredKeys.add(key);
                }
                if (Boolean.TRUE.equals(repository.getIsRoot())) {
                    rootTargetRepositoryCount++;
                }
            }
        }

        if (rootTargetRepositoryCount != 1) {
            issues.add(new InstantiationValidationIssue(
                    "targetRepositories",
                    "Exactly one targetRepositories[] entry must set isRoot: true",
                    HINT_ROOT_REPOSITORY));
        }

        Set<String> compositionModules = new LinkedHashSet<>();
        validateCompositionStructure(manifest, compositionModules, issues);

        Set<String> usedKeys = new LinkedHashSet<>();
        List<RouteDestination> destinations = new ArrayList<>();
        Set<String> instantiatedModules = new LinkedHashSet<>();
        int rootInstantiationEntryCount = 0;

        List<ManifestInstantiationEntry> instantiation = manifest.getInstantiation();
        if (instantiation == null || instantiation.isEmpty()) {
            issues.add(new InstantiationValidationIssue(
                    "instantiation",
                    "Manifest instantiation is required",
                    HINT_NON_EMPTY_INSTANTIATION));
        } else {
            for (int i = 0; i < instantiation.size(); i++) {
                ManifestInstantiationEntry entry = instantiation.get(i);
                String fieldPath = "instantiation[" + i + "]";
                if (entry == null) {
                    issues.add(new InstantiationValidationIssue(
                            fieldPath,
                            "Instantiation entry is required",
                            "Provide an instantiation object with type and targets."));
                    continue;
                }
                if (entry.getType() == null) {
                    issues.add(new InstantiationValidationIssue(
                            fieldPath + ".type",
                            "Instantiation type is required",
                            "Set type to 'root' or 'module'."));
                    continue;
                }
                if (entry.getType() == ManifestInstantiationType.ROOT) {
                    rootInstantiationEntryCount++;
                    if (StringUtils.hasText(entry.getModuleName())) {
                        issues.add(new InstantiationValidationIssue(
                                fieldPath + ".moduleName",
                                "moduleName must be omitted when type is root",
                                "Remove moduleName from root instantiation entries."));
                    }
                } else if (entry.getType() == ManifestInstantiationType.MODULE) {
                    if (!StringUtils.hasText(entry.getModuleName())) {
                        issues.add(new InstantiationValidationIssue(
                                fieldPath + ".moduleName",
                                "moduleName is required when type is module",
                                "Set moduleName to a composition[].module value."));
                    } else {
                        instantiatedModules.add(entry.getModuleName().trim());
                    }
                }

                List<ManifestTarget> targets = entry.getTargets();
                if (targets == null || targets.isEmpty()) {
                    issues.add(new InstantiationValidationIssue(
                            fieldPath + ".targets",
                            "Instantiation targets are required",
                            HINT_NON_EMPTY_INSTANTIATION));
                } else {
                    validateTargetsList(
                            targets,
                            fieldPath + ".targets",
                            fieldPath,
                            declaredKeys,
                            usedKeys,
                            destinations,
                            issues);
                }
            }
        }

        if (rootInstantiationEntryCount != 1) {
            issues.add(new InstantiationValidationIssue(
                    "instantiation",
                    "Exactly one instantiation[] entry must have type: root",
                    HINT_NON_EMPTY_INSTANTIATION));
        }

        for (String module : compositionModules) {
            if (!instantiatedModules.contains(module)) {
                issues.add(new InstantiationValidationIssue(
                        "composition",
                        "Composition module '%s' has no matching instantiation[] entry with type: module"
                                .formatted(module),
                        "Add an instantiation entry with type: module and moduleName: " + module + "."));
            }
        }
        for (String module : instantiatedModules) {
            if (!compositionModules.contains(module)) {
                issues.add(new InstantiationValidationIssue(
                        "instantiation",
                        "Instantiation moduleName '%s' does not match any composition[].module".formatted(module),
                        "Declare the module in composition[] or remove the instantiation entry."));
            }
        }

        for (String key : declaredKeys) {
            if (!usedKeys.contains(key)) {
                issues.add(new InstantiationValidationIssue(
                        "targetRepositories[key=%s]".formatted(key),
                        "Target repository key '%s' is unused".formatted(key),
                        HINT_UNUSED_KEY));
            }
        }

        detectDuplicateAndNestedDestinations(destinations, issues);
    }

    private void validateCompositionStructure(
            Manifest manifest,
            Set<String> compositionModules,
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
                    compositionModules,
                    issues);
        }
    }

    private void validateCompositionEntry(
            ManifestComposition composition,
            String fieldPath,
            Set<String> seenModules,
            Set<String> parentParameterKeys,
            Set<String> compositionModules,
            List<InstantiationValidationIssue> issues) {
        if (composition == null) {
            issues.add(new InstantiationValidationIssue(
                    fieldPath,
                    "Composition entry is required",
                    "Provide a composition object with module identity."));
            return;
        }

        validateCompositionModule(composition, fieldPath, seenModules, compositionModules, issues);
        validateCompositionBlueprintIdentity(composition, fieldPath, issues);
        validateParameterMapping(
                composition.getParameterMapping(),
                fieldPath + ".parameterMapping",
                parentParameterKeys,
                issues);
    }

    private void validateCompositionModule(
            ManifestComposition composition,
            String fieldPath,
            Set<String> seenModules,
            Set<String> compositionModules,
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
        } else {
            compositionModules.add(module);
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

    private void validateTargetsList(
            List<ManifestTarget> targets,
            String fieldPath,
            String instantiationEntryPath,
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
                        "Provide a target with repo and destinationPath."));
                continue;
            }

            validateRelativePath(target.getSourcePath(), targetPath + ".sourcePath", issues);
            validateRelativePath(target.getDestinationPath(), targetPath + ".destinationPath", issues);

            if (!StringUtils.hasText(target.getRepo())) {
                issues.add(new InstantiationValidationIssue(
                        targetPath + ".repo",
                        "Target repo is required",
                        HINT_UNKNOWN_REPOSITORY));
                continue;
            }

            String repositoryKey = target.getRepo().trim();
            usedKeys.add(repositoryKey);
            if (!declaredKeys.isEmpty() && !declaredKeys.contains(repositoryKey)) {
                issues.add(new InstantiationValidationIssue(
                        targetPath + ".repo",
                        "Target repo '%s' is not a declared targetRepositories[].key"
                                .formatted(repositoryKey),
                        HINT_UNKNOWN_REPOSITORY));
            }

            destinations.add(new RouteDestination(
                    repositoryKey,
                    normalizeDestinationPath(target.getDestinationPath()),
                    targetPath,
                    instantiationEntryPath));
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
                if (!left.instantiationEntryPath().equals(right.instantiationEntryPath())) {
                    continue;
                }
                if (left.normalizedPath().equals(right.normalizedPath())) {
                    String exactKey = left.repositoryKey() + "|" + left.normalizedPath();
                    if (reportedExact.add(exactKey)) {
                        issues.add(new InstantiationValidationIssue(
                                left.fieldPath() + ".destinationPath",
                                "Duplicate destination (repo='%s', destinationPath='%s')"
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
                                left.fieldPath() + ".destinationPath",
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
        if (manifest.getTargetRepositories() != null) {
            for (ManifestTargetRepository repository : manifest.getTargetRepositories()) {
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

    private record RouteDestination(
            String repositoryKey,
            String normalizedPath,
            String fieldPath,
            String instantiationEntryPath) {
    }

    private record EmailFormatValue(@Email String value) {
    }
}
