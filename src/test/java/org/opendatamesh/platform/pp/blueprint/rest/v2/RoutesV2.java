package org.opendatamesh.platform.pp.blueprint.rest.v2;

public enum RoutesV2 {

    GIT_PROVIDERS("/api/v2/pp/blueprint/git-providers");
        
    private final String path;
    
    RoutesV2(String path) {
        this.path = path;
    }
    
    public String getPath() {
        return path;
    }
}