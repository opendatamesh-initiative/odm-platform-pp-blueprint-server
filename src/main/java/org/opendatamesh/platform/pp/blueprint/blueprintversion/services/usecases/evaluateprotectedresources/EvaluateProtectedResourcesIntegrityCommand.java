package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

public record EvaluateProtectedResourcesIntegrityCommand(
        String rootPublicationRef,
        ProductRepoLocator rootProductRepo,
        List<KeyedProductRepoLocator> additionalProductRepos,
        List<KeyedProductRepoRef> additionalRefs,
        String blueprintName,
        String blueprintVersionNumber,
        Map<String, JsonNode> lineageParameters
) {
    public EvaluateProtectedResourcesIntegrityCommand {
        additionalProductRepos = additionalProductRepos == null
                ? List.of()
                : List.copyOf(additionalProductRepos);
        additionalRefs = additionalRefs == null
                ? List.of()
                : List.copyOf(additionalRefs);
        lineageParameters = lineageParameters == null
                ? Map.of()
                : Map.copyOf(lineageParameters);
    }
}
