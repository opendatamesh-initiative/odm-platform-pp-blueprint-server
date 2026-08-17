package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * Domain input for the instantiate-blueprint-version use case (mapped from the REST command).
 * Identifies the blueprint version to apply, target repositories, render parameters,
 * and optional commit author identity.
 */
public record InstantiateBlueprintVersionCommand(
        String blueprintName,
        String blueprintVersion,
        List<TargetRepositoryDto> targetRepositories,
        Map<String, JsonNode> blueprintParameters,
        String commitAuthorName,
        String commitAuthorEmail
) {

}
