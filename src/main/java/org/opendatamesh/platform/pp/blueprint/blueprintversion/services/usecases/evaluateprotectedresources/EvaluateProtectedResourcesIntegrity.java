package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources;

import com.fasterxml.jackson.databind.JsonNode;
import org.opendatamesh.platform.git.model.Repository;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.BlueprintRepo;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.BlueprintGitNamingConventions;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.InstantiationScenario;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate.BlueprintRepositoryLogicalType;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate.InstantiateBlueprintVersionCommand;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate.RenderedTreeSnapshot;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate.TargetRepositoryDto;
import org.opendatamesh.platform.pp.blueprint.exceptions.NotFoundException;
import org.opendatamesh.platform.pp.blueprint.manifest.model.Manifest;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestInstantiation;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestProtectedResource;
import org.opendatamesh.platform.pp.blueprint.manifest.parser.ManifestParserFactory;
import org.opendatamesh.platform.pp.blueprint.utils.usecases.UseCase;
import org.springframework.http.HttpHeaders;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
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
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

class EvaluateProtectedResourcesIntegrity implements UseCase {

    private final EvaluateProtectedResourcesIntegrityCommand command;
    private final EvaluateProtectedResourcesIntegrityPresenter presenter;
    private final EvaluateProtectedResourcesIntegrityPersistencyOutboundPort persistencyPort;
    private final EvaluateProtectedResourcesIntegrityCredentialsOutboundPort credentialsPort;
    private final EvaluateProtectedResourcesIntegrityGitOutboundPort productGitPort;
    private final EvaluateProtectedResourcesIntegrityInstantiateOutboundPort instantiatePort;
    private final EvaluateProtectedResourcesIntegrityDigestOutboundPort digestPort;

    private boolean presented;

    EvaluateProtectedResourcesIntegrity(
            EvaluateProtectedResourcesIntegrityCommand command,
            EvaluateProtectedResourcesIntegrityPresenter presenter,
            EvaluateProtectedResourcesIntegrityPersistencyOutboundPort persistencyPort,
            EvaluateProtectedResourcesIntegrityCredentialsOutboundPort credentialsPort,
            EvaluateProtectedResourcesIntegrityGitOutboundPort productGitPort,
            EvaluateProtectedResourcesIntegrityInstantiateOutboundPort instantiatePort,
            EvaluateProtectedResourcesIntegrityDigestOutboundPort digestPort
    ) {
        this.command = command;
        this.presenter = presenter;
        this.persistencyPort = persistencyPort;
        this.credentialsPort = credentialsPort;
        this.productGitPort = productGitPort;
        this.instantiatePort = instantiatePort;
        this.digestPort = digestPort;
    }

    @Override
    public void execute() {
        Path actualTree = null;
        RenderedTreeSnapshot snapshot = new RenderedTreeSnapshot();
        try {
            actualTree = evaluate(snapshot);
        } catch (RuntimeException e) {
            if (!presented) {
                presenter.presentInfrastructureFailure(infrastructureMessage(e));
            }
        } finally {
            deleteRecursively(actualTree);
            deleteRecursively(snapshot.getExpectedTreeRoot());
        }
    }

