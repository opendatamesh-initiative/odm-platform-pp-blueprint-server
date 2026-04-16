package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.updatedocumentationfields;

import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.utils.usecases.TransactionalOutboundPort;
import org.opendatamesh.platform.pp.blueprint.utils.usecases.UseCase;

class UpdateBlueprintVersionDocumentationFields implements UseCase {

    private final UpdateBlueprintVersionDocumentationFieldsCommand command;
    private final UpdateBlueprintVersionDocumentationFieldsPresenter presenter;
    private final UpdateBlueprintVersionDocumentationFieldsValidationOutboundPort validationOutboundPort;
    private final UpdateBlueprintVersionDocumentationFieldsPersistenceOutboundPort persistenceOutboundPort;
    private final TransactionalOutboundPort transactionalOutboundPort;

    UpdateBlueprintVersionDocumentationFields(
            UpdateBlueprintVersionDocumentationFieldsCommand command,
            UpdateBlueprintVersionDocumentationFieldsPresenter presenter,
            UpdateBlueprintVersionDocumentationFieldsValidationOutboundPort validationOutboundPort,
            UpdateBlueprintVersionDocumentationFieldsPersistenceOutboundPort persistenceOutboundPort,
            TransactionalOutboundPort transactionalOutboundPort
    ) {
        this.command = command;
        this.presenter = presenter;
        this.validationOutboundPort = validationOutboundPort;
        this.persistenceOutboundPort = persistenceOutboundPort;
        this.transactionalOutboundPort = transactionalOutboundPort;
    }

    @Override
    public void execute() {
        transactionalOutboundPort.doInTransaction(() -> {
            BlueprintVersion blueprintVersion = persistenceOutboundPort.findByUuid(command.uuid());

            blueprintVersion.setName(command.name());
            blueprintVersion.setDescription(command.description());
            blueprintVersion.setUpdatedBy(command.updatedBy());

            validationOutboundPort.validate(command);

            BlueprintVersion updated = persistenceOutboundPort.update(blueprintVersion);
            presenter.presentUpdated(updated);
        });
    }
}
