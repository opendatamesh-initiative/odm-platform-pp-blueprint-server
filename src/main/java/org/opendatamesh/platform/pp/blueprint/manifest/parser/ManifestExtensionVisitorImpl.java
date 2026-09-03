package org.opendatamesh.platform.pp.blueprint.manifest.parser;

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

/**
 * Walks the manifest object graph and applies {@link ManifestExtensionHandler} at each {@code ManifestComponentBase} node.
 */
class ManifestExtensionVisitorImpl implements ManifestVisitor, ManifestParameterVisitor, ManifestInstantiationVisitor,
        ManifestInstantiationRootVisitor, ManifestCompositionVisitor, ManifestProtectedResourceVisitor {

    private final ManifestExtensionHandler extensionHandler;

    ManifestExtensionVisitorImpl(ManifestExtensionHandler extensionHandler) {
        this.extensionHandler = extensionHandler;
    }

    @Override
    public void visit(Manifest manifest) {
        extensionHandler.handleComponentBaseExtension(manifest, Manifest.class);
        if (manifest.getParameters() != null) {
            manifest.getParameters().forEach(p -> p.accept(this));
        }
        if (manifest.getProtectedResources() != null) {
            manifest.getProtectedResources().forEach(r -> r.accept(this));
        }
        if (manifest.getComposition() != null) {
            manifest.getComposition().forEach(c -> c.accept(this));
        }
        if (manifest.getInstantiation() != null) {
            manifest.getInstantiation().accept(this);
        }
    }

    @Override
    public void visit(ManifestParameter manifestParameter) {
        extensionHandler.handleComponentBaseExtension(manifestParameter, ManifestParameter.class);
        if (manifestParameter.getValidation() != null) {
            manifestParameter.getValidation().accept(this);
        }
        if (manifestParameter.getUi() != null) {
            manifestParameter.getUi().accept(this);
        }
    }

    @Override
    public void visit(ManifestProtectedResource manifestProtectedResource) {
        extensionHandler.handleComponentBaseExtension(manifestProtectedResource, ManifestProtectedResource.class);
        if (manifestProtectedResource.getIntegrity() != null) {
            manifestProtectedResource.getIntegrity().accept(this);
        }
    }

    @Override
    public void visit(ManifestComposition manifestComposition) {
        extensionHandler.handleComponentBaseExtension(manifestComposition, ManifestComposition.class);
        if (manifestComposition.getTargets() != null) {
            ManifestCompositionVisitor compositionVisitor = this;
            manifestComposition.getTargets().forEach(t -> t.accept(compositionVisitor));
        }
    }

    @Override
    public void visit(ManifestInstantiation manifestInstantiation) {
        extensionHandler.handleComponentBaseExtension(manifestInstantiation, ManifestInstantiation.class);
        if (manifestInstantiation.getRepositories() != null) {
            manifestInstantiation.getRepositories().forEach(r -> r.accept(this));
        }
        if (manifestInstantiation.getRoot() != null) {
            manifestInstantiation.getRoot().accept(this);
        }
    }

    @Override
    public void visit(ManifestParameterValidation validation) {
        extensionHandler.handleComponentBaseExtension(validation, ManifestParameterValidation.class);
    }

    @Override
    public void visit(ManifestParameterUi ui) {
        extensionHandler.handleComponentBaseExtension(ui, ManifestParameterUi.class);
    }

    @Override
    public void visit(ManifestInstantiationRepository repository) {
        extensionHandler.handleComponentBaseExtension(repository, ManifestInstantiationRepository.class);
    }

    @Override
    public void visit(ManifestInstantiationRoot root) {
        extensionHandler.handleComponentBaseExtension(root, ManifestInstantiationRoot.class);
        if (root.getTargets() != null) {
            ManifestInstantiationRootVisitor rootVisitor = this;
            root.getTargets().forEach(t -> t.accept(rootVisitor));
        }
    }

    @Override
    public void visit(ManifestTarget target) {
        extensionHandler.handleComponentBaseExtension(target, ManifestTarget.class);
    }

    @Override
    public void visit(ManifestProtectedResourceIntegrity integrity) {
        extensionHandler.handleComponentBaseExtension(integrity, ManifestProtectedResourceIntegrity.class);
    }
}