    private Path evaluate(RenderedTreeSnapshot snapshot) {
        BlueprintVersion blueprintVersion;
        try {
            blueprintVersion = persistencyPort.findByBlueprintNameAndVersion(
                    command.blueprintName(), command.blueprintVersionNumber());
        } catch (NotFoundException e) {
            presentInfrastructure(
                    "Cannot check protected resources: blueprint '%s' version '%s' was not found"
                            .formatted(command.blueprintName(), command.blueprintVersionNumber()));
            return null;
        }

        BlueprintRepo blueprintRepo = blueprintVersion.getBlueprint() == null
                ? null
                : blueprintVersion.getBlueprint().getBlueprintRepo();
        if (blueprintRepo == null
                || !StringUtils.hasText(blueprintRepo.getRemoteUrlHttp())
                || blueprintRepo.getProviderType() == null) {
            presentInfrastructure("Cannot check protected resources: the blueprint repository is not configured");
            return null;
        }

        Manifest manifest = parseManifest(blueprintVersion);
        InstantiationScenario scenario = resolveScenario(manifest);
        if (scenario != InstantiationScenario.MONOREPO_NO_COMPOSITION) {
            presentNotApplicable(
                    "Protected-resource checks currently apply only to monorepo blueprints without composition");
            return null;
        }
        if (CollectionUtils.isEmpty(manifest.getProtectedResources())) {
            presentNotApplicable("This blueprint does not declare protected resources");
            return null;
        }

        if (!StringUtils.hasText(command.publicationTag())
                || command.productRepo() == null
                || !StringUtils.hasText(command.productRepo().remoteUrlHttp())
                || !StringUtils.hasText(command.productRepo().providerType())) {
            presentFailed(List.of(),
                    "Cannot check protected resources: the data product version is missing its Git repository or tag");
            return null;
        }

        HttpHeaders productHeaders = credentialsPort.resolveHeaders(
                command.productRepo().providerType(),
                command.productRepo().providerBaseUrl()
        ).orElse(null);
        if (productHeaders == null) {
            presentInfrastructure("Cannot check protected resources: Git access is not configured for provider "
                    + command.productRepo().providerType());
            return null;
        }
        HttpHeaders blueprintHeaders = credentialsPort.resolveHeaders(
                blueprintRepo.getProviderType().name(),
                blueprintRepo.getProviderBaseUrl()
        ).orElse(null);
        if (blueprintHeaders == null) {
            presentInfrastructure("Cannot check protected resources: Git access is not configured for provider "
                    + blueprintRepo.getProviderType().name());
            return null;
        }

        Path actualTree = copyProductClone(productHeaders);

        instantiatePort.executeLocalInstantiation(
                buildInstantiateCommand(),
                blueprintHeaders,
                snapshot
        );
        if (snapshot.getExpectedTreeRoot() == null || !Files.isDirectory(snapshot.getExpectedTreeRoot())) {
            presentInfrastructure("Cannot check protected resources: failed to rebuild the expected files from the blueprint");
            return actualTree;
        }

        List<ProtectedResourceMismatch> mismatches = new ArrayList<>();
        for (ManifestProtectedResource protectedResource : manifest.getProtectedResources()) {
            compareProtectedResource(protectedResource, actualTree, snapshot.getExpectedTreeRoot(), mismatches);
        }
        if (mismatches.isEmpty()) {
            presentPassed("Protected resources match the blueprint");
        } else {
            presentFailed(mismatches, formatFailureMessage(mismatches));
        }
        return actualTree;
    }

    private Path copyProductClone(HttpHeaders productHeaders) {
        try {
            Path actualTree = Files.createTempDirectory("blueprint-integrity-actual-");
            productGitPort.withClonedProductAtTag(
                    command.productRepo(),
                    command.publicationTag(),
                    productHeaders,
                    clonePath -> copyTree(clonePath, actualTree)
            );
            return actualTree;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to copy the data product version files", e);
        }
    }

