package org.opendatamesh.platform.pp.blueprint.manifest.parser;

import org.opendatamesh.platform.pp.blueprint.manifest.model.Manifest;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestComposition;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestParameter;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestProtectedResource;
import org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation.ManifestInstantiationEntry;
import org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation.ManifestTarget;
import org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation.ManifestTargetRepository;
import org.opendatamesh.platform.pp.blueprint.manifest.model.parameter.ManifestParameterUi;
import org.opendatamesh.platform.pp.blueprint.manifest.model.parameter.ManifestParameterValidation;
import org.opendatamesh.platform.pp.blueprint.manifest.model.protectedresource.ManifestProtectedResourceIntegrity;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestInstantiationEntryVisitor;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestParameterVisitor;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestProtectedResourceVisitor;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestVisitor;

/**
 * Walks the manifest object graph and applies {@link ManifestExtensionHandler} at each {@code ManifestComponentBase} node.
 */
class ManifestExtensionVisitorImpl implements ManifestVisitor, ManifestParameterVisitor,
        ManifestInstantiationEntryVisitor, ManifestProtectedResourceVisitor {

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
        if (manifest.getTargetRepositories() != null) {
            manifest.getTargetRepositories().forEach(r -> r.accept(this));
        }
        if (manifest.getInstantiation() != null) {
            manifest.getInstantiation().forEach(e -> e.accept(this));
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
    }

    @Override
    public void visit(ManifestTargetRepository targetRepository) {
        extensionHandler.handleComponentBaseExtension(targetRepository, ManifestTargetRepository.class);
    }

    @Override
    public void visit(ManifestInstantiationEntry instantiationEntry) {
        extensionHandler.handleComponentBaseExtension(instantiationEntry, ManifestInstantiationEntry.class);
        if (instantiationEntry.getTargets() != null) {
            ManifestInstantiationEntryVisitor entryVisitor = this;
            instantiationEntry.getTargets().forEach(t -> t.accept(entryVisitor));
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
    public void visit(ManifestTarget target) {
        extensionHandler.handleComponentBaseExtension(target, ManifestTarget.class);
    }

    @Override
    public void visit(ManifestProtectedResourceIntegrity integrity) {
        extensionHandler.handleComponentBaseExtension(integrity, ManifestProtectedResourceIntegrity.class);
    }
}
