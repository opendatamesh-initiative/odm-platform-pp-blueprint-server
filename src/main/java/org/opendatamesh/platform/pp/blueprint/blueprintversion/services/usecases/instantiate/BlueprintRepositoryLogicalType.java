package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Logical role of a repository within blueprint instantiation (e.g. monorepo root).
 */
public enum BlueprintRepositoryLogicalType {

    @JsonProperty("root")
    ROOT
}
