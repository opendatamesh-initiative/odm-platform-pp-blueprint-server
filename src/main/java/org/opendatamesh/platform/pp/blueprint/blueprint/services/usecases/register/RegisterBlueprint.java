package org.opendatamesh.platform.pp.blueprint.blueprint.services.usecases.register;

import org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint;
import org.opendatamesh.platform.pp.blueprint.utils.usecases.TransactionalOutboundPort;
import org.opendatamesh.platform.pp.blueprint.utils.usecases.UseCase;

class RegisterBlueprint implements UseCase {

    private final RegisterBlueprintCommand command;
    private final RegisterBlueprintPresenter presenter;
    private final RegisterBlueprintSemanticValidationOutboundPort semanticValidationOutboundPort;
    private final RegisterBlueprintPersistenceOutboundPort persistenceOutboundPort;
    private final TransactionalOutboundPort transactionalOutboundPort;

    RegisterBlueprint(
            RegisterBlueprintCommand command,
            RegisterBlueprintPresenter presenter,
            RegisterBlueprintSemanticValidationOutboundPort semanticValidationOutboundPort,
            RegisterBlueprintPersistenceOutboundPort persistenceOutboundPort,
            TransactionalOutboundPort transactionalOutboundPort
    ) {
        this.command = command;
        this.presenter = presenter;
        this.semanticValidationOutboundPort = semanticValidationOutboundPort;
        this.persistenceOutboundPort = persistenceOutboundPort;
        this.transactionalOutboundPort = transactionalOutboundPort;
    }

    @Override
    public void execute() {
        Blueprint payload = command.blueprint();
        if (payload != null) {
            payload.setUuid(null);
        }
        transactionalOutboundPort.doInTransaction(() -> {
            semanticValidationOutboundPort.validate(payload);
            Blueprint created = persistenceOutboundPort.createBlueprint(payload);
            presenter.presentRegistered(created);
        });
    }
}
