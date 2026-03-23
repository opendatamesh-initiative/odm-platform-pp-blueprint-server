package org.opendatamesh.platform.service.template.rest.v2;

public enum RoutesV2 {

    UP_HEALTH("/api/v2/up/health");
        
    private final String path;
    
    RoutesV2(String path) {
        this.path = path;
    }
    
    public String getPath() {
        return path;
    }
}