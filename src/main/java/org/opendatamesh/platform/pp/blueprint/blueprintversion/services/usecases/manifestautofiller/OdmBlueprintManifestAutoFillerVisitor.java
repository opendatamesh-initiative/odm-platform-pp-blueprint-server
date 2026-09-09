package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.manifestautofiller;

import org.opendatamesh.platform.pp.blueprint.manifest.model.Manifest;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestComposition;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestParameter;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestProtectedResource;
import org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation.ManifestInstantiationEntry;
import org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation.ManifestInstantiationType;
import org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation.ManifestTarget;
import org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation.ManifestTargetRepository;
import org.opendatamesh.platform.pp.blueprint.manifest.model.parameter.ManifestParameterUi;
import org.opendatamesh.platform.pp.blueprint.manifest.model.parameter.ManifestParameterValidation;
import org.opendatamesh.platform.pp.blueprint.manifest.model.protectedresource.ManifestProtectedResourceIntegrity;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestInstantiationEntryVisitor;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestParameterVisitor;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestProtectedResourceVisitor;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestVisitor;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

class OdmBlueprintManifestAutoFillerVisitor implements ManifestVisitor, ManifestParameterVisitor,
        ManifestProtectedResourceVisitor, ManifestInstantiationEntryVisitor {

    private final String blueprintName;

    OdmBlueprintManifestAutoFillerVisitor(String blueprintName) {
        this.blueprintName = blueprintName;
    }

    @Override
    public void visit(Manifest manifest) {
        seedManifestSpecAndIdentity(manifest);
        autofillParameterDefaults(manifest.getParameters());

        String repositoryKey = ensureTargetRepositoriesDefaults(manifest);
        ensureInstantiationDefaults(manifest, repositoryKey);
    }

    @Override
    public void visit(ManifestParameter manifestParameter) {
        if (manifestParameter.getKey() != null && !manifestParameter.getKey().isEmpty()) {
            if (manifestParameter.getType() == null) {
                manifestParameter.setType(ManifestParameter.ManifestParameterType.STRING);
            }
        }
    }

    @Override
    public void visit(ManifestProtectedResource manifestProtectedResource) {
        // No auto fill for protected resources
    }

    @Override
    public void visit(ManifestTargetRepository targetRepository) {
        // No auto fill for repository entries beyond manifest-level defaults
    }

    @Override
    public void visit(ManifestInstantiationEntry instantiationEntry) {
        if (instantiationEntry.getTargets() == null
                && instantiationEntry.getType() == ManifestInstantiationType.ROOT) {
            ManifestTarget target = new ManifestTarget();
            target.setSourcePath("./");
            target.setRepo(OdmBlueprintManifestAutoFiller.DEFAULT_REPOSITORY_KEY);
            target.setDestinationPath("./");
            List<ManifestTarget> targets = new ArrayList<>();
            targets.add(target);
            instantiationEntry.setTargets(targets);
        }
    }

    @Override
    public void visit(ManifestParameterValidation validation) {
        // No auto fill for parameter validation
    }

    @Override
    public void visit(ManifestParameterUi ui) {
        // No auto fill for parameter ui
    }

    @Override
    public void visit(ManifestProtectedResourceIntegrity integrity) {
        // No auto fill for protected resource integrity
    }

    @Override
    public void visit(ManifestTarget target) {
        // No auto fill for targets beyond instantiation-level defaults
    }

    @Override
    public void visit(ManifestComposition manifestComposition) {
        // No auto fill for manifest composition
    }

    private void seedManifestSpecAndIdentity(Manifest manifest) {
        if (!StringUtils.hasText(manifest.getSpec())) {
            manifest.setSpec(Manifest.SPEC_NAME);
        }
        if (!StringUtils.hasText(manifest.getSpecVersion())) {
            manifest.setSpecVersion("1.0.0");
        }
        if (!StringUtils.hasText(manifest.getVersion())) {
            manifest.setVersion("1.0.0");
        }
        if (!StringUtils.hasText(manifest.getName())) {
            manifest.setName(blueprintName);
        }
    }

    private void autofillParameterDefaults(List<ManifestParameter> parameters) {
        if (parameters == null) {
            return;
        }
        for (ManifestParameter parameter : parameters) {
            if (parameter != null) {
                parameter.accept(this);
            }
        }
    }

    /**
     * Ensures {@code manifest.targetRepositories} exists and returns the resolved root repository key
     * (defaulting to {@link OdmBlueprintManifestAutoFiller#DEFAULT_REPOSITORY_KEY} when missing).
     */
    private String ensureTargetRepositoriesDefaults(Manifest manifest) {
        if (CollectionUtils.isEmpty(manifest.getTargetRepositories())) {
            ManifestTargetRepository repository = new ManifestTargetRepository();
            repository.setKey(OdmBlueprintManifestAutoFiller.DEFAULT_REPOSITORY_KEY);
            repository.setDescription("Target repository for all data product assets");
            repository.setIsRoot(true);
            List<ManifestTargetRepository> repositories = new ArrayList<>();
            repositories.add(repository);
            manifest.setTargetRepositories(repositories);
        }

        ManifestTargetRepository firstRepository = manifest.getTargetRepositories().getFirst();
        String repositoryKey = firstRepository.getKey();
        if (!StringUtils.hasText(repositoryKey)) {
            repositoryKey = OdmBlueprintManifestAutoFiller.DEFAULT_REPOSITORY_KEY;
            firstRepository.setKey(repositoryKey);
        }
        return repositoryKey.trim();
    }

    private void ensureInstantiationDefaults(Manifest manifest, String repositoryKey) {
        if (CollectionUtils.isEmpty(manifest.getInstantiation())) {
            ManifestInstantiationEntry rootEntry = new ManifestInstantiationEntry();
            rootEntry.setType(ManifestInstantiationType.ROOT);
            ManifestTarget target = new ManifestTarget();
            target.setSourcePath("./");
            target.setRepo(repositoryKey);
            target.setDestinationPath("./");
            List<ManifestTarget> targets = new ArrayList<>();
            targets.add(target);
            rootEntry.setTargets(targets);
            List<ManifestInstantiationEntry> instantiation = new ArrayList<>();
            instantiation.add(rootEntry);
            manifest.setInstantiation(instantiation);
            return;
        }

        for (ManifestInstantiationEntry entry : manifest.getInstantiation()) {
            if (entry != null) {
                entry.accept(this);
            }
        }
    }
}
