package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.updatedataproduct;

import com.fasterxml.jackson.databind.JsonNode;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate.SourceRepositoryDto;

import java.util.List;
import java.util.Map;

interface UpdateDataProductManifestOutboundPort {

    /**
     * Collects structural, request, and structure-freeze validation issues for update.
     * Does not throw on the first error.
     */
    List<UpdateValidationIssue> collectValidationIssues(
            BlueprintVersion current,
            BlueprintVersion next,
            Map<String, JsonNode> parameters,
            List<UpdateDataProductTargetRepositoryDto> targetRepositories);

    List<UpdateRoute> flattenRoutes(JsonNode nextContent);

    String retrieveRootTargetRepositoryKey(JsonNode nextContent);

    Map<String, JsonNode> enrichRequestParametersWithDefaultsIfNeeded(
            JsonNode nextContent,
            Map<String, JsonNode> requestParameters);

    List<UpdateCompositionIdentity> listCompositionIdentities(JsonNode nextContent);

    boolean isMonorepoNoComposition(JsonNode moduleContent);

    List<SourceRepositoryDto> retrieveAllSourceRepositories(
            BlueprintVersion nextParent,
            JsonNode nextContent,
            Map<String, BlueprintVersion> modulesByAlias);

    List<UpdateValidationIssue> collectProviderMismatchIssues(
            BlueprintVersion nextParent,
            Map<String, BlueprintVersion> modulesByAlias);

    List<UpdateValidationIssue> collectModuleParameterResolutionIssues(
            JsonNode nextContent,
            Map<String, JsonNode> nextParentResolvedParameters);

    Map<String, Map<String, JsonNode>> resolveModuleParameters(
            JsonNode nextContent,
            Map<String, JsonNode> nextParentResolvedParameters);
}