    private void copyTree(Path source, Path destination) {
        try {
            Files.walkFileTree(source, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (dir.getFileName() != null && ".git".equals(dir.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    Path relative = source.relativize(dir);
                    if (!relative.toString().isEmpty()) {
                        Files.createDirectories(destination.resolve(relative));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (Files.isSymbolicLink(file) || !Files.isRegularFile(file)) {
                        return FileVisitResult.CONTINUE;
                    }
                    Path target = destination.resolve(source.relativize(file));
                    if (target.getParent() != null) {
                        Files.createDirectories(target.getParent());
                    }
                    Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to copy the data product version files", e);
        }
    }

    private InstantiateBlueprintVersionCommand buildInstantiateCommand() {
        ProductRepoLocator productRepo = command.productRepo();
        Repository repository = EvaluateProtectedResourcesIntegrityGitOutboundPortImpl.toGitRepository(productRepo);
        TargetRepositoryDto target = new TargetRepositoryDto(
                productRepo.externalIdentifier(),
                BlueprintRepositoryLogicalType.ROOT,
                productRepo.defaultBranch(),
                repository
        );
        Map<String, JsonNode> parameters = command.lineageParameters() == null
                ? Map.of()
                : command.lineageParameters();
        return new InstantiateBlueprintVersionCommand(
                command.blueprintName(),
                command.blueprintVersionNumber(),
                List.of(target),
                parameters,
                BlueprintGitNamingConventions.DEFAULT_COMMIT_AUTHOR_NAME,
                BlueprintGitNamingConventions.DEFAULT_COMMIT_AUTHOR_EMAIL
        );
    }

    private void compareProtectedResource(
            ManifestProtectedResource protectedResource,
            Path actualRoot,
            Path expectedRoot,
            List<ProtectedResourceMismatch> mismatches
    ) {
        String declaredPath = protectedResource.getPath();
        if (protectedResource.getIntegrity() != null
                && StringUtils.hasText(protectedResource.getIntegrity().getAlgorithm())
                && !"sha256".equalsIgnoreCase(protectedResource.getIntegrity().getAlgorithm().trim())) {
            mismatches.add(new ProtectedResourceMismatch(
                    declaredPath,
                    MismatchKind.UNSUPPORTED_ALGORITHM,
                    List.of(),
                    "unsupported integrity algorithm '%s'"
                            .formatted(protectedResource.getIntegrity().getAlgorithm())
            ));
            return;
        }

        DigestResult actual = digestPort.digest(actualRoot, declaredPath);
        DigestResult expected = digestPort.digest(expectedRoot, declaredPath);

        if (actual.hasError()) {
            mismatches.add(new ProtectedResourceMismatch(
                    declaredPath, actual.error(), List.of(), actual.detail()));
            return;
        }
        if (expected.hasError()) {
            mismatches.add(new ProtectedResourceMismatch(
                    declaredPath, expected.error(), List.of(), expected.detail()));
            return;
        }

        if (actual.isEmptyMatch() && expected.isEmptyMatch()) {
            mismatches.add(new ProtectedResourceMismatch(
                    declaredPath,
                    MismatchKind.MISSING_ON_PUBLISHED,
                    List.of(),
                    "the path is missing from both the data product version and the blueprint"
            ));
            mismatches.add(new ProtectedResourceMismatch(
                    declaredPath,
                    MismatchKind.MISSING_ON_REINSTANTIATED,
                    List.of(),
                    "the path is missing from both the data product version and the blueprint"
            ));
            return;
        }
        if (actual.isEmptyMatch()) {
            mismatches.add(new ProtectedResourceMismatch(
                    declaredPath,
                    MismatchKind.MISSING_ON_PUBLISHED,
                    List.copyOf(expected.fileDigests().keySet()),
                    "the path is missing from the data product version"
            ));
            return;
        }
        if (expected.isEmptyMatch()) {
            mismatches.add(new ProtectedResourceMismatch(
                    declaredPath,
                    MismatchKind.MISSING_ON_REINSTANTIATED,
                    List.copyOf(actual.fileDigests().keySet()),
                    "the path is not produced by the blueprint"
            ));
            return;
        }

        List<String> missingOnPublished = new ArrayList<>();
        List<String> missingOnReinstantiated = new ArrayList<>();
        List<String> contentDiffers = new ArrayList<>();
        for (String relative : unionKeys(actual.fileDigests(), expected.fileDigests())) {
            boolean onActual = actual.fileDigests().containsKey(relative);
            boolean onExpected = expected.fileDigests().containsKey(relative);
            if (onExpected && !onActual) {
                missingOnPublished.add(relative);
            } else if (onActual && !onExpected) {
                missingOnReinstantiated.add(relative);
            } else if (!actual.fileDigests().get(relative).equals(expected.fileDigests().get(relative))) {
                contentDiffers.add(relative);
            }
        }
        if (!missingOnPublished.isEmpty()) {
            mismatches.add(new ProtectedResourceMismatch(
                    declaredPath, MismatchKind.MISSING_ON_PUBLISHED, missingOnPublished, null));
        }
        if (!missingOnReinstantiated.isEmpty()) {
            mismatches.add(new ProtectedResourceMismatch(
                    declaredPath, MismatchKind.MISSING_ON_REINSTANTIATED, missingOnReinstantiated, null));
        }
        if (!contentDiffers.isEmpty()) {
            mismatches.add(new ProtectedResourceMismatch(
                    declaredPath, MismatchKind.CONTENT_DIFFERS, contentDiffers, null));
        }
    }

    private static List<String> unionKeys(Map<String, String> left, Map<String, String> right) {
        Map<String, String> union = new LinkedHashMap<>(left);
        union.putAll(right);
        return new ArrayList<>(union.keySet());
    }

    static String formatFailureMessage(List<ProtectedResourceMismatch> mismatches) {
        List<String> parts = new ArrayList<>();
        for (ProtectedResourceMismatch mismatch : mismatches) {
            parts.add(formatMismatch(mismatch));
        }
        return String.join("; ", parts);
    }

    private static String formatMismatch(ProtectedResourceMismatch mismatch) {
        String resource = mismatch.declaredPath();
        String files = fileClause(mismatch.affectedFiles());
        boolean plural = mismatch.affectedFiles() != null && mismatch.affectedFiles().size() > 1;
        return switch (mismatch.kind()) {
            case MISSING_ON_PUBLISHED -> files == null
                    ? "Protected resource '%s' is missing from the data product version".formatted(resource)
                    : "Protected resource '%s' is missing %s from the data product version".formatted(resource, files);
            case MISSING_ON_REINSTANTIATED -> files == null
                    ? "Protected resource '%s' is not produced by the blueprint".formatted(resource)
                    : "Protected resource '%s': %s %s in the data product version but %s not produced by the blueprint"
                    .formatted(resource, files, plural ? "are" : "is", plural ? "are" : "is");
            case CONTENT_DIFFERS -> files == null
                    ? "Protected resource '%s': file contents differ from the blueprint".formatted(resource)
                    : "Protected resource '%s': contents of %s differ from the blueprint".formatted(resource, files);
            case INVALID_PATH ->
                    "Protected resource '%s' is not a valid path".formatted(resource);
            case SYMLINK ->
                    "Protected resource '%s' cannot be checked because it contains a symbolic link".formatted(resource);
            case UNSUPPORTED_ALGORITHM ->
                    "Protected resource '%s' uses an integrity check that is not supported".formatted(resource);
        };
    }

    private static String fileClause(List<String> files) {
        if (files == null || files.isEmpty()) {
            return null;
        }
        List<String> quoted = new ArrayList<>(files.size());
        for (String file : files) {
            quoted.add("'" + file + "'");
        }
        String joined = String.join(", ", quoted);
        return files.size() == 1 ? "file " + joined : "files " + joined;
    }

    private InstantiationScenario resolveScenario(Manifest manifest) {
        if (manifest.getInstantiation() == null || manifest.getInstantiation().getStrategy() == null) {
            throw new IllegalStateException("Cannot check protected resources: the blueprint manifest is missing an instantiation strategy");
        }
        boolean hasComposition = !CollectionUtils.isEmpty(manifest.getComposition());
        ManifestInstantiation.InstantiationStrategy strategy = manifest.getInstantiation().getStrategy();
        return switch (strategy) {
            case MONOREPO -> hasComposition
                    ? InstantiationScenario.MONOREPO_WITH_COMPOSITION
                    : InstantiationScenario.MONOREPO_NO_COMPOSITION;
            case POLYREPO -> hasComposition
                    ? InstantiationScenario.POLYREPO_WITH_COMPOSITION
                    : InstantiationScenario.POLYREPO_NO_COMPOSITION;
        };
    }

    private Manifest parseManifest(BlueprintVersion blueprintVersion) {
        try {
            return ManifestParserFactory.getParser().deserialize(blueprintVersion.getContent());
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Cannot check protected resources: the blueprint manifest could not be read",
                    e);
        }
    }

    private void presentNotApplicable(String reason) {
        presented = true;
        presenter.presentNotApplicable(reason);
    }

    private void presentPassed(String message) {
        presented = true;
        presenter.presentPassed(message);
    }

    private void presentFailed(List<ProtectedResourceMismatch> mismatches, String message) {
        presented = true;
        presenter.presentFailed(mismatches, message);
    }

    private void presentInfrastructure(String message) {
        presented = true;
        presenter.presentInfrastructureFailure(message);
    }

    private String infrastructureMessage(RuntimeException e) {
        String message = e.getMessage();
        if (!StringUtils.hasText(message)) {
            message = e.getClass().getSimpleName();
        }
        return message.toLowerCase(Locale.ROOT).contains("token")
                ? "Cannot complete the protected-resource check"
                : message;
    }

    static void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            });
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }
}
