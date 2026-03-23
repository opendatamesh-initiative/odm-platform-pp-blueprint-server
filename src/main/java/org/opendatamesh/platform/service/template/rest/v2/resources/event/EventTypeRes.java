package org.opendatamesh.platform.service.template.rest.v2.resources.event;

import java.util.Locale;

public enum EventTypeRes {
    // --- EMITTED EVENT TYPES ---
    SERVICE_TEMPLATE_CREATED,

    // --- RECEIVED EVENT TYPES ---
    // Data Product Events
    SERVICE_TEMPLATE_CREATED_APPROVED;

    public static EventTypeRes fromString(String value) {
        return EventTypeRes.valueOf(value.toUpperCase(Locale.ROOT));
    }
}
