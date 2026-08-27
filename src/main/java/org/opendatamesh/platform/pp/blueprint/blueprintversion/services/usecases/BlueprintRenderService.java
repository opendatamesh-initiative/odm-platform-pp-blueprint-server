package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.BlueprintRepo;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.exceptions.InternalException;
import org.opendatamesh.platform.pp.blueprint.manifest.model.Manifest;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestParameter;
import org.opendatamesh.platform.pp.blueprint.manifest.parser.ManifestParserFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

/**
 * Shared Velocity render-and-copy utility used by instantiate and update
 * templating outbound ports.
 */
@Service
public class BlueprintRenderService {

    private static final ObjectMapper YAML_OBJECT_MAPPER = new ObjectMapper(
            new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));
    private static final String ODM_BLUEPRINT_DIR = ".odm/blueprint";
    private static final String MANIFEST_DEFAULT_FILENAME = "blueprint-manifest.yaml";
    private static final Set<String> COPY_TREE_SKIP_NAMES = Set.of(".git");

    /**
     * Whole-tree 1→1 render used by update (and retained for backward
     * compatibility).
     * Includes parent lineage sidecar relocate into the rendered tree.
     * <p>
     * Renders an entire blueprint source tree into a target workspace.
     * Resolves parameters from the version's manifest, evaluates Velocity
     * templates,
     * records the blueprint lineage sidecar, and copies the result to
     * {@code targetRoot}.
     * Used by the update flow for the monorepo, no-composition case.
     */
    public void monorepoNoCompositionRenderAndCopy(
            BlueprintVersion blueprintVersion,
            Map<String, JsonNode> parameters,
            Path sourceRoot,
            Path targetRoot) {
        Manifest manifest = parseManifest(blueprintVersion);
        Map<String, JsonNode> fullParameters = retrieveFullListOfParametersAndValues(
                manifest, emptyIfNull(parameters));
        VelocityEngine velocityEngine = createVelocityEngine();
        VelocityContext velocityContext = buildVelocityContext(fullParameters);

        Path tempRoot = initTemporaryDirectory();
        try {
            copyTree(sourceRoot, tempRoot);
            renderVelocityTemplates(tempRoot, velocityEngine, velocityContext);
            relocateBlueprintReadme(tempRoot, blueprintVersion);
            relocateManifestFile(tempRoot, blueprintVersion);
            copyTree(tempRoot, targetRoot);
        } catch (IOException e) {
            throw new InternalException(
                    "Failed while rendering blueprint templates or copying files from '%s' to '%s': %s"
                            .formatted(sourceRoot, targetRoot, e.getMessage()),
                    e);
        } finally {
            deleteRecursively(tempRoot);
        }
    }

    /**
     * Renders the data-product descriptor template from the parent blueprint source
     * into the
     * root target workspace. The output is written at the same repository-relative
     * path, with
     * a {@code .vm} suffix stripped when present. Does nothing if
     * {@code descriptorTemplatePath}
     * is blank.
     */
    public void renderDescriptorTemplate(
            Path sourceRoot,
            String descriptorTemplatePath,
            Path targetRoot,
            Map<String, JsonNode> parameters) {
        if (descriptorTemplatePath == null || descriptorTemplatePath.isBlank()) {
            return;
        }
        String templatePath = normalizeRepoPath(descriptorTemplatePath);
        Path templateFile = loadExistingTemplate(sourceRoot, templatePath);
        String renderedPath = stripVmSuffix(templatePath);
        Path destinationFile = targetRoot.resolve(renderedPath);
        VelocityEngine velocityEngine = createVelocityEngine();
        VelocityContext velocityContext = buildVelocityContext(emptyIfNull(parameters));

        Path tempRoot = initTemporaryDirectory();
        try {
            copyFile(templateFile, tempRoot.resolve(templatePath));
            renderVelocityTemplates(tempRoot, velocityEngine, velocityContext);
            Path renderedFile = requireRenderedDescriptor(tempRoot.resolve(renderedPath), destinationFile);
            copyFile(renderedFile, destinationFile);
        } catch (IOException e) {
            throw new InternalException(
                    "Failed while rendering descriptor template from '%s' to '%s': %s"
                            .formatted(templateFile, destinationFile, e.getMessage()),
                    e);
        } finally {
            deleteRecursively(tempRoot);
        }
    }

    /**
     * Renders a source file or directory and copies the result into a destination
     * path
     * under the target workspace. Velocity templates in the subtree are evaluated
     * with
     * the given parameters. Does not record lineage; callers that need parent
     * provenance
     * use {@link #relocateParentLineageSidecar(Path, BlueprintVersion)}.
     */
    public void renderAndCopySubtree(
            Path sourceRoot,
            String sourcePath,
            Path targetRoot,
            String destinationPath,
            Map<String, JsonNode> parameters) {
        Path sourceSubtree = requireExistingSubtree(sourceRoot, sourcePath);
        Path destinationSubtree = resolveSubtree(targetRoot, destinationPath);
        VelocityEngine velocityEngine = createVelocityEngine();
        VelocityContext velocityContext = buildVelocityContext(emptyIfNull(parameters));

        Path tempRoot = initTemporaryDirectory();
        try {
            stageSubtree(sourceSubtree, tempRoot);
            renderVelocityTemplates(tempRoot, velocityEngine, velocityContext);
            Files.createDirectories(destinationSubtree);
            copyTree(tempRoot, destinationSubtree);
        } catch (IOException e) {
            throw new InternalException(
                    "Failed while rendering blueprint templates or copying files from '%s' to '%s': %s"
                            .formatted(sourceSubtree, destinationSubtree, e.getMessage()),
                    e);
        } finally {
            deleteRecursively(tempRoot);
        }
    }

    /**
     * Records parent blueprint lineage on the designated root target: moves the
     * README
     * (when present) and writes a manifest snapshot under {@code .odm/blueprint/}.
     */
    public void relocateParentLineageSidecar(Path rootTarget, BlueprintVersion parentVersion) {
        try {
            relocateBlueprintReadme(rootTarget, parentVersion);
            relocateManifestFile(rootTarget, parentVersion);
        } catch (IOException e) {
            throw new InternalException(
                    "Failed while recording parent lineage sidecar under '%s': %s"
                            .formatted(rootTarget, e.getMessage()),
                    e);
        }
    }

    private Path requireExistingSubtree(Path root, String relativePath) {
        Path subtree = resolveSubtree(root, relativePath);
        if (!Files.exists(subtree)) {
            throw new InternalException(
                    "Source path '%s' does not exist under '%s'".formatted(relativePath, root));
        }
        return subtree;
    }

    private Path loadExistingTemplate(Path sourceRoot, String templatePath) {
        Path templateFile = sourceRoot.resolve(templatePath);
        if (!Files.isRegularFile(templateFile)) {
            throw new InternalException(
                    "Descriptor template '%s' does not exist under '%s'".formatted(templatePath, sourceRoot));
        }
        return templateFile;
    }

    private Path requireRenderedDescriptor(Path renderedFile, Path destinationFile) {
        if (!Files.isRegularFile(renderedFile)) {
            throw new InternalException(
                    "Expected rendered data product descriptor at '%s' after templating; file missing"
                            .formatted(destinationFile));
        }
        return renderedFile;
    }

    private Path resolveSubtree(Path root, String relativePath) {
        String normalized = normalizeRepoPath(relativePath);
        if (normalized.isEmpty() || ".".equals(normalized) || "./".equals(normalized)) {
            return root;
        }
        return root.resolve(normalized);
    }

    private Path initTemporaryDirectory() {
        try {
            return Files.createTempDirectory("odm-blueprint-render-");
        } catch (IOException e) {
            throw new InternalException("Could not create a temporary directory for blueprint rendering", e);
        }
    }

    private void stageSubtree(Path sourceSubtree, Path tempRoot) throws IOException {
        if (Files.isDirectory(sourceSubtree)) {
            copyTree(sourceSubtree, tempRoot);
            return;
        }
        copyFile(sourceSubtree, tempRoot.resolve(sourceSubtree.getFileName().toString()));
    }

    private void relocateBlueprintReadme(Path gitRoot, BlueprintVersion blueprintVersion) throws IOException {
        BlueprintRepo repo = blueprintVersion.getBlueprint().getBlueprintRepo();
        String readmePath = normalizeRepoPath(repo.getReadmePath());
        if (readmePath.isEmpty()) {
            return;
        }
        Path readmeFile = gitRoot.resolve(readmePath);
        if (!Files.isRegularFile(readmeFile)) {
            return;
        }
        Path lineageDir = odmBlueprintDirectory(gitRoot);
        Files.move(
                readmeFile,
                lineageDir.resolve(readmeFile.getFileName().toString()),
                StandardCopyOption.REPLACE_EXISTING);
    }

    private void relocateManifestFile(Path gitRoot, BlueprintVersion blueprintVersion) throws IOException {
        BlueprintRepo repo = blueprintVersion.getBlueprint().getBlueprintRepo();
        Path manifestFile = gitRoot.resolve(normalizeRepoPath(repo.getManifestRootPath()));
        if (Files.isRegularFile(manifestFile)) {
            Files.delete(manifestFile);
        }
        Path manifestSnapshot = odmBlueprintDirectory(gitRoot).resolve(MANIFEST_DEFAULT_FILENAME);
        YAML_OBJECT_MAPPER.writeValue(manifestSnapshot.toFile(), blueprintVersion.getContent());
    }

    private Path odmBlueprintDirectory(Path gitRoot) throws IOException {
        return Files.createDirectories(gitRoot.resolve(ODM_BLUEPRINT_DIR));
    }

    private Map<String, JsonNode> emptyIfNull(Map<String, JsonNode> parameters) {
        return parameters == null ? Map.of() : parameters;
    }

    private Map<String, JsonNode> retrieveFullListOfParametersAndValues(
            Manifest manifest,
            Map<String, JsonNode> parameters) {
        Map<String, JsonNode> out = new LinkedHashMap<>();
        if (manifest.getParameters() == null) {
            return out;
        }
        for (ManifestParameter p : manifest.getParameters()) {
            String key = p.getKey();
            JsonNode fromRequest = parameters.get(key);
            if (fromRequest != null && !fromRequest.isNull()) {
                out.put(key, fromRequest);
            } else if (p.getDefaultValue() != null && !p.getDefaultValue().isNull()) {
                out.put(key, p.getDefaultValue());
            }
        }
        return out;
    }

    private VelocityEngine createVelocityEngine() {
        VelocityEngine engine = new VelocityEngine();
        engine.init();
        return engine;
    }

    private VelocityContext buildVelocityContext(Map<String, JsonNode> resolved) {
        VelocityContext ctx = new VelocityContext();
        for (Map.Entry<String, JsonNode> e : resolved.entrySet()) {
            ctx.put(e.getKey(), jsonNodeToJava(e.getValue()));
        }
        return ctx;
    }

    private Object jsonNodeToJava(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isInt()) {
            return node.intValue();
        }
        if (node.isLong()) {
            return node.longValue();
        }
        if (node.isDouble() || node.isFloat()) {
            return node.doubleValue();
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isArray() || node.isObject()) {
            return YAML_OBJECT_MAPPER.convertValue(node, Object.class);
        }
        return node.toString();
    }

    private void renderVelocityTemplates(Path root, VelocityEngine engine, VelocityContext context) throws IOException {
        List<Path> vmFiles = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.getFileName().toString().endsWith(".vm")) {
                    vmFiles.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        vmFiles.sort(Comparator.comparing(Path::toString));
        for (Path vmPath : vmFiles) {
            String template = Files.readString(vmPath, StandardCharsets.UTF_8);
            StringWriter writer = new StringWriter();
            boolean ok = engine.evaluate(context, writer, vmPath.toString(), template);
            if (!ok) {
                throw new InternalException(
                        "Apache Velocity reported an error while evaluating template '%s'".formatted(vmPath));
            }
            Path outputPath = vmPath.resolveSibling(vmPath.getFileName().toString().replaceFirst("\\.vm$", ""));
            Files.writeString(outputPath, writer.toString(), StandardCharsets.UTF_8);
            Files.delete(vmPath);
        }
    }

    private void copyTree(Path from, Path to) throws IOException {
        Files.walkFileTree(from, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (isCopyTreeSkippedName(dir.getFileName())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Path targetDir = to.resolve(from.relativize(dir));
                if (!Files.exists(targetDir)) {
                    Files.createDirectories(targetDir);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (isCopyTreeSkippedName(file.getFileName())) {
                    return FileVisitResult.CONTINUE;
                }
                Path dest = to.resolve(from.relativize(file));
                Files.createDirectories(Objects.requireNonNull(dest.getParent()));
                Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void copyFile(Path from, Path to) throws IOException {
        Files.createDirectories(Objects.requireNonNull(to.getParent()));
        Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
    }

    private boolean isCopyTreeSkippedName(Path pathName) {
        return pathName != null && COPY_TREE_SKIP_NAMES.contains(pathName.toString());
    }

    private void deleteRecursively(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }

    private String normalizeRepoPath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        return path.replace('\\', '/').replaceFirst("^/+", "");
    }

    private String stripVmSuffix(String path) {
        if (path.endsWith(".vm")) {
            return path.substring(0, path.length() - 3);
        }
        return path;
    }

    private Manifest parseManifest(BlueprintVersion blueprintVersion) {
        JsonNode raw = blueprintVersion.getContent();
        try {
            return ManifestParserFactory.getParser().deserialize(raw);
        } catch (IOException e) {
            throw new InternalException(
                    "Could not parse manifest content for blueprint version '%s' (versionNumber=%s)"
                            .formatted(blueprintVersion.getName(), blueprintVersion.getVersionNumber()),
                    e);
        }
    }
}
