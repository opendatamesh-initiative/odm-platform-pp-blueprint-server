package org.opendatamesh.platform.pp.blueprint.manifest.visitors;

import org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation.ManifestTargetRepositoryNaming;

/**
 * Visitor for nested nodes under {@code targetRepositories[]}.
 */
public interface ManifestTargetRepositoryVisitor {

    void visit(ManifestTargetRepositoryNaming naming);
}
