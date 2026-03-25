package org.opendatamesh.platform.pp.blueprint.manifest.model;

import org.opendatamesh.platform.pp.blueprint.manifest.model.core.ManifestComponentBase;
import org.opendatamesh.platform.pp.blueprint.manifest.model.protectedresource.ManifestProtectedResourceIntegrity;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestVisitor;

public class ManifestProtectedResource extends ManifestComponentBase {

    private String path;
    private ManifestProtectedResourceIntegrity integrity;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public ManifestProtectedResourceIntegrity getIntegrity() {
        return integrity;
    }

    public void setIntegrity(ManifestProtectedResourceIntegrity integrity) {
        this.integrity = integrity;
    }

    public void accept(ManifestVisitor visitor) {
        visitor.visit(this);
    }
}
