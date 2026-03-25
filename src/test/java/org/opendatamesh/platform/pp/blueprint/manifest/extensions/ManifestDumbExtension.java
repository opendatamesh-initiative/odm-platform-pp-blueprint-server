package org.opendatamesh.platform.pp.blueprint.manifest.extensions;

import org.opendatamesh.platform.pp.blueprint.manifest.model.core.ManifestComponentBase;

class ManifestDumbExtension extends ManifestComponentBase {

    private String fieldA;
    private String fieldB;

    ManifestDumbExtension() {
    }

    public String getFieldA() {
        return fieldA;
    }

    public void setFieldA(String fieldA) {
        this.fieldA = fieldA;
    }

    public String getFieldB() {
        return fieldB;
    }

    public void setFieldB(String fieldB) {
        this.fieldB = fieldB;
    }
}
