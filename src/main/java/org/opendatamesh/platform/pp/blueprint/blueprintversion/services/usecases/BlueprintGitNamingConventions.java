package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases;

import java.util.UUID;

/**
 * Domain naming and identity policy for Git operations on data-product repositories
 * (checkpoint tags, temporary branches, orphan init branches, default commit author).
 * Distinct from {@code BlueprintVersion.tag} (blueprint source release tag).
 */
public final class BlueprintGitNamingConventions {

    public static final String DEFAULT_COMMIT_AUTHOR_NAME = "odm-blueprint-server";
    public static final String DEFAULT_COMMIT_AUTHOR_EMAIL = "odm-blueprint-server@local";

    private BlueprintGitNamingConventions() {
    }

    public static String checkpointTag(String versionNumber) {
        return "blueprint-v" + versionNumber;
    }

    public static String updateBranchName(String versionNumber) {
        return "update/blueprint-v" + versionNumber;
    }

    public static String orphanInitBranchName() {
        return "odm-init/" + UUID.randomUUID();
    }
}
