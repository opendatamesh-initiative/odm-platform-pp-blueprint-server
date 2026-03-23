package org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint;

import java.util.Locale;

/**
 * Enum defining supported repository owner types
 */
public enum BlueprintRepoOwnerTypeRes {
    ORGANIZATION,
    ACCOUNT;

    public static BlueprintRepoOwnerTypeRes fromString(String value) {
        return BlueprintRepoOwnerTypeRes.valueOf(value.toUpperCase(Locale.ROOT));
    }
}

