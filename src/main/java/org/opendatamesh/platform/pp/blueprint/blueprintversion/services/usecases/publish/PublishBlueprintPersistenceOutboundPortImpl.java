package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.publish;

import org.opendatamesh.platform.pp.blueprint.blueprint.services.core.BlueprintService;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint;
import org.springframework.util.StringUtils;

public class PublishBlueprintPersistenceOutboundPortImpl implements PublishBlueprintPersistenceOutboundPort {

    private final BlueprintService blueprintService;

    public PublishBlueprintPersistenceOutboundPortImpl(BlueprintService blueprintService) {
        this.blueprintService = blueprintService;
    }

    @Override
    public Blueprint findByUuidOrName(String uuid, String name) {
        if (StringUtils.hasText(uuid)) {
            return blueprintService.findOne(uuid);
        } else {
            return blueprintService.findOne(name);
        }
    }
}

