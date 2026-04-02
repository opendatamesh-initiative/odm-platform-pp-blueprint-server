package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate;

import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;

interface InstantiateBlueprintVersionPersistencyOutboundPort {

    BlueprintVersion findByBlueprintNameAndVersion(String blueprintName, String blueprintVersion);
}
