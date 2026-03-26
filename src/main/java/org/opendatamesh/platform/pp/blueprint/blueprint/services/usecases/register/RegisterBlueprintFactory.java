package org.opendatamesh.platform.pp.blueprint.blueprint.services.usecases.register;

import org.opendatamesh.platform.pp.blueprint.blueprint.services.core.BlueprintService;
import org.opendatamesh.platform.pp.blueprint.utils.usecases.TransactionalOutboundPort;
import org.opendatamesh.platform.pp.blueprint.utils.usecases.UseCase;
import org.springframework.stereotype.Component;

@Component
public class RegisterBlueprintFactory {

    private final BlueprintService blueprintService;
    private final TransactionalOutboundPort transactionalOutboundPort;

    public RegisterBlueprintFactory(
            BlueprintService blueprintService,
            TransactionalOutboundPort transactionalOutboundPort
    ) {
        this.blueprintService = blueprintService;
        this.transactionalOutboundPort = transactionalOutboundPort;
    }

    public UseCase buildRegisterBlueprint(RegisterBlueprintCommand command, RegisterBlueprintPresenter presenter) {
        RegisterBlueprintSemanticValidationOutboundPort semantic =
                new RegisterBlueprintSemanticValidationOutboundPortImpl();
        RegisterBlueprintPersistenceOutboundPort persistence =
                new RegisterBlueprintPersistenceOutboundPortImpl(blueprintService);
        return new RegisterBlueprint(
                command,
                presenter,
                semantic,
                persistence,
                transactionalOutboundPort
        );
    }
}
