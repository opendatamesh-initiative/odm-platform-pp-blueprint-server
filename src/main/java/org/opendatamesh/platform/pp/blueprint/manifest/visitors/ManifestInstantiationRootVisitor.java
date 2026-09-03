package org.opendatamesh.platform.pp.blueprint.manifest.visitors;

import org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation.ManifestTarget;

/**
 * Visitor for nested nodes under {@code instantiation.root} (shared {@link ManifestTarget} routes).
 */
public interface ManifestInstantiationRootVisitor {

    void visit(ManifestTarget target);
}
