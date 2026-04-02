package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.publish;

import org.opendatamesh.platform.pp.blueprint.blueprint.services.core.BlueprintService;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintSearchOptions;
import org.springframework.data.domain.Pageable;
import org.opendatamesh.platform.pp.blueprint.exceptions.NotFoundException;

public class PublishBlueprintPersistenceOutboundPortImpl implements PublishBlueprintPersistenceOutboundPort {

    private final BlueprintService blueprintService;

    public PublishBlueprintPersistenceOutboundPortImpl(BlueprintService blueprintService) {
        this.blueprintService = blueprintService;
    }

    @Override
    public Blueprint findByUuidOrName(String uuid, String name) {
        BlueprintSearchOptions filter = new BlueprintSearchOptions();
        filter.setName(name);
        filter.setUuid(uuid);
        Blueprint blueprint = blueprintService.findAllFiltered(Pageable.ofSize(1), filter).stream().findFirst().orElse(null);
        if (blueprint == null) {
            throw new NotFoundException("Blueprint not found");
        }
        return blueprint;
    }
}

