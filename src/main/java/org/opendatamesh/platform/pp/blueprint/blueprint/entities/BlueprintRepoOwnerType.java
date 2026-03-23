package org.opendatamesh.platform.pp.blueprint.blueprint.entities;

import java.util.Locale;

/**
 * Enum defining supported repository owner types
 */
public enum BlueprintRepoOwnerType {
    ORGANIZATION,
    ACCOUNT;

    public static BlueprintRepoOwnerType fromString(String value) {
        return BlueprintRepoOwnerType.valueOf(value.toUpperCase(Locale.ROOT));
    }
}

