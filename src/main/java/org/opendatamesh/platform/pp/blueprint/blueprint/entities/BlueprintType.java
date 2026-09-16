package org.opendatamesh.platform.pp.blueprint.blueprint.entities;

import java.util.Locale;

/**
 * Catalog discriminant: Blueprint (root) vs Blueprint module (component).
 */
public enum BlueprintType {
    BLUEPRINT,
    MODULE;

    public static BlueprintType fromString(String value) {
        return BlueprintType.valueOf(value.toUpperCase(Locale.ROOT));
    }
}
