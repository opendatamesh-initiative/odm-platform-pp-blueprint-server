package org.opendatamesh.platform.pp.blueprint.manifest.visitors;

import org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation.ManifestInstantiationRepository;
import org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation.ManifestInstantiationRoot;

/**
 * Visitor for nested nodes under {@code instantiation} (repositories and root).
 */
public interface ManifestInstantiationVisitor {

    void visit(ManifestInstantiationRepository repository);

    void visit(ManifestInstantiationRoot root);
}
