package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.publish;

import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.core.BlueprintVersionCrudService;

class PublishBlueprintVersionPersistenceOutboundPortImpl implements PublishBlueprintVersionPersistenceOutboundPort {

    private final BlueprintVersionCrudService blueprintVersionCrudService;

    PublishBlueprintVersionPersistenceOutboundPortImpl(BlueprintVersionCrudService blueprintVersionCrudService) {
        this.blueprintVersionCrudService = blueprintVersionCrudService;
    }

    @Override
    public BlueprintVersion createBlueprintVersion(BlueprintVersion blueprintVersion) {
        return blueprintVersionCrudService.create(blueprintVersion);
    }
}
