package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.publish;

import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.core.BlueprintVersionCrudService;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.manifestvalidator.OdmBlueprintValidatorFactory;
import org.opendatamesh.platform.pp.blueprint.utils.usecases.TransactionalOutboundPort;
import org.opendatamesh.platform.pp.blueprint.utils.usecases.UseCase;
import org.opendatamesh.platform.pp.blueprint.blueprint.services.core.BlueprintService;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.core.BlueprintVersionQueryService;
import org.springframework.stereotype.Component;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.manifestautofiller.OdmBlueprintManifestAutoFillerFactory;
@Component
public class PublishBlueprintVersionFactory {

    private final TransactionalOutboundPort transactionalOutboundPort;
    private final BlueprintVersionCrudService blueprintVersionCrudService;
    private final OdmBlueprintValidatorFactory manifestValidatorFactory;
    private final BlueprintService blueprintService;
    private final BlueprintVersionQueryService blueprintVersionQueryService;
    private final OdmBlueprintManifestAutoFillerFactory manifestAutoFillerFactory;

    public PublishBlueprintVersionFactory(
            TransactionalOutboundPort transactionalOutboundPort,
            BlueprintVersionCrudService blueprintVersionCrudService,
            OdmBlueprintValidatorFactory manifestValidatorFactory,
            BlueprintService blueprintService,
            BlueprintVersionQueryService blueprintVersionQueryService,
            OdmBlueprintManifestAutoFillerFactory manifestAutoFillerFactory

    ) {
        this.transactionalOutboundPort = transactionalOutboundPort;
        this.blueprintVersionCrudService = blueprintVersionCrudService;
        this.manifestValidatorFactory = manifestValidatorFactory;
        this.blueprintService = blueprintService;
        this.blueprintVersionQueryService = blueprintVersionQueryService;
        this.manifestAutoFillerFactory = manifestAutoFillerFactory;
    }

    public UseCase buildPublishBlueprintVersion(
            PublishBlueprintVersionCommand command,
            PublishBlueprintVersionPresenter presenter
    ) {
        PublishBlueprintVersionManifestOutboundPort manifestPort =
                new PublishBlueprintVersionManifestOutboundPortImpl(manifestValidatorFactory, manifestAutoFillerFactory);
        PublishBlueprintVersionPersistenceOutboundPort blueprintVersionPersistencePort =
                new PublishBlueprintVersionPersistenceOutboundPortImpl(blueprintVersionQueryService, blueprintVersionCrudService);
        PublishBlueprintPersistenceOutboundPort blueprintPersistencePort =
                new PublishBlueprintPersistenceOutboundPortImpl(blueprintService);
        return new PublishBlueprintVersion(
                command,
                presenter,
                manifestPort,
                blueprintVersionPersistencePort,
                blueprintPersistencePort,
                transactionalOutboundPort
        );
    }
}
