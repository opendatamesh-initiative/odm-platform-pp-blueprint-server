package org.opendatamesh.platform.pp.blueprint.manifest.visitors;

import org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation.ManifestTarget;

/**
 * Visitor for nested nodes under {@code composition[]} (shared {@link ManifestTarget} routes).
 */
public interface ManifestCompositionVisitor {

    void visit(ManifestTarget target);
}
