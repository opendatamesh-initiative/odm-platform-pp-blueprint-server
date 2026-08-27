package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources;

import org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint;
import org.opendatamesh.platform.pp.blueprint.blueprint.services.core.BlueprintService;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.core.BlueprintVersionCrudService;
import org.opendatamesh.platform.pp.blueprint.exceptions.NotFoundException;
import org.opendatamesh.platform.pp.blueprint.manifest.model.Manifest;
import org.opendatamesh.platform.pp.blueprint.manifest.parser.ManifestParserFactory;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintSearchOptions;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.BlueprintVersionSearchOptions;
import org.springframework.data.domain.Pageable;

import java.io.IOException;

class EvaluateProtectedResourcesIntegrityPersistencyOutboundPortImpl
        implements EvaluateProtectedResourcesIntegrityPersistencyOutboundPort {

    private final BlueprintService blueprintService;
    private final BlueprintVersionCrudService blueprintVersionCrudService;

    EvaluateProtectedResourcesIntegrityPersistencyOutboundPortImpl(
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

    @Override
    public Manifest readManifest(BlueprintVersion blueprintVersion) {
        try {
            return ManifestParserFactory.getParser().deserialize(blueprintVersion.getContent());
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Cannot check protected resources: the blueprint manifest could not be read",
                    e);
        }
    }
}
