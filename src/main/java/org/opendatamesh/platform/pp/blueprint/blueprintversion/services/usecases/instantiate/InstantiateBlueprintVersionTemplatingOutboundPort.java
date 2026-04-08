package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate;

import java.nio.file.Path;
import java.util.Map;

import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;

import com.fasterxml.jackson.databind.JsonNode;

interface InstantiateBlueprintVersionTemplatingOutboundPort {

    void renderAndCopy(
            BlueprintVersion blueprintVersion,
            Map<String, JsonNode> parameters,
            Map<SourceRepositoryDto, Path> sourceRepositories,
            Map<TargetRepositoryDto, Path> targetRepositories);
}
