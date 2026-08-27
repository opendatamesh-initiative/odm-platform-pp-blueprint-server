package org.opendatamesh.platform.pp.blueprint.old.v1.resources;

public class RegistryProductVersionSearchOptions {

    private String dataProductUuid;
    private String versionNumber;

    public String getDataProductUuid() {
        return dataProductUuid;
    }

    public void setDataProductUuid(String dataProductUuid) {
        this.dataProductUuid = dataProductUuid;
    }

    public String getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(String versionNumber) {
        this.versionNumber = versionNumber;
    }
}
