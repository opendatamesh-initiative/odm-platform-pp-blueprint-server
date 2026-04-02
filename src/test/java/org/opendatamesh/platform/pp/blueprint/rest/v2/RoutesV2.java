package org.opendatamesh.platform.pp.blueprint.rest.v2;

public enum RoutesV2 {

    GIT_PROVIDERS("/api/v2/pp/blueprint/git-providers"),
    BLUEPRINTS("/api/v2/pp/blueprint/blueprints"),
    BLUEPRINT_REGISTER("/api/v2/pp/blueprint/blueprints/register"),
    BLUEPRINT_VERSIONS("/api/v2/pp/blueprint/blueprints-versions"),
    BLUEPRINT_VERSIONS_INSTANTIATE("/api/v2/pp/blueprint/blueprints-versions/instantiate");

    private final String path;
    
    RoutesV2(String path) {
        this.path = path;
    }
    
    public String getPath() {
        return path;
    }
}