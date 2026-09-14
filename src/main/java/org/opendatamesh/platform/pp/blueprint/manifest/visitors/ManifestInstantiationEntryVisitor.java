package org.opendatamesh.platform.pp.blueprint.manifest.visitors;

import org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation.ManifestTarget;

/**
 * Visitor for nested nodes under {@code instantiation[]} (route targets).
 */
public interface ManifestInstantiationEntryVisitor {

    void visit(ManifestTarget target);
}
