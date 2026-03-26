package org.opendatamesh.platform.pp.blueprint.blueprint.services.usecases.register;

import org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint;

interface RegisterBlueprintPersistenceOutboundPort {

    Blueprint createBlueprint(Blueprint blueprint);
}
