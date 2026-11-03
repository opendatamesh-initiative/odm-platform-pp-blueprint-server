package org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation;

import org.opendatamesh.platform.pp.blueprint.manifest.model.core.ManifestComponentBase;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestTargetRepositoryVisitor;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Naming convention for the physical repository bound to a {@code targetRepositories[]} key.
 * The server only validates the declaration; clients resolve and enforce it at instantiation.
 */
public class ManifestTargetRepositoryNaming extends ManifestComponentBase {

    public static final String ENFORCEMENT_BINDING = "binding";
    public static final String ENFORCEMENT_SUGGESTED = "suggested";

    /**
     * Reserved data product parameters a pattern may reference.
     */
    public static final Set<String> ALLOWED_VARIABLES = Collections.unmodifiableSet(new LinkedHashSet<>(List.of(
            "dpDomain", "dpName", "dpFqn", "dpDisplayName", "dpDescription", "dpOwnerId", "dpOwnerName")));

    private String pattern;
    /**
     * {@code binding} or {@code suggested}; clients treat an omitted value as {@code binding}.
     */
    private String enforcement;

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public String getEnforcement() {
        return enforcement;
    }

    public void setEnforcement(String enforcement) {
        this.enforcement = enforcement;
    }

    public void accept(ManifestTargetRepositoryVisitor visitor) {
        visitor.visit(this);
    }
}
