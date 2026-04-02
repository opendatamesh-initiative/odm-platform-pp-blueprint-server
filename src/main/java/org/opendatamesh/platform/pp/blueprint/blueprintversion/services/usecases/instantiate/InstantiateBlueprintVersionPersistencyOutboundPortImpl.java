package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate;

import org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint;
import org.opendatamesh.platform.pp.blueprint.blueprint.services.core.BlueprintService;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.core.BlueprintVersionCrudService;
import org.opendatamesh.platform.pp.blueprint.exceptions.NotFoundException;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintSearchOptions;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.BlueprintVersionSearchOptions;
import org.springframework.data.domain.Pageable;

class InstantiateBlueprintVersionPersistencyOutboundPortImpl implements InstantiateBlueprintVersionPersistencyOutboundPort {

    private final BlueprintService blueprintService;
    private final BlueprintVersionCrudService blueprintVersionCrudService;

    InstantiateBlueprintVersionPersistencyOutboundPortImpl(
            BlueprintService blueprintService,
            BlueprintVersionCrudService blueprintVersionCrudService
    ) {
        this.blueprintService = blueprintService;
        this.blueprintVersionCrudService = blueprintVersionCrudService;
    }

    @Override
    public BlueprintVersion findByBlueprintNameAndVersion(String blueprintName, String blueprintVersion) {
        BlueprintSearchOptions blueprintSearchOptions = new BlueprintSearchOptions();
        blueprintSearchOptions.setName(blueprintName);
        Blueprint blueprint = blueprintService.findAllFiltered(Pageable.unpaged(), blueprintSearchOptions)
                .stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "Blueprint with name '%s' not found".formatted(blueprintName))
                );

        BlueprintVersionSearchOptions searchOptions = new BlueprintVersionSearchOptions();
        searchOptions.setBlueprintUuid(blueprint.getUuid());
        searchOptions.setVersionNumber(blueprintVersion);
        return blueprintVersionCrudService.findAllFiltered(Pageable.unpaged(), searchOptions)
                .stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "Blueprint version '%s' not found for blueprint '%s'"
                                .formatted(blueprintVersion, blueprintName)
                ));
    }
}
