package org.opendatamesh.platform.pp.blueprint.manifest.visitors;

import org.opendatamesh.platform.pp.blueprint.manifest.model.Manifest;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestComposition;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestInstantiation;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestParameter;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestProtectedResource;

public interface ManifestVisitor {

    void visit(Manifest manifest);

    void visit(ManifestParameter manifestParameter);

    void visit(ManifestProtectedResource manifestProtectedResource);

    void visit(ManifestComposition manifestComposition);

    void visit(ManifestInstantiation manifestInstantiation);
}
