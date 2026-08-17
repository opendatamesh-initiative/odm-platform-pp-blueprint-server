package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.updatedataproduct;

import com.fasterxml.jackson.databind.JsonNode;
import org.opendatamesh.platform.git.model.Repository;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate.SourceRepositoryDto;

import java.util.List;
import java.util.Map;

interface UpdateDataProductManifestOutboundPort {

    void validateManifestAndParameters(String spec, String specVersion, JsonNode manifest, Map<String, JsonNode> parameters);

    void validateTargetRepositories(BlueprintVersion blueprintVersion, List<UpdateDataProductTargetRepositoryDto> targetRepositories);

    Repository resolveSourceRepository(BlueprintVersion blueprintVersion);
}
