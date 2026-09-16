package org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation;

import org.opendatamesh.platform.pp.blueprint.manifest.model.core.ManifestComponentBase;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestVisitor;

public class ManifestTargetRepository extends ManifestComponentBase {

    private String key;
    private String description;
    /**
     * When {@code true}, designates this key as the data-product root repository (lineage, descriptor
     * enrichment, registry primary pointer). Exactly one entry must set {@code isRoot: true}.
     */
    private Boolean isRoot;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getIsRoot() {
        return isRoot;
    }

    public void setIsRoot(Boolean isRoot) {
        this.isRoot = isRoot;
    }

    public void accept(ManifestVisitor visitor) {
        visitor.visit(this);
    }
}
