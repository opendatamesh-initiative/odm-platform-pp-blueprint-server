package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

public record InstantiateBlueprintVersionCommand(
        String blueprintName,
        String blueprintVersion,
        List<TargetRepositoryDto> targetRepositories,
        Map<String, JsonNode> blueprintParameters,
        Map<String, String> authHeaders,
        String commitAuthorName,
        String commitAuthorEmail
) {

}
