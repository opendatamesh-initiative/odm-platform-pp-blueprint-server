package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.publish;

import org.opendatamesh.platform.pp.blueprint.blueprintversion.repositories.BlueprintVersionsRepository;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.core.BlueprintVersionCrudService;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.manifestvalidator.ManifestValidatorImpl;
import org.opendatamesh.platform.pp.blueprint.utils.usecases.TransactionalOutboundPort;
import org.opendatamesh.platform.pp.blueprint.utils.usecases.UseCase;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

@Component
public class PublishBlueprintVersionFactory {

    private final TransactionalOutboundPort transactionalOutboundPort;
    private final BlueprintVersionCrudService blueprintVersionCrudService;
    private final BlueprintVersionsRepository blueprintVersionsRepository;
    private final ObjectMapper objectMapper;

    public PublishBlueprintVersionFactory(
            TransactionalOutboundPort transactionalOutboundPort,
            BlueprintVersionCrudService blueprintVersionCrudService,
            BlueprintVersionsRepository blueprintVersionsRepository,
            ObjectMapper objectMapper
    ) {
        this.transactionalOutboundPort = transactionalOutboundPort;
        this.blueprintVersionCrudService = blueprintVersionCrudService;
        this.blueprintVersionsRepository = blueprintVersionsRepository;
        this.objectMapper = objectMapper;
    }

    public UseCase buildPublishBlueprintVersion(
            PublishBlueprintVersionCommand command,
            PublishBlueprintVersionPresenter presenter
    ) {
        ManifestValidatorImpl manifestValidator = new ManifestValidatorImpl(objectMapper);
        PublishBlueprintVersionManifestOutboundPort manifestPort =
                new PublishBlueprintVersionManifestOutboundPortImpl(manifestValidator);
        PublishBlueprintVersionSemanticOutboundPort semanticPort =
                new PublishBlueprintVersionSemanticOutboundPortImpl(blueprintVersionsRepository);
        PublishBlueprintVersionPersistenceOutboundPort persistencePort =
                new PublishBlueprintVersionPersistenceOutboundPortImpl(blueprintVersionCrudService);
        return new PublishBlueprintVersion(
                command,
                presenter,
                semanticPort,
                manifestPort,
                persistencePort,
                transactionalOutboundPort
        );
    }
}
