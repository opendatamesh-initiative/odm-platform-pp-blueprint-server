package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources;

import org.opendatamesh.platform.pp.blueprint.blueprint.entities.BlueprintRepo;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.exceptions.NotFoundException;
import org.opendatamesh.platform.pp.blueprint.manifest.model.Manifest;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestProtectedResource;
import org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation.ManifestTargetRepository;
import org.opendatamesh.platform.pp.blueprint.utils.usecases.UseCase;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

class EvaluateProtectedResourcesIntegrity implements UseCase {

    private final EvaluateProtectedResourcesIntegrityCommand command;
    private final EvaluateProtectedResourcesIntegrityPresenter presenter;
    private final EvaluateProtectedResourcesIntegrityPersistencyOutboundPort persistencyPort;
    private final EvaluateProtectedResourcesIntegrityGitOutboundPort productGitPort;
    private final EvaluateProtectedResourcesIntegrityInstantiateOutboundPort instantiatePort;
    private final EvaluateProtectedResourcesIntegrityDigestOutboundPort digestPort;

    private boolean presented;

    EvaluateProtectedResourcesIntegrity(
            EvaluateProtectedResourcesIntegrityCommand command,
            EvaluateProtectedResourcesIntegrityPresenter presenter,
            EvaluateProtectedResourcesIntegrityPersistencyOutboundPort persistencyPort,
            EvaluateProtectedResourcesIntegrityGitOutboundPort productGitPort,
            EvaluateProtectedResourcesIntegrityInstantiateOutboundPort instantiatePort,
            EvaluateProtectedResourcesIntegrityDigestOutboundPort digestPort
    ) {
        this.command = command;
        this.presenter = presenter;
        this.persistencyPort = persistencyPort;
        this.productGitPort = productGitPort;
        this.instantiatePort = instantiatePort;
        this.digestPort = digestPort;
    }

    @Override
    public void execute() {
        try {
            BlueprintVersion blueprintVersion = loadBlueprintVersion();
            if (blueprintVersion == null) {
                return;
            }
            Manifest manifest = persistencyPort.readManifest(blueprintVersion);
            if (isEmptyProtection(manifest)) {
                presentNotApplicable("This blueprint does not declare protected resources");
                return;
            }
            List<ProtectedPublishedTarget> protectedTargets = resolveProtectedPublishedTargets(manifest);
            if (protectedTargets == null) {
                return;
            }
            if (refuseIfSourceBlueprintRepositoryMissing(blueprintVersion)) {
                return;
            }
            try (TargetWorkingTrees expected = instantiatePort.reinstantiateBlueprintLocally(
                    blueprintVersion, command)) {
                compareProtectedTargets(protectedTargets, expected);
            }
        } catch (RuntimeException e) {
            presentInfrastructureIfNeeded(e);
        }
    }

    private BlueprintVersion loadBlueprintVersion() {
        try {
            return persistencyPort.findByBlueprintNameAndVersion(
                    command.blueprintName(), command.blueprintVersionNumber());
        } catch (NotFoundException e) {
            presentInfrastructure(
                    "Cannot check protected resources: blueprint '%s' version '%s' was not found"
                            .formatted(command.blueprintName(), command.blueprintVersionNumber()));
            return null;
        }
    }

    private boolean isEmptyProtection(Manifest manifest) {
        return manifest.getProtectedResources() == null || manifest.getProtectedResources().isEmpty();
    }

    private boolean refuseIfSourceBlueprintRepositoryMissing(BlueprintVersion blueprintVersion) {
        BlueprintRepo blueprintRepo = blueprintVersion.getBlueprint() == null
                ? null
                : blueprintVersion.getBlueprint().getBlueprintRepo();
        if (blueprintRepo == null
                || !hasText(blueprintRepo.getRemoteUrlHttp())
                || blueprintRepo.getProviderType() == null) {
            presentInfrastructure("Cannot check protected resources: the blueprint repository is not configured");
            return true;
        }
        return false;
    }

