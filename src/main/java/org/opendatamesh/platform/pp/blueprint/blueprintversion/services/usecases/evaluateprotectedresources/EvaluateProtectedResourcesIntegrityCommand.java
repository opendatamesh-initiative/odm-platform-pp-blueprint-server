package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public record EvaluateProtectedResourcesIntegrityCommand(
        String publicationTag,
        ProductRepoLocator productRepo,
        String blueprintName,
        String blueprintVersionNumber,
        Map<String, JsonNode> lineageParameters
) {
}
