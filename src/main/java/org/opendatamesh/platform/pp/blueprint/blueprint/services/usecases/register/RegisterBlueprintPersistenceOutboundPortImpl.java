package org.opendatamesh.platform.pp.blueprint.blueprint.services.usecases.register;

import org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint;
import org.opendatamesh.platform.pp.blueprint.blueprint.services.core.BlueprintService;

class RegisterBlueprintPersistenceOutboundPortImpl implements RegisterBlueprintPersistenceOutboundPort {

    private final BlueprintService blueprintService;

    RegisterBlueprintPersistenceOutboundPortImpl(BlueprintService blueprintService) {
        this.blueprintService = blueprintService;
    }

    @Override
    public Blueprint createBlueprint(Blueprint blueprint) {
        return blueprintService.create(blueprint);
    }
}
