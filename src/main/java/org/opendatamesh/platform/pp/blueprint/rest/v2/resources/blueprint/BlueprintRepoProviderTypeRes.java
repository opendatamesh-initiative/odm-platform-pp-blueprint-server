package org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint;

import java.util.Locale;

/**
 * Enum defining supported Git provider types
 */
public enum BlueprintRepoProviderTypeRes {
    AZURE,
    BITBUCKET,
    GITHUB,
    GITLAB;

    public static BlueprintRepoProviderTypeRes fromString(String value) {
        return BlueprintRepoProviderTypeRes.valueOf(value.toUpperCase(Locale.ROOT));
    }
}
