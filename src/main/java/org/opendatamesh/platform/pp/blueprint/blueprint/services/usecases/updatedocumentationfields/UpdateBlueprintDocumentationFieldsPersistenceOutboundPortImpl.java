package org.opendatamesh.platform.pp.blueprint.blueprint.services.usecases.updatedocumentationfields;

import org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint;
import org.opendatamesh.platform.pp.blueprint.blueprint.services.core.BlueprintService;
import org.opendatamesh.platform.pp.blueprint.exceptions.NotFoundException;

class UpdateBlueprintDocumentationFieldsPersistenceOutboundPortImpl implements UpdateBlueprintDocumentationFieldsPersistenceOutboundPort {

    private final BlueprintService blueprintService;

    UpdateBlueprintDocumentationFieldsPersistenceOutboundPortImpl(BlueprintService blueprintService) {
        this.blueprintService = blueprintService;
    }

    @Override
    public Blueprint findByUuid(String uuid) {
        Blueprint blueprint = blueprintService.findOne(uuid);
        if (blueprint == null) {
            throw new NotFoundException("Blueprint with id=" + uuid + " not found");
        }
        return blueprint;
    }

    @Override
    public Blueprint update(Blueprint blueprint) {
        return blueprintService.overwrite(blueprint.getUuid(), blueprint);
    }
}
