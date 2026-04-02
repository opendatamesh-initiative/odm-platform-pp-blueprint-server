package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.BlueprintRepo;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.exceptions.InternalException;
import org.opendatamesh.platform.pp.blueprint.manifest.model.Manifest;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestParameter;
import org.opendatamesh.platform.pp.blueprint.manifest.parser.ManifestParserFactory;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

import static org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestInstantiation.InstantiationStrategy.MONOREPO;

class InstantiateBlueprintVersionTemplatingOutboundPortImpl implements InstantiateBlueprintVersionTemplatingOutboundPort {

    private static final ObjectMapper YAML_OBJECT_MAPPER = new ObjectMapper(
            new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
    );
    /**
     * Aligned with {@link InstantiateBlueprintVersionOdmBlueprintManifestOutboundPortImpl}.
     */
    private static final String VERSION = "(v1|1\\.\\d+\\.\\d+.*)";
    private static final String ODM_BLUEPRINT_DIR = ".odm/blueprint";
    private static final String MANIFEST_DEFAULT_FILENAME = "blueprint-manifest.yaml";

    @Override
    public void renderAndCopy(
            BlueprintVersion blueprintVersion,
            Map<String, JsonNode> parameters,
            Map<SourceRepositoryDto, Path> sourceRepositories,
            Map<TargetRepositoryDto, Path> targetRepositories
    ) {
        if (!(Manifest.SPEC_NAME.equalsIgnoreCase(blueprintVersion.getSpec())
                && blueprintVersion.getSpecVersion().matches(VERSION))) {
            throw new UnsupportedOperationException(
                    "Templating supports only manifest spec '%s' with specVersion matching pattern %s; got spec=%s, specVersion=%s"
                            .formatted(Manifest.SPEC_NAME, VERSION, blueprintVersion.getSpec(), blueprintVersion.getSpecVersion())
            );
        }
        Manifest manifest = parseManifest(blueprintVersion);
        if (isMonorepoNoComposition(manifest)) {
            handleMonorepoNoCompositionRenderAndCopy(blueprintVersion, parameters, sourceRepositories, targetRepositories, manifest);
        } else {
            throw new UnsupportedOperationException(
                    "Templating for this blueprint layout is not implemented yet (expected monorepo without composition)"
            );
        }
    }

    private void handleMonorepoNoCompositionRenderAndCopy(BlueprintVersion blueprintVersion, Map<String, JsonNode> parameters, Map<SourceRepositoryDto, Path> sourceRepositories, Map<TargetRepositoryDto, Path> targetRepositories, Manifest manifest) {
        Map.Entry<SourceRepositoryDto, Path> rootSourceRepo = sourceRepositories.entrySet().stream()
                .filter(entry -> entry.getKey().type() == BlueprintRepositoryLogicalType.ROOT)
                .findFirst()
                .orElseThrow(() -> new InternalException(
                        "Blueprint instantiation requires a cloned source repository with type 'root'; none found in %s"
                                .formatted(sourceRepositories.keySet())
                ));
        Map.Entry<TargetRepositoryDto, Path> rootTargetRepo = targetRepositories.entrySet().stream()
                .filter(entry -> entry.getKey().type() == BlueprintRepositoryLogicalType.ROOT)
                .findFirst()
                .orElseThrow(() -> new InternalException(
                        "Blueprint instantiation requires a target repository with type 'root'; none found in %s"
                                .formatted(targetRepositories.keySet())
                ));

        BlueprintRepo repo = blueprintVersion.getBlueprint().getBlueprintRepo();

        Map<String, JsonNode> fullParameters = retrieveFullListOfParametersAndValues(manifest, parameters == null ? Map.of() : parameters);
        VelocityEngine velocityEngine = createVelocityEngine();
        VelocityContext velocityContext = buildVelocityContext(fullParameters);

        Path sourceRoot = rootSourceRepo.getValue();
        Path targetRoot = rootTargetRepo.getValue();
        Path tempRoot = initTemporaryDirectory();
        try {
            copyTree(sourceRoot, tempRoot);
            renderVelocityTemplates(tempRoot, velocityEngine, velocityContext);
            relocateBlueprintReadme(tempRoot, repo);
            relocateManifestFile(tempRoot, blueprintVersion);
            copyTree(tempRoot, targetRoot);
        } catch (IOException e) {
            throw new InternalException(
                    "Failed while rendering blueprint templates or copying files from '%s' to '%s': %s"
                            .formatted(sourceRoot, targetRoot, e.getMessage()),
                    e
            );
        } finally {
            deleteRecursively(tempRoot);
        }
    }

