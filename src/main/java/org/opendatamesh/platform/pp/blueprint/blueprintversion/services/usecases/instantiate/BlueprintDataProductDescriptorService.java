package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import io.micrometer.common.util.StringUtils;

import org.opendatamesh.dpds.model.DataProductVersion;
import org.opendatamesh.dpds.parser.Parser;
import org.opendatamesh.dpds.parser.ParserFactory;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.BlueprintRepo;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.exceptions.InternalException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * Embeds DPDS blueprint provenance ({@code blueprint}) into the instantiated
 * root data product descriptor file.
 * Uses {@link org.opendatamesh.dpds.model.blueprint.Blueprint}, not the
 * platform JPA
 * {@link org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint}
 * entity.
 */
@Component
public class BlueprintDataProductDescriptorService {

    private static final Logger log = LoggerFactory.getLogger(BlueprintDataProductDescriptorService.class);

    private static final ObjectMapper JSON_DPDS = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
    private static final ObjectMapper YAML_DPDS = new ObjectMapper(
            new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
    ).setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

    private enum DescriptorFormat {
        JSON,
        YAML
    }

    public void enrichDescriptorWithBlueprintMetadata(
            Path rootTargetPath,
            BlueprintVersion blueprintVersion,
            Map<String, JsonNode> resolvedParameters
    ) {
        if(StringUtils.isEmpty(blueprintVersion.getBlueprint().getBlueprintRepo().getDescriptorTemplatePath())) {
           log.info("Blueprint repository descriptor template path is not configured; cannot write descriptor lineage for blueprint {}", blueprintVersion.getBlueprint().getUuid());
           return;
        }
        
        BlueprintRepo repo = blueprintVersion.getBlueprint().getBlueprintRepo();
        String blueprintId = blueprintVersion.getBlueprint().getUuid();
        log.debug(
                "Descriptor lineage enrichment for blueprint {} at root {}",
                blueprintId,
                rootTargetPath
        );

        String relativeDescriptorPath = renderedDescriptorRelativePath(repo.getDescriptorTemplatePath());
        if (relativeDescriptorPath.isEmpty()) {
            throw new InternalException(
                    "Blueprint repository descriptor template path is not configured; cannot write descriptor lineage for blueprint %s"
                            .formatted(blueprintId)
            );
        }

        Path descriptorFile = rootTargetPath.resolve(relativeDescriptorPath);
        if (!Files.isRegularFile(descriptorFile)) {
            throw new InternalException(
                    "Expected rendered data product descriptor at '%s' after templating; file missing"
                            .formatted(descriptorFile)
            );
        }

        
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(descriptorFile);
        } catch (IOException e) {
            throw new InternalException(
                    "Failed to read data product descriptor at '%s' for lineage enrichment"
                            .formatted(descriptorFile),
                    e
            );
        }
        
        DescriptorFormat format = detectFormat(descriptorFile.getFileName().toString(), bytes);
        ObjectMapper rootMapper = format == DescriptorFormat.JSON ? JSON_DPDS : YAML_DPDS;
        Parser parser = ParserFactory.getParser(rootMapper);

        JsonNode rootNode;
        try {
            rootNode = rootMapper.readTree(bytes);
        } catch (IOException e) {
            throw new InternalException(
                    "Failed to parse data product descriptor at '%s' for lineage enrichment"
                            .formatted(descriptorFile),
                    e
            );
        }

        DataProductVersion dpv = parseDpdsDescriptor(descriptorFile, parser, rootNode);

        org.opendatamesh.dpds.model.blueprint.Blueprint lineage =
                toDpdsBlueprint(blueprintVersion, resolvedParameters);
        dpv.setblueprint(lineage);

        JsonNode serialized = serializeDpdsDescriptor(descriptorFile, parser, dpv);

        writeDescriptorToFile(descriptorFile, format, serialized);
    }

    private void writeDescriptorToFile(Path descriptorFile, DescriptorFormat format, JsonNode serialized) {
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
                    e
            );
        }
    }

    private JsonNode serializeDpdsDescriptor(Path descriptorFile, Parser parser, DataProductVersion dpv) {
        JsonNode serialized;
        try {
            serialized = parser.serialize(dpv);
        } catch (IOException e) {
            throw new InternalException(
                    "DPDS parser could not serialize data product descriptor after lineage enrichment ('%s')"
                            .formatted(descriptorFile),
                    e
            );
        }
        return serialized;
    }

    private DataProductVersion parseDpdsDescriptor(Path descriptorFile, Parser parser, JsonNode rootNode) {
        DataProductVersion dpv;
        try {
            dpv = parser.deserialize(rootNode);
        } catch (IOException e) {
            throw new InternalException(
                    "DPDS parser could not deserialize data product descriptor at '%s'"
                            .formatted(descriptorFile),
                    e
            );
        }
        return dpv;
    }

    static org.opendatamesh.dpds.model.blueprint.Blueprint toDpdsBlueprint(
            BlueprintVersion blueprintVersion,
            Map<String, JsonNode> resolvedParameters
    ) {
        org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint platformBp = blueprintVersion.getBlueprint();
        org.opendatamesh.dpds.model.blueprint.Blueprint out = new org.opendatamesh.dpds.model.blueprint.Blueprint();
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

    /**
     * Repository-relative path to the rendered descriptor (Velocity output), without {@code .vm}.
     */
    private String renderedDescriptorRelativePath(String descriptorTemplatePath) {
        if (descriptorTemplatePath == null || descriptorTemplatePath.isBlank()) {
            return "";
        }
        String normalized = descriptorTemplatePath.replace('\\', '/').replaceFirst("^/+", "");
        if (normalized.endsWith(".vm")) {
            normalized = normalized.substring(0, normalized.length() - 3);
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
                        .formatted(fileName)
        );
    }
}
