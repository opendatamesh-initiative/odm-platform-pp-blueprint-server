package org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation;

import org.opendatamesh.platform.pp.blueprint.manifest.model.core.ManifestComponentBase;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestCompositionVisitor;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestInstantiationRootVisitor;

/**
 * Shared route entry used by {@code instantiation.root.targets[]} and {@code composition[].targets[]}.
 * Explored via {@link ManifestCompositionVisitor} or {@link ManifestInstantiationRootVisitor}
 * depending on parent context.
 */
public class ManifestTarget extends ManifestComponentBase {

    private String sourcePath;
    private String repository;
    private String path;

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    public String getRepository() {
        return repository;
    }

    public void setRepository(String repository) {
        this.repository = repository;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void accept(ManifestCompositionVisitor visitor) {
        visitor.visit(this);
    }

    public void accept(ManifestInstantiationRootVisitor visitor) {
        visitor.visit(this);
    }
}
