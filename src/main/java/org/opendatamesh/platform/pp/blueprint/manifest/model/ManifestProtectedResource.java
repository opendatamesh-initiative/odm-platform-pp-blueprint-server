package org.opendatamesh.platform.pp.blueprint.manifest.model;

import org.opendatamesh.platform.pp.blueprint.manifest.model.core.ManifestComponentBase;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestVisitor;

public class ManifestProtectedResource extends ManifestComponentBase {

    private String path;
    private String repository;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getRepository() {
        return repository;
    }

    public void setRepository(String repository) {
        this.repository = repository;
    }

    public void accept(ManifestVisitor visitor) {
        visitor.visit(this);
    }
}
