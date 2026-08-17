package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.updatedataproduct;

import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;

interface UpdateDataProductPersistencyOutboundPort {

    BlueprintVersion findByBlueprintNameAndVersion(String blueprintName, String blueprintVersion);
}
