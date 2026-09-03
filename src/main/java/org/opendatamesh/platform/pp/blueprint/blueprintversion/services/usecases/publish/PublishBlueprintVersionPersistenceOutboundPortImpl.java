package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.publish;

import org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint;
import org.opendatamesh.platform.pp.blueprint.blueprint.services.core.BlueprintService;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.core.BlueprintVersionCrudService;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.core.BlueprintVersionQueryService;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersionShort;
import org.opendatamesh.platform.pp.blueprint.exceptions.NotFoundException;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintSearchOptions;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.BlueprintVersionSearchOptions;
import org.springframework.data.domain.Pageable;
import java.util.Optional;

class PublishBlueprintVersionPersistenceOutboundPortImpl implements PublishBlueprintVersionPersistenceOutboundPort {

    private final BlueprintVersionQueryService blueprintVersionQueryService;
    private final BlueprintVersionCrudService blueprintVersionCrudService;
    private final BlueprintService blueprintService;

    PublishBlueprintVersionPersistenceOutboundPortImpl(
            BlueprintVersionQueryService blueprintVersionQueryService,
            BlueprintVersionCrudService blueprintVersionCrudService,
            BlueprintService blueprintService
    ) {
        this.blueprintVersionQueryService = blueprintVersionQueryService;
        this.blueprintVersionCrudService = blueprintVersionCrudService;
        this.blueprintService = blueprintService;
    }

    @Override
    public BlueprintVersion createBlueprintVersion(BlueprintVersion blueprintVersion) {
        return blueprintVersionCrudService.create(blueprintVersion);
    }

    @Override
    public Optional<BlueprintVersionShort> findByBlueprintUuidAndVersionNumber(String blueprintUuid, String versionNumber) {
        BlueprintVersionSearchOptions filter = new BlueprintVersionSearchOptions();
        filter.setBlueprintUuid(blueprintUuid);
        filter.setVersionNumber(versionNumber);
        return blueprintVersionQueryService.findAllShort(Pageable.ofSize(1), filter).stream().findFirst();
    }

    @Override
    public BlueprintVersion findModuleBlueprintVersion(String blueprintName, String blueprintVersion) {
        BlueprintSearchOptions blueprintFilter = new BlueprintSearchOptions();
        blueprintFilter.setName(blueprintName);
        Blueprint blueprint = blueprintService.findAllFiltered(Pageable.unpaged(), blueprintFilter)
                .stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "Blueprint with name '%s' not found".formatted(blueprintName)));

        BlueprintVersionSearchOptions versionFilter = new BlueprintVersionSearchOptions();
        versionFilter.setBlueprintName(blueprint.getName());
        versionFilter.setVersionNumber(blueprintVersion);
        return blueprintVersionCrudService.findAllFiltered(Pageable.unpaged(), versionFilter)
                .stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "Blueprint version '%s' not found for blueprint '%s'"
                                .formatted(blueprintVersion, blueprintName)));
    }
}
