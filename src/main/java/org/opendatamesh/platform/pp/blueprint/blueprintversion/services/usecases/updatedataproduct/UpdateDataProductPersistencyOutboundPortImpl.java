package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.updatedataproduct;

import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.core.BlueprintVersionCrudService;
import org.opendatamesh.platform.pp.blueprint.exceptions.NotFoundException;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.BlueprintVersionSearchOptions;
import org.springframework.data.domain.Pageable;

class UpdateDataProductPersistencyOutboundPortImpl implements UpdateDataProductPersistencyOutboundPort {

    private final BlueprintVersionCrudService blueprintVersionCrudService;

    UpdateDataProductPersistencyOutboundPortImpl(
            BlueprintVersionCrudService blueprintVersionCrudService
    ) {
        this.blueprintVersionCrudService = blueprintVersionCrudService;
    }

    @Override
    public BlueprintVersion findByBlueprintNameAndVersion(String blueprintName, String blueprintVersion) {
        BlueprintVersionSearchOptions searchOptions = new BlueprintVersionSearchOptions();
        searchOptions.setBlueprintName(blueprintName);
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
