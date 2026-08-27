package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import org.opendatamesh.dpds.model.DataProductVersion;
import org.opendatamesh.dpds.model.blueprint.Blueprint;
import org.opendatamesh.dpds.parser.Parser;
import org.opendatamesh.dpds.parser.ParserFactory;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.exceptions.InternalException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * Embeds DPDS blueprint provenance ({@code blueprint}) into the instantiated
 * root data product descriptor file.
 * Uses {@link Blueprint}, not the platform JPA
 * {@link org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint}
 * entity.
 */
@Service
public class BlueprintDataProductDescriptorService {

    private static final Logger log = LoggerFactory.getLogger(BlueprintDataProductDescriptorService.class);

    private static final ObjectMapper JSON_DPDS = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
    private static final ObjectMapper YAML_DPDS = new ObjectMapper(
            new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER))
            .setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

    private enum DescriptorFormat {
        JSON,
        YAML
    }

    /**
     * Writes blueprint lineage into the rendered data product descriptor under the
     * root target workspace. Skips enrichment when the blueprint repository has no
     * descriptor template path.
     */
    public void enrichDescriptorWithBlueprintMetadata(
            Path rootTargetPath,
            BlueprintVersion blueprintVersion,
            Map<String, JsonNode> resolvedParameters) {
        if (!hasDescriptorTemplate(blueprintVersion)) {
            log.info(
                    "Blueprint repository descriptor template path is not configured; cannot write descriptor lineage for blueprint {}",
                    blueprintVersion.getBlueprint().getUuid());
            return;
        }

        Path descriptorFile = requireRenderedDescriptor(rootTargetPath, blueprintVersion);
        byte[] bytes = readDescriptorBytes(descriptorFile);
        DescriptorFormat format = detectFormat(descriptorFile.getFileName().toString(), bytes);
        DataProductVersion descriptor = parseDpdsDescriptor(descriptorFile, format, bytes);
        descriptor.setblueprint(toDpdsBlueprint(blueprintVersion, resolvedParameters));
        writeDescriptor(descriptorFile, format, descriptor);
    }

    private boolean hasDescriptorTemplate(BlueprintVersion blueprintVersion) {
        String descriptorTemplatePath = blueprintVersion.getBlueprint().getBlueprintRepo().getDescriptorTemplatePath();
        return descriptorTemplatePath != null && !descriptorTemplatePath.isBlank();
    }

    private Path requireRenderedDescriptor(Path rootTargetPath, BlueprintVersion blueprintVersion) {
        String relativeDescriptorPath = renderedDescriptorRelativePath(
                blueprintVersion.getBlueprint().getBlueprintRepo().getDescriptorTemplatePath());
        Path descriptorFile = rootTargetPath.resolve(relativeDescriptorPath);
        if (!Files.isRegularFile(descriptorFile)) {
            throw new InternalException(
                    "Expected rendered data product descriptor at '%s' after templating; file missing"
                            .formatted(descriptorFile));
        }
        return descriptorFile;
    }

    private byte[] readDescriptorBytes(Path descriptorFile) {
        try {
            return Files.readAllBytes(descriptorFile);
        } catch (IOException e) {
            throw new InternalException(
                    "Failed to read data product descriptor at '%s' for lineage enrichment"
                            .formatted(descriptorFile),
                    e);
        }
    }

    private DataProductVersion parseDpdsDescriptor(Path descriptorFile, DescriptorFormat format, byte[] bytes) {
        ObjectMapper mapper = mapperFor(format);
        Parser parser = ParserFactory.getParser(mapper);
        JsonNode rootNode = readDescriptorTree(descriptorFile, mapper, bytes);
        try {
            return parser.deserialize(rootNode);
        } catch (IOException e) {
            throw new InternalException(
                    "DPDS parser could not deserialize data product descriptor at '%s'"
                            .formatted(descriptorFile),
                    e);
        }
    }

    private JsonNode readDescriptorTree(Path descriptorFile, ObjectMapper mapper, byte[] bytes) {
        try {
            return mapper.readTree(bytes);
        } catch (IOException e) {
            throw new InternalException(
                    "Failed to parse data product descriptor at '%s' for lineage enrichment"
                            .formatted(descriptorFile),
                    e);
        }
    }

    private void writeDescriptor(Path descriptorFile, DescriptorFormat format, DataProductVersion descriptor) {
        JsonNode serialized = serializeDpdsDescriptor(descriptorFile, format, descriptor);
        try {
            if (format == DescriptorFormat.JSON) {
                JSON_DPDS.writerWithDefaultPrettyPrinter().writeValue(descriptorFile.toFile(), serialized);
            } else {
                YAML_DPDS.writeValue(descriptorFile.toFile(), YAML_DPDS.convertValue(serialized, Object.class));
            }
        } catch (IOException e) {
            throw new InternalException(
                    "Failed to write enriched data product descriptor to '%s'"
                            .formatted(descriptorFile),
                    e);
        }
    }

    private JsonNode serializeDpdsDescriptor(Path descriptorFile, DescriptorFormat format, DataProductVersion descriptor) {
        Parser parser = ParserFactory.getParser(mapperFor(format));
        try {
            return parser.serialize(descriptor);
        } catch (IOException e) {
            throw new InternalException(
                    "DPDS parser could not serialize data product descriptor after lineage enrichment ('%s')"
                            .formatted(descriptorFile),
                    e);
        }
    }

    private ObjectMapper mapperFor(DescriptorFormat format) {
        return format == DescriptorFormat.JSON ? JSON_DPDS : YAML_DPDS;
    }

    private Blueprint toDpdsBlueprint(
            BlueprintVersion blueprintVersion,
            Map<String, JsonNode> resolvedParameters) {
        org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint platformBp = blueprintVersion
                .getBlueprint();
        Blueprint out = new Blueprint();
        out.setSchemaVersion("1");
        out.setBlueprintUuid(platformBp.getUuid());
        out.setBlueprintName(platformBp.getName());
        out.setBlueprintDisplayName(platformBp.getDisplayName());
        out.setBlueprintVersionUuid(blueprintVersion.getUuid());
        out.setBlueprintVersionNumber(blueprintVersion.getVersionNumber());
        out.setBlueprintVersionTag(blueprintVersion.getTag());
        ObjectNode params = JsonNodeFactory.instance.objectNode();
        if (resolvedParameters != null) {
            for (Map.Entry<String, JsonNode> e : resolvedParameters.entrySet()) {
                if (e.getValue() != null && !e.getValue().isNull()) {
                    params.set(e.getKey(), e.getValue());
                }
            }
        }
        out.setParameters(params);
        return out;
    }

    private String renderedDescriptorRelativePath(String descriptorTemplatePath) {
        return stripVmSuffix(normalizeRelative(descriptorTemplatePath));
    }

    private static String stripVmSuffix(String path) {
        if (path.endsWith(".vm")) {
            return path.substring(0, path.length() - 3);
        }
        return path;
    }

    private static String normalizeRelative(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String normalized = path.replace('\\', '/').trim();
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.equals(".") || normalized.isEmpty()) {
            return "";
        }
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private DescriptorFormat detectFormat(String fileName, byte[] content) {
        String name = fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".json")) {
            return DescriptorFormat.JSON;
        }
        if (name.endsWith(".yaml") || name.endsWith(".yml")) {
            return DescriptorFormat.YAML;
        }
        int n = Math.min(content.length, 4096);
        String probe = new String(content, 0, n, StandardCharsets.UTF_8).stripLeading();
        if (probe.startsWith("{") || probe.startsWith("[")) {
            return DescriptorFormat.JSON;
        }
        if (probe.startsWith("---")) {
            return DescriptorFormat.YAML;
        }
        throw new InternalException(
                "Cannot determine data product descriptor format for file name '%s'; use extension .json, .yaml, .yml, or start the file with '---' or '{'"
                        .formatted(fileName));
    }
}
