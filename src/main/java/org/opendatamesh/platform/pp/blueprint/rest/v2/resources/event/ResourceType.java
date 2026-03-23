package org.opendatamesh.platform.pp.blueprint.rest.v2.resources.event;

import java.util.Locale;

public enum ResourceType {
    BLUEPRINT;

    public static ResourceType fromString(String value) {
        return ResourceType.valueOf(value.toUpperCase(Locale.ROOT));
    }
}