    private List<ProtectedPublishedTarget> resolveProtectedPublishedTargets(Manifest manifest) {
        String rootKey = resolveExplicitRootKey(manifest);
        if (rootKey == null) {
            presentFailed(List.of(),
                    "Cannot check protected resources: the blueprint manifest does not declare exactly one root target repository");
            return null;
        }
        Set<String> declaredKeys = declaredRepositoryKeys(manifest);
        Map<String, List<ManifestProtectedResource>> resourcesByKey = new LinkedHashMap<>();
        List<String> unknown = new ArrayList<>();
        for (ManifestProtectedResource protectedResource : manifest.getProtectedResources()) {
            if (protectedResource == null) {
                continue;
            }
            String resolvedKey = resolveProtectedRepositoryKey(protectedResource, rootKey, declaredKeys, unknown);
            if (resolvedKey == null) {
                continue;
            }
            resourcesByKey.computeIfAbsent(resolvedKey, ignored -> new ArrayList<>()).add(protectedResource);
        }
        if (!unknown.isEmpty()) {
            presentFailed(List.of(), String.join("; ", unknown));
            return null;
        }
        List<ProtectedPublishedTarget> targets = new ArrayList<>();
        for (Map.Entry<String, List<ManifestProtectedResource>> entry : resourcesByKey.entrySet()) {
            ProtectedPublishedTarget target = resolvePublishedTarget(entry.getKey(), rootKey, entry.getValue());
            if (target == null) {
                return null;
            }
            targets.add(target);
        }
        return targets;
    }

    private String resolveExplicitRootKey(Manifest manifest) {
        if (manifest.getTargetRepositories() == null) {
            return null;
        }
        String rootKey = null;
        int rootCount = 0;
        for (ManifestTargetRepository repository : manifest.getTargetRepositories()) {
            if (repository == null || !Boolean.TRUE.equals(repository.getIsRoot())) {
                continue;
            }
            rootCount++;
            if (hasText(repository.getKey())) {
                rootKey = repository.getKey().trim();
            }
        }
        if (rootCount != 1 || !hasText(rootKey)) {
            return null;
        }
        return rootKey;
    }

    private Set<String> declaredRepositoryKeys(Manifest manifest) {
        Set<String> keys = new LinkedHashSet<>();
        if (manifest.getTargetRepositories() == null) {
            return keys;
        }
        for (ManifestTargetRepository repository : manifest.getTargetRepositories()) {
            if (repository != null && hasText(repository.getKey())) {
                keys.add(repository.getKey().trim());
            }
        }
        return keys;
    }

    private String resolveProtectedRepositoryKey(
            ManifestProtectedResource protectedResource,
            String rootKey,
            Set<String> declaredKeys,
            List<String> unknown
    ) {
        if (!hasText(protectedResource.getRepository())) {
            return rootKey;
        }
        String key = protectedResource.getRepository().trim();
        if (!declaredKeys.contains(key)) {
            unknown.add("Cannot check protected resources: protected resource '%s' names unknown repository key '%s'"
                    .formatted(protectedResource.getPath(), key));
            return null;
        }
        return key;
    }

    private ProtectedPublishedTarget resolvePublishedTarget(
            String repositoryKey,
            String rootKey,
            List<ManifestProtectedResource> resources
    ) {
        if (rootKey.equals(repositoryKey)) {
            if (!isCompleteLocator(command.rootProductRepo()) || !hasText(command.rootPublicationRef())) {
                presentFailed(List.of(),
                        "Cannot check protected resources: the data product version is missing its Git repository or tag");
                return null;
            }
            return new ProtectedPublishedTarget(
                    repositoryKey, command.rootProductRepo(), command.rootPublicationRef(), resources);
        }
        List<KeyedProductRepoLocator> locators = exactLocatorMatches(repositoryKey);
        List<KeyedProductRepoRef> refs = exactRefMatches(repositoryKey);
        if (locators.size() != 1 || refs.size() != 1
                || !isCompleteLocator(locators.get(0).locator())
                || !hasText(refs.get(0).ref())) {
            presentFailed(List.of(),
                    "Cannot check protected resources: publication metadata for repository key '%s' is missing, blank, or duplicated"
                            .formatted(repositoryKey));
            return null;
        }
        return new ProtectedPublishedTarget(
                repositoryKey, locators.get(0).locator(), refs.get(0).ref(), resources);
    }

