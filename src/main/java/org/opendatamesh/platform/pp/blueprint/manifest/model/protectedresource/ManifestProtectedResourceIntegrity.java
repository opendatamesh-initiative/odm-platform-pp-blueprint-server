package org.opendatamesh.platform.pp.blueprint.manifest.model.protectedresource;

import org.opendatamesh.platform.pp.blueprint.manifest.model.core.ManifestComponentBase;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestProtectedResourceVisitor;

public class ManifestProtectedResourceIntegrity extends ManifestComponentBase {

    private String algorithm;
    private String value;

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public void accept(ManifestProtectedResourceVisitor visitor) {
        visitor.visit(this);
    }
}
