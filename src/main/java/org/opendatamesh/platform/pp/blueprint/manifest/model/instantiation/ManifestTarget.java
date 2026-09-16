package org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation;

import org.opendatamesh.platform.pp.blueprint.manifest.model.core.ManifestComponentBase;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestInstantiationEntryVisitor;

/**
 * Route entry used by {@code instantiation[].targets[]}.
 */
public class ManifestTarget extends ManifestComponentBase {

    private String sourcePath;
    private String repo;
    private String destinationPath;

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    public String getRepo() {
        return repo;
    }

    public void setRepo(String repo) {
        this.repo = repo;
    }

    public String getDestinationPath() {
        return destinationPath;
    }

    public void setDestinationPath(String destinationPath) {
        this.destinationPath = destinationPath;
    }

    public void accept(ManifestInstantiationEntryVisitor visitor) {
        visitor.visit(this);
    }
}
