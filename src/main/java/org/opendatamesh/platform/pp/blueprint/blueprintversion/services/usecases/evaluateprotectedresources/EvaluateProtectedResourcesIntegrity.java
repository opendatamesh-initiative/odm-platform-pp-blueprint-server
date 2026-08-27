package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources;

import org.opendatamesh.platform.pp.blueprint.blueprint.entities.BlueprintRepo;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.InstantiationScenario;
import org.opendatamesh.platform.pp.blueprint.exceptions.NotFoundException;
import org.opendatamesh.platform.pp.blueprint.manifest.model.Manifest;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestInstantiation;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestProtectedResource;
import org.opendatamesh.platform.pp.blueprint.utils.usecases.UseCase;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
            if (refuseIfNotEvaluable(blueprintVersion, manifest)) {
                return;
            }
            try (WorkingTree published = productGitPort.clonePublishedDataProductVersion(
                    command.productRepo(), command.publicationTag());
                 WorkingTree expected = instantiatePort.reinstantiateBlueprintLocally(
                         blueprintVersion, command)) {
                compareProtectedResources(manifest.getProtectedResources(), published, expected);
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

    private boolean refuseIfNotEvaluable(BlueprintVersion blueprintVersion, Manifest manifest) {
        BlueprintRepo blueprintRepo = blueprintVersion.getBlueprint() == null
                ? null
                : blueprintVersion.getBlueprint().getBlueprintRepo();
        if (blueprintRepo == null
                || !StringUtils.hasText(blueprintRepo.getRemoteUrlHttp())
                || blueprintRepo.getProviderType() == null) {
            presentInfrastructure("Cannot check protected resources: the blueprint repository is not configured");
            return true;
        }
        if (resolveScenario(manifest) != InstantiationScenario.MONOREPO_NO_COMPOSITION) {
            presentNotApplicable(
                    "Protected-resource checks currently apply only to monorepo blueprints without composition");
            return true;
        }
        if (CollectionUtils.isEmpty(manifest.getProtectedResources())) {
            presentNotApplicable("This blueprint does not declare protected resources");
            return true;
        }
        if (!StringUtils.hasText(command.publicationTag())
                || command.productRepo() == null
                || !StringUtils.hasText(command.productRepo().remoteUrlHttp())
                || !StringUtils.hasText(command.productRepo().providerType())) {
            presentFailed(List.of(),
                    "Cannot check protected resources: the data product version is missing its Git repository or tag");
            return true;
        }
        return false;
    }

    private void compareProtectedResources(
            List<ManifestProtectedResource> protectedResources,
            WorkingTree published,
            WorkingTree expected
    ) {
        List<ProtectedResourceMismatch> mismatches = new ArrayList<>();
        for (ManifestProtectedResource protectedResource : protectedResources) {
            compareProtectedResource(protectedResource, published, expected, mismatches);
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
        if (!StringUtils.hasText(message)) {
            message = e.getClass().getSimpleName();
        }
        return message.toLowerCase(Locale.ROOT).contains("token")
                ? "Cannot complete the protected-resource check"
                : message;
    }
}
