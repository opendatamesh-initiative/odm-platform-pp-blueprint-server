package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate;

import com.fasterxml.jackson.databind.JsonNode;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;

import java.util.List;
import java.util.Map;

interface InstantiateBlueprintVersionManifestOutboundPort {

    void validateManifestAndParameters(String spec, String specVersion, JsonNode manifest, Map<String, JsonNode> parameters);

    List<SourceRepositoryDto> retrieveAllSourceRepositories(BlueprintVersion blueprintVersion, JsonNode manifest);

    void validateTargetRepositories(BlueprintVersion blueprintVersion, List<TargetRepositoryDto> targetRepositories);
}
