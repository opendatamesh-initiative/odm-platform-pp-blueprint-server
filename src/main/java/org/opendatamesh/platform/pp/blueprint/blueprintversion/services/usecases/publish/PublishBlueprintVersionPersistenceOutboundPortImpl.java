package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.publish;

import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.core.BlueprintVersionCrudService;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.core.BlueprintVersionQueryService;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersionShort;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.BlueprintVersionSearchOptions;
import org.springframework.data.domain.Pageable;
import java.util.Optional;

class PublishBlueprintVersionPersistenceOutboundPortImpl implements PublishBlueprintVersionPersistenceOutboundPort {

    private final BlueprintVersionQueryService blueprintVersionQueryService;
    private final BlueprintVersionCrudService blueprintVersionCrudService;

    PublishBlueprintVersionPersistenceOutboundPortImpl(BlueprintVersionQueryService blueprintVersionQueryService, BlueprintVersionCrudService blueprintVersionCrudService) {
        this.blueprintVersionQueryService = blueprintVersionQueryService;
        this.blueprintVersionCrudService = blueprintVersionCrudService;
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
}
