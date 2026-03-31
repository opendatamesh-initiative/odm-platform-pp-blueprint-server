package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.manifestvalidator;

import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.blueprint.manifest.model.Manifest;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestComposition;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestInstantiation;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestParameter;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestProtectedResource;
import org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation.ManifestInstantiationCompositionLayout;
import org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation.ManifestInstantiationTarget;
import org.opendatamesh.platform.pp.blueprint.manifest.model.protectedresource.ManifestProtectedResourceIntegrity;
import org.opendatamesh.platform.pp.blueprint.manifest.parser.ManifestParser;
import org.opendatamesh.platform.pp.blueprint.manifest.parser.ManifestParserFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Autofill and validation for {@code odm-blueprint-manifest} content during publish.
 */
public class ManifestValidatorImpl implements ManifestValidator {

    private static final Pattern SEMVER = Pattern.compile(
            "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)"
                    + "(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?"
                    + "(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$"
    );

    private final ObjectMapper objectMapper;
    private final ManifestParser manifestParser;

    public ManifestValidatorImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.manifestParser = ManifestParserFactory.getParser(objectMapper);
    }

    @Override
    public JsonNode autofill(JsonNode content, BlueprintVersion blueprintVersion) {
        ObjectNode root = baseObject(content);
        String blueprintName = resolveBlueprintName(blueprintVersion);

        putIfMissingText(root, "spec", Manifest.SPEC_NAME);
        putIfMissingText(root, "specVersion", "1.0.0");
        putIfMissingText(root, "name", blueprintName);
        putIfMissingText(root, "version", "1.0.0");

        if (root.has("parameters") && root.get("parameters").isArray()) {
            ArrayNode params = (ArrayNode) root.get("parameters");
            for (int i = 0; i < params.size(); i++) {
                if (params.get(i).isObject()) {
                    ObjectNode p = (ObjectNode) params.get(i);
                    putIfMissingText(p, "key", "parameterKey-" + UUID.randomUUID());
                    putIfMissingText(p, "type", "string");
                    if (!p.has("required") || p.get("required").isNull()) {
                        p.put("required", false);
                    }
                }
            }
        }

        if (!root.has("instantiation") || root.get("instantiation").isNull()) {
            ObjectNode inst = objectMapper.createObjectNode();
            inst.put("strategy", "monorepo");
            root.set("instantiation", inst);
        } else if (root.get("instantiation").isObject()) {
            ObjectNode inst = (ObjectNode) root.get("instantiation");
            if (!inst.has("strategy") || inst.get("strategy").isNull()) {
                inst.put("strategy", "monorepo");
            }
        }

        return root;
    }

    private static String resolveBlueprintName(BlueprintVersion blueprintVersion) {
        if (blueprintVersion.getBlueprint() != null && StringUtils.hasText(blueprintVersion.getBlueprint().getName())) {
            return blueprintVersion.getBlueprint().getName();
        }
        return "blueprint-" + UUID.randomUUID();
    }

    private ObjectNode baseObject(JsonNode content) {
        if (content == null || content.isNull()) {
            return objectMapper.createObjectNode();
        }
        if (!content.isObject()) {
            throw new BadRequestException("Manifest content must be a JSON object");
        }
        return (ObjectNode) content.deepCopy();
    }

    private static void putIfMissingText(ObjectNode node, String field, String value) {
        if (!node.has(field) || node.get(field).isNull() || !StringUtils.hasText(textOrNull(node.get(field)))) {
            node.put(field, value);
        }
    }

    private static String textOrNull(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        if (!n.isTextual()) {
            return null;
        }
        return n.asText();
    }

    @Override
    public void validate(JsonNode content) {
        if (content == null || content.isNull()) {
            throw new BadRequestException("Missing Blueprint Version content");
        }
        final Manifest manifest;
        try {
            manifest = manifestParser.deserialize(content);
        } catch (IOException e) {
            throw new BadRequestException("Invalid manifest content: " + e.getMessage(), e);
        }

        validateRoot(manifest);
        validateParameters(manifest.getParameters());
        validateProtectedResources(manifest.getProtectedResources());
        validateComposition(manifest.getComposition());
        validateInstantiation(manifest.getInstantiation(), manifest.getComposition());
    }

    private void validateRoot(Manifest manifest) {
        if (!Manifest.SPEC_NAME.equals(manifest.getSpec())) {
            throw new BadRequestException("Manifest spec must be '" + Manifest.SPEC_NAME + "'");
        }
        if (!StringUtils.hasText(manifest.getSpecVersion()) || !SEMVER.matcher(manifest.getSpecVersion().trim()).matches()) {
            throw new BadRequestException("Invalid manifest specVersion");
        }
        if (!StringUtils.hasText(manifest.getName())) {
            throw new BadRequestException("Manifest name must be a non-empty string");
        }
        if (!StringUtils.hasText(manifest.getVersion()) || !SEMVER.matcher(manifest.getVersion().trim()).matches()) {
            throw new BadRequestException("Manifest version must follow semantic versioning");
        }
    }

    private void validateParameters(List<ManifestParameter> parameters) {
        if (parameters == null) {
            return;
        }
        Set<String> keys = new HashSet<>();
        for (ManifestParameter p : parameters) {
            if (!StringUtils.hasText(p.getKey())) {
                throw new BadRequestException("Each manifest parameter must have a key");
            }
            if (!keys.add(p.getKey())) {
                throw new BadRequestException("Duplicate manifest parameter key: " + p.getKey());
            }
            if (p.getType() == null) {
                throw new BadRequestException("Manifest parameter '" + p.getKey() + "' must declare a type");
            }
            if (p.getDefaultValue() != null && !p.getDefaultValue().isNull()) {
                assertDefaultMatchesType(p.getKey(), p.getType(), p.getDefaultValue());
            }
        }
    }

    private void assertDefaultMatchesType(String key, ManifestParameter.ManifestParameterType type, JsonNode def) {
        switch (type) {
            case STRING -> {
                if (!def.isTextual()) {
                    throw new BadRequestException("Default for parameter '" + key + "' must be a string");
                }
            }
            case INTEGER -> {
                if (!def.isInt() && !def.isLong()) {
                    throw new BadRequestException("Default for parameter '" + key + "' must be an integer");
                }
            }
            case BOOLEAN -> {
                if (!def.isBoolean()) {
                    throw new BadRequestException("Default for parameter '" + key + "' must be a boolean");
                }
            }
            case ARRAY -> {
                if (!def.isArray()) {
                    throw new BadRequestException("Default for parameter '" + key + "' must be an array");
                }
            }
            case OBJECT -> {
                if (!def.isObject()) {
                    throw new BadRequestException("Default for parameter '" + key + "' must be an object");
                }
            }
        }
    }

    private void validateProtectedResources(List<ManifestProtectedResource> resources) {
        if (resources == null) {
            return;
        }
        for (ManifestProtectedResource r : resources) {
            if (!StringUtils.hasText(r.getPath())) {
                throw new BadRequestException("Each protected resource must have a path string");
            }
            ManifestProtectedResourceIntegrity integrity = r.getIntegrity();
            if (integrity != null) {
                if (!StringUtils.hasText(integrity.getAlgorithm())) {
                    throw new BadRequestException("Protected resource integrity algorithm must be a string");
                }
                if (!StringUtils.hasText(integrity.getValue())) {
                    throw new BadRequestException("Protected resource integrity value must be a string");
                }
            }
        }
    }

    private void validateComposition(List<ManifestComposition> composition) {
        if (composition == null) {
            return;
        }
        for (ManifestComposition c : composition) {
            if (!StringUtils.hasText(c.getModule())) {
                throw new BadRequestException("Each composition entry must have module");
            }
            if (!StringUtils.hasText(c.getBlueprintName())) {
                throw new BadRequestException("Each composition entry must have blueprintName");
            }
            if (!StringUtils.hasText(c.getBlueprintVersion())) {
                throw new BadRequestException("Each composition entry must have blueprintVersion");
            }
        }
    }

    private void validateInstantiation(ManifestInstantiation instantiation, List<ManifestComposition> composition) {
        if (instantiation == null || instantiation.getStrategy() == null) {
            throw new BadRequestException("Manifest instantiation with strategy is required");
        }

        List<ManifestInstantiationCompositionLayout> layout = instantiation.getCompositionLayout();
        if (layout != null && !layout.isEmpty()) {
            Set<String> modules = compositionModules(composition);
            if (modules.isEmpty()) {
                throw new BadRequestException("compositionLayout requires composition entries");
            }
            for (ManifestInstantiationCompositionLayout row : layout) {
                if (!StringUtils.hasText(row.getModule())) {
                    throw new BadRequestException("compositionLayout entry must have module");
                }
                if (!StringUtils.hasText(row.getTargetPath())) {
                    throw new BadRequestException("compositionLayout entry must have targetPath");
                }
                if (!modules.contains(row.getModule())) {
                    throw new BadRequestException("compositionLayout module must match a composition module");
                }
            }
        }

        if (instantiation.getStrategy() != ManifestInstantiation.InstantiationStrategy.POLYREPO) {
            return;
        }
        List<ManifestInstantiationTarget> targets = instantiation.getTargets();
        if (targets == null || targets.isEmpty()) {
            throw new BadRequestException("polyrepo strategy requires targets");
        }
        boolean hasComposition = composition != null && !composition.isEmpty();
        for (ManifestInstantiationTarget t : targets) {
            if (!StringUtils.hasText(t.getRepositoryNamePostfix())) {
                throw new BadRequestException("polyrepo target requires repositoryNamePostfix");
            }
            if (t.getCreatePolicy() == null) {
                throw new BadRequestException("polyrepo target requires createPolicy");
            }
            if (hasComposition) {
                if (!StringUtils.hasText(t.getModule())) {
                    throw new BadRequestException("polyrepo target requires module when composition is defined");
                }
            } else {
                if (!StringUtils.hasText(t.getSourcePath())) {
                    throw new BadRequestException("polyrepo target requires sourcePath when composition is not defined");
                }
                if (!StringUtils.hasText(t.getTargetPath())) {
                    throw new BadRequestException("polyrepo target requires targetPath when composition is not defined");
                }
            }
        }
    }

    private static Set<String> compositionModules(List<ManifestComposition> composition) {
        Set<String> modules = new HashSet<>();
        if (composition == null) {
            return modules;
        }
        for (ManifestComposition c : composition) {
            if (StringUtils.hasText(c.getModule())) {
                modules.add(c.getModule());
            }
        }
        return modules;
    }

    @Override
    public void setVersionFieldsOnBlueprintVersion(BlueprintVersion blueprintVersion) {
        try {
            Manifest manifest = manifestParser.deserialize(blueprintVersion.getContent());
            blueprintVersion.setVersionNumber(manifest.getVersion());
            blueprintVersion.setSpec(manifest.getSpec());
            blueprintVersion.setSpecVersion(manifest.getSpecVersion());
        } catch (IOException e) {
            throw new BadRequestException("Invalid manifest content: " + e.getMessage(), e);
        }
    }
}
