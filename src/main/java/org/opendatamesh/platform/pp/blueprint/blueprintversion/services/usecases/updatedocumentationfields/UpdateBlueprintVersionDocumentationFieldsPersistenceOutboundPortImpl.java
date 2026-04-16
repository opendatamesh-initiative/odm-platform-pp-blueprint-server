package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.updatedocumentationfields;

import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.core.BlueprintVersionCrudService;

class UpdateBlueprintVersionDocumentationFieldsPersistenceOutboundPortImpl implements UpdateBlueprintVersionDocumentationFieldsPersistenceOutboundPort {

    private final BlueprintVersionCrudService blueprintVersionCrudService;

    UpdateBlueprintVersionDocumentationFieldsPersistenceOutboundPortImpl(
            BlueprintVersionCrudService blueprintVersionCrudService
    ) {
        this.blueprintVersionCrudService = blueprintVersionCrudService;
    }

    @Override
    public BlueprintVersion update(BlueprintVersion blueprintVersion) {
        blueprintVersionCrudService.overwrite(blueprintVersion.getUuid(), blueprintVersion);
        return blueprintVersionCrudService.findOne(blueprintVersion.getUuid());
    }

    @Override
    public BlueprintVersion findByUuid(String uuid) {
        BlueprintVersion blueprintVersion = blueprintVersionCrudService.findOne(uuid);
        return blueprintVersion;
    }
}
