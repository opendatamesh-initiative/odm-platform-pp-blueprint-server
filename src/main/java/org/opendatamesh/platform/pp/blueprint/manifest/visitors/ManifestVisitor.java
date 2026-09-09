package org.opendatamesh.platform.pp.blueprint.manifest.visitors;

import org.opendatamesh.platform.pp.blueprint.manifest.model.Manifest;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestComposition;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestParameter;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestProtectedResource;
import org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation.ManifestInstantiationEntry;
import org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation.ManifestTargetRepository;

public interface ManifestVisitor {

    void visit(Manifest manifest);

    void visit(ManifestParameter manifestParameter);

    void visit(ManifestProtectedResource manifestProtectedResource);

    void visit(ManifestComposition manifestComposition);

    void visit(ManifestTargetRepository targetRepository);

    void visit(ManifestInstantiationEntry instantiationEntry);
}
