package org.opendatamesh.platform.pp.blueprint.blueprint.services.usecases.updatedocumentationfields;

import org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint;
import org.opendatamesh.platform.pp.blueprint.exceptions.NotFoundException;
import org.opendatamesh.platform.pp.blueprint.utils.usecases.TransactionalOutboundPort;
import org.opendatamesh.platform.pp.blueprint.utils.usecases.UseCase;

class UpdateBlueprintDocumentationFields implements UseCase {

    private final UpdateBlueprintDocumentationFieldsCommand command;
    private final UpdateBlueprintDocumentationFieldsPresenter presenter;
    private final UpdateBlueprintDocumentationFieldsStructuralValidationOutboundPort structuralValidationOutboundPort;
    private final UpdateBlueprintDocumentationFieldsSemanticValidationOutboundPort semanticValidationOutboundPort;
    private final UpdateBlueprintDocumentationFieldsPersistenceOutboundPort persistenceOutboundPort;
    private final TransactionalOutboundPort transactionalOutboundPort;

    UpdateBlueprintDocumentationFields(
            UpdateBlueprintDocumentationFieldsCommand command,
            UpdateBlueprintDocumentationFieldsPresenter presenter,
            UpdateBlueprintDocumentationFieldsStructuralValidationOutboundPort structuralValidationOutboundPort,
            UpdateBlueprintDocumentationFieldsSemanticValidationOutboundPort semanticValidationOutboundPort,
            UpdateBlueprintDocumentationFieldsPersistenceOutboundPort persistenceOutboundPort,
            TransactionalOutboundPort transactionalOutboundPort
    ) {
        this.command = command;
        this.presenter = presenter;
        this.structuralValidationOutboundPort = structuralValidationOutboundPort;
        this.semanticValidationOutboundPort = semanticValidationOutboundPort;
        this.persistenceOutboundPort = persistenceOutboundPort;
        this.transactionalOutboundPort = transactionalOutboundPort;
    }

    @Override
    public void execute() {
        transactionalOutboundPort.doInTransaction(() -> {
            Blueprint blueprint = persistenceOutboundPort.findByUuid(command.blueprintUuid());
            
            blueprint.setDisplayName(command.displayName());
            blueprint.setDescription(command.description());
            
            structuralValidationOutboundPort.validate(blueprint);
            semanticValidationOutboundPort.validate(blueprint);

            // replace the blueprint repo with the new one. If null preserve the existing one.
            if (command.blueprintRepo() != null) {
                command.blueprintRepo().setBlueprint(blueprint);
                blueprint.setBlueprintRepo(command.blueprintRepo());
            }

            Blueprint blueprintUpdated = persistenceOutboundPort.update(blueprint);
            presenter.presentUpdated(blueprintUpdated);
        });
    }
}
