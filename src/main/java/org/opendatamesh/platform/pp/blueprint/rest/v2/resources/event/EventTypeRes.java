package org.opendatamesh.platform.pp.blueprint.rest.v2.resources.event;

import java.util.Locale;

public enum EventTypeRes {
    // --- EMITTED EVENT TYPES ---
    BLUEPRINT_CREATED,

    // --- RECEIVED EVENT TYPES ---
    BLUEPRINT_CREATED_APPROVED;

    public static EventTypeRes fromString(String value) {
        return EventTypeRes.valueOf(value.toUpperCase(Locale.ROOT));
    }
}
