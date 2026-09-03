package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.manifestautofiller;

import org.opendatamesh.platform.pp.blueprint.manifest.model.Manifest;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestComposition;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestInstantiation;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestParameter;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestProtectedResource;
import org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation.ManifestInstantiationRepository;
import org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation.ManifestInstantiationRoot;
import org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation.ManifestTarget;
import org.opendatamesh.platform.pp.blueprint.manifest.model.parameter.ManifestParameterUi;
import org.opendatamesh.platform.pp.blueprint.manifest.model.parameter.ManifestParameterValidation;
import org.opendatamesh.platform.pp.blueprint.manifest.model.protectedresource.ManifestProtectedResourceIntegrity;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestCompositionVisitor;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestInstantiationRootVisitor;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestInstantiationVisitor;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestParameterVisitor;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestProtectedResourceVisitor;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestVisitor;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

class OdmBlueprintManifestAutoFillerVisitor implements ManifestVisitor, ManifestParameterVisitor,
        ManifestProtectedResourceVisitor, ManifestInstantiationVisitor, ManifestInstantiationRootVisitor,
        ManifestCompositionVisitor {

    private final String blueprintName;

    OdmBlueprintManifestAutoFillerVisitor(String blueprintName) {
        this.blueprintName = blueprintName;
    }

    @Override
    public void visit(Manifest manifest) {
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

        List<ManifestParameter> parameters = manifest.getParameters();
        if (parameters != null) {
            for (ManifestParameter parameter : parameters) {
                if (parameter != null) {
                    parameter.accept(this);
                }
            }
        }

        if (manifest.getInstantiation() == null) {
            manifest.setInstantiation(new ManifestInstantiation());
        }
        manifest.getInstantiation().accept(this);
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
    public void visit(ManifestInstantiation manifestInstantiation) {
        if (CollectionUtils.isEmpty(manifestInstantiation.getRepositories())) {
            ManifestInstantiationRepository repository = new ManifestInstantiationRepository();
            repository.setKey(OdmBlueprintManifestAutoFiller.DEFAULT_REPOSITORY_KEY);
            repository.setDescription("Target repository for all data product assets");
            List<ManifestInstantiationRepository> repositories = new ArrayList<>();
            repositories.add(repository);
            manifestInstantiation.setRepositories(repositories);
        }

        String repositoryKey = manifestInstantiation.getRepositories().getFirst().getKey();
        if (!StringUtils.hasText(repositoryKey)) {
            repositoryKey = OdmBlueprintManifestAutoFiller.DEFAULT_REPOSITORY_KEY;
            manifestInstantiation.getRepositories().getFirst().setKey(repositoryKey);
        }

        if (manifestInstantiation.getRoot() == null) {
            ManifestInstantiationRoot root = new ManifestInstantiationRoot();
            root.setRepository(repositoryKey.trim());
            ManifestTarget target = new ManifestTarget();
            target.setSourcePath("./");
            target.setRepository(repositoryKey.trim());
            target.setPath("./");
            List<ManifestTarget> targets = new ArrayList<>();
            targets.add(target);
            root.setTargets(targets);
            manifestInstantiation.setRoot(root);
            return;
        }

        ManifestInstantiationRoot root = manifestInstantiation.getRoot();
        // Do not autofill root.repository when omitted: it is required and rejected at publish/instantiate.
        // Only seed a default whole-tree route when root.targets is omitted (null).
        // An explicit empty list is a structural error rejected by publish/instantiate validators.
        if (root.getTargets() == null) {
            if (!StringUtils.hasText(root.getRepository())) {
                root.setRepository(repositoryKey.trim());
            }
            ManifestTarget target = new ManifestTarget();
            target.setSourcePath("./");
            target.setRepository(
                    StringUtils.hasText(root.getRepository())
                            ? root.getRepository().trim()
                            : repositoryKey.trim());
            target.setPath("./");
            List<ManifestTarget> targets = new ArrayList<>();
            targets.add(target);
            root.setTargets(targets);
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
    public void visit(ManifestInstantiationRepository repository) {
        // No auto fill for repository entries beyond instantiation-level defaults
    }

    @Override
    public void visit(ManifestInstantiationRoot root) {
        // Handled in visit(ManifestInstantiation)
    }

    @Override
    public void visit(ManifestTarget target) {
        // No auto fill for targets beyond instantiation-level defaults
    }

    @Override
    public void visit(ManifestComposition manifestComposition) {
        // No auto fill for manifest composition
    }
}
