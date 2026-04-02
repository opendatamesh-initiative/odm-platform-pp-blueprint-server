package org.opendatamesh.platform.pp.blueprint.blueprintversion.entities;

public enum ManifestSpec {
    ODM_BLUEPRINT_MANIFEST("odm-blueprint-manifest");

    private final String spec;

    ManifestSpec(String spec) {
        this.spec = spec;
    }

    public String getSpec() {
        return spec;
    }
}
