package org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint;

import java.util.Locale;

/**
 * REST twin of {@link org.opendatamesh.platform.pp.blueprint.blueprint.entities.BlueprintType}.
 * {@code BLUEPRINT} is the root Blueprint; {@code MODULE} is the Blueprint module (component).
 */
public enum BlueprintTypeRes {
    BLUEPRINT,
    MODULE;

    public static BlueprintTypeRes fromString(String value) {
        return BlueprintTypeRes.valueOf(value.toUpperCase(Locale.ROOT));
    }
}
