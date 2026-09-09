package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate;

import com.fasterxml.jackson.databind.JsonNode;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;

import java.util.List;
import java.util.Map;

interface InstantiateBlueprintVersionManifestOutboundPort {

    /**
     * Collects structural and request validation issues for instantiate. Does not throw on the first error.
     */
    List<InstantiationValidationIssue> collectValidationIssues(
            String spec,
            String specVersion,
            JsonNode content,
            Map<String, JsonNode> parameters,
            List<TargetRepositoryDto> targetRepositories);

    /**
     * Flattens parent {@code root.targets} and {@code composition[].targets} into routes.
     */
    List<InstantiationRoute> flattenRoutes(JsonNode content);

    /**
     * The target root repository is the one containing the data product descriptor and the .odm folder
     */
    String retrieveRootTargetRepositoryKey(JsonNode content);

    /**
     * Merges request values with root parameter defaults for lineage and {@code $param} resolution.
     */
    Map<String, JsonNode> enrichRequestParametersWithDefaultsIfNeeded(JsonNode content, Map<String, JsonNode> requestParameters);

    /**
     * Collects unresolved {@code $param} references after request values and parent
     * defaults have been merged.
     */
    List<InstantiationValidationIssue> collectModuleParameterResolutionIssues(
            JsonNode content,
            Map<String, JsonNode> parentResolvedParameters);

    /**
     * Builds one module-local render context per composition alias from
     * {@code parameterMapping}.
     */
    Map<String, Map<String, JsonNode>> resolveModuleParameters(
            JsonNode content,
            Map<String, JsonNode> parentResolvedParameters);

    /**
     * Lists composition module identities so the use case can look up published versions.
     */
    List<InstantiationCompositionIdentity> listCompositionIdentities(JsonNode content);

    /**
     * Whether stored child content is a monorepo with no composition.
     */
    boolean isMonorepoNoComposition(JsonNode content);


    /**
     * Builds source repository descriptors for the parent and published composition modules.
     * Validates that each module's Git provider type and base URL match the parent; mismatches
     * are returned as validation issues via {@code providerMismatchIssues} (caller-owned list).
     */
    List<SourceRepositoryDto> retrieveAllSourceRepositories(
            BlueprintVersion parentVersion,
            JsonNode manifest,
            Map<String, BlueprintVersion> modulesByAlias);
}