    private List<KeyedProductRepoLocator> exactLocatorMatches(String repositoryKey) {
        List<KeyedProductRepoLocator> matches = new ArrayList<>();
        for (KeyedProductRepoLocator locator : command.additionalProductRepos()) {
            if (locator != null && repositoryKey.equals(locator.repositoryKey())) {
                matches.add(locator);
            }
        }
        return matches;
    }

    private List<KeyedProductRepoRef> exactRefMatches(String repositoryKey) {
        List<KeyedProductRepoRef> matches = new ArrayList<>();
        for (KeyedProductRepoRef ref : command.additionalRefs()) {
            if (ref != null && repositoryKey.equals(ref.repositoryKey())) {
                matches.add(ref);
            }
        }
        return matches;
    }

    private static boolean isCompleteLocator(ProductRepoLocator locator) {
        return locator != null
                && hasText(locator.remoteUrlHttp())
                && hasText(locator.providerType());
    }

    private void compareProtectedTargets(
            List<ProtectedPublishedTarget> protectedTargets,
            TargetWorkingTrees expected
    ) {
        List<ProtectedResourceMismatch> mismatches = new ArrayList<>();
        for (ProtectedPublishedTarget target : protectedTargets) {
            WorkingTree expectedTree = expected.get(target.repositoryKey());
            if (expectedTree == null) {
                presentFailed(List.of(),
                        "Cannot check protected resources: expected tree for repository key '%s' was not produced"
                                .formatted(target.repositoryKey()));
                return;
            }
            try (WorkingTree published = productGitPort.clonePublishedDataProductVersion(
                    target.locator(), target.ref())) {
                for (ManifestProtectedResource protectedResource : target.resources()) {
                    compareProtectedResource(protectedResource, published, expectedTree, mismatches);
                }
            }
        }
        if (mismatches.isEmpty()) {
            presentPassed("Protected resources match the blueprint");
        } else {
            presentFailed(mismatches, formatFailureMessage(mismatches));
        }
    }

    private void compareProtectedResource(
            ManifestProtectedResource protectedResource,
            WorkingTree published,
            WorkingTree expected,
            List<ProtectedResourceMismatch> mismatches
    ) {
        String declaredPath = protectedResource.getPath();
        if (protectedResource.getIntegrity() != null
                && hasText(protectedResource.getIntegrity().getAlgorithm())
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

        DigestResult actual = digestPort.computeDigest(published, declaredPath);
        DigestResult expectedDigest = digestPort.computeDigest(expected, declaredPath);

        if (actual.hasError()) {
            mismatches.add(new ProtectedResourceMismatch(
                    declaredPath, actual.error(), List.of(), actual.detail()));
            return;
        }
        if (expectedDigest.hasError()) {
            mismatches.add(new ProtectedResourceMismatch(
                    declaredPath, expectedDigest.error(), List.of(), expectedDigest.detail()));
            return;
        }

        if (actual.isEmptyMatch() && expectedDigest.isEmptyMatch()) {
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
                    List.copyOf(expectedDigest.fileDigests().keySet()),
                    "the path is missing from the data product version"
            ));
            return;
        }
        if (expectedDigest.isEmptyMatch()) {
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
        for (String relative : unionKeys(actual.fileDigests(), expectedDigest.fileDigests())) {
            boolean onActual = actual.fileDigests().containsKey(relative);
            boolean onExpected = expectedDigest.fileDigests().containsKey(relative);
            if (onExpected && !onActual) {
                missingOnPublished.add(relative);
            } else if (onActual && !onExpected) {
                missingOnReinstantiated.add(relative);
            } else if (!actual.fileDigests().get(relative).equals(expectedDigest.fileDigests().get(relative))) {
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

    private void presentInfrastructureIfNeeded(RuntimeException e) {
        if (!presented) {
            presenter.presentInfrastructureFailure(infrastructureMessage(e));
        }
    }

    private String infrastructureMessage(RuntimeException e) {
        String message = e.getMessage();
        if (!hasText(message)) {
            message = e.getClass().getSimpleName();
        }
        return message.toLowerCase(Locale.ROOT).contains("token")
                ? "Cannot complete the protected-resource check"
                : message;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record ProtectedPublishedTarget(
            String repositoryKey,
            ProductRepoLocator locator,
            String ref,
            List<ManifestProtectedResource> resources
    ) {
    }
}
