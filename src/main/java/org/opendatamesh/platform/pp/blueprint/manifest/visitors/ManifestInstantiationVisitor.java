package org.opendatamesh.platform.pp.blueprint.manifest.visitors;

import org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation.ManifestInstantiationCompositionLayout;
import org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation.ManifestInstantiationTarget;

public interface ManifestInstantiationVisitor {

    void visit(ManifestInstantiationCompositionLayout compositionLayout);

    void visit(ManifestInstantiationTarget target);
}
