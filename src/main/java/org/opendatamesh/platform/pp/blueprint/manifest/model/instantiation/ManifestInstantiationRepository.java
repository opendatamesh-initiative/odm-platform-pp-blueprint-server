package org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation;

import org.opendatamesh.platform.pp.blueprint.manifest.model.core.ManifestComponentBase;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestInstantiationVisitor;

public class ManifestInstantiationRepository extends ManifestComponentBase {

    private String key;
    private String description;

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

    public void accept(ManifestInstantiationVisitor visitor) {
        visitor.visit(this);
    }
}
