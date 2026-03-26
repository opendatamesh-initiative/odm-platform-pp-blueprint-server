package org.opendatamesh.platform.pp.blueprint.manifest.visitors;

import org.opendatamesh.platform.pp.blueprint.manifest.model.protectedresource.ManifestProtectedResourceIntegrity;

public interface ManifestProtectedResourceVisitor {

    void visit(ManifestProtectedResourceIntegrity integrity);
}
