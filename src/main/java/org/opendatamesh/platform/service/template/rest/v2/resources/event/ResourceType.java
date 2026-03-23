package org.opendatamesh.platform.service.template.rest.v2.resources.event;

import java.util.Locale;

public enum ResourceType {
    SERVICE_TEMPLATE;

    public static ResourceType fromString(String value) {
        return ResourceType.valueOf(value.toUpperCase(Locale.ROOT));
    }
}