    private static Path initTemporaryDirectory() {
        Path tempRoot;
        try {
            tempRoot = Files.createTempDirectory("odm-blueprint-inst-");
        } catch (IOException e) {
            throw new InternalException("Could not create a temporary directory for blueprint rendering", e);
        }
        return tempRoot;
    }

    private void relocateBlueprintReadme(Path tempRoot, BlueprintRepo repo) throws IOException {
        String readmeRel = normalizeRepoPath(repo.getReadmePath());
        if (!readmeRel.isEmpty()) {
            Path readmeSrc = tempRoot.resolve(readmeRel);
            if (Files.isRegularFile(readmeSrc)) {
                Path lineageDir = tempRoot.resolve(ODM_BLUEPRINT_DIR);
                Files.createDirectories(lineageDir);
                Path dest = lineageDir.resolve(readmeSrc.getFileName().toString());
                Files.move(readmeSrc, dest, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void relocateManifestFile(
            Path tempRoot,
            BlueprintVersion blueprintVersion
    ) throws IOException {
        //Remove old manifest file
        BlueprintRepo repo = blueprintVersion.getBlueprint().getBlueprintRepo();
        String manifestRel = normalizeRepoPath(repo.getManifestRootPath());
        Path manifestFile = tempRoot.resolve(manifestRel);
        if (Files.isRegularFile(manifestFile)) {
            Files.delete(manifestFile);
        }
        //Save manifest on .odm/blueprint/.... with stored manifest content
        Path odmBlueprintDir = tempRoot.resolve(ODM_BLUEPRINT_DIR);
        Files.createDirectories(odmBlueprintDir);
        Path dest = odmBlueprintDir.resolve(MANIFEST_DEFAULT_FILENAME);
        YAML_OBJECT_MAPPER.writeValue(dest.toFile(), blueprintVersion.getContent());
    }

    private Map<String, JsonNode> retrieveFullListOfParametersAndValues(Manifest manifest, Map<String, JsonNode> parameters) {
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

    private static Object jsonNodeToJava(JsonNode node) {
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
                throw new InternalException("Apache Velocity reported an error while evaluating template '%s'".formatted(vmPath));
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
                Path targetDir = to.resolve(from.relativize(dir));
                if (!Files.exists(targetDir)) {
                    Files.createDirectories(targetDir);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path dest = to.resolve(from.relativize(file));
                Files.createDirectories(Objects.requireNonNull(dest.getParent()));
                Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
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

    /**
     * Repository-relative path without leading slash; empty means repository root.
     */
    private String normalizeRepoPath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        return path.replace('\\', '/').replaceFirst("^/+", "");
    }

    // Case 2.1. Monorepo, no composition, see manifest specification README.md
    private boolean isMonorepoNoComposition(Manifest manifest) {
        return MONOREPO.equals(manifest.getInstantiation().getStrategy()) &&
                CollectionUtils.isEmpty(manifest.getComposition());
    }

    private Manifest parseManifest(BlueprintVersion blueprintVersion) {
        JsonNode raw = blueprintVersion.getContent();
        try {
            return ManifestParserFactory.getParser().deserialize(raw);
        } catch (IOException e) {
            throw new InternalException(
                    "Could not parse manifest content for blueprint version '%s' (versionNumber=%s)"
                            .formatted(blueprintVersion.getName(), blueprintVersion.getVersionNumber()),
                    e
            );
        }
    }

}
