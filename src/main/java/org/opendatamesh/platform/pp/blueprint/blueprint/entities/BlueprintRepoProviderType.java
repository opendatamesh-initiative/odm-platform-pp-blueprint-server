package org.opendatamesh.platform.pp.blueprint.blueprint.entities;

import java.util.Locale;

/**
 * Enum defining supported Git provider types
 */
public enum BlueprintRepoProviderType {
    AZURE,
    BITBUCKET,
    GITHUB,
    GITLAB;

    public static BlueprintRepoProviderType fromString(String value) {
        return BlueprintRepoProviderType.valueOf(value.toUpperCase(Locale.ROOT));
    }
}
