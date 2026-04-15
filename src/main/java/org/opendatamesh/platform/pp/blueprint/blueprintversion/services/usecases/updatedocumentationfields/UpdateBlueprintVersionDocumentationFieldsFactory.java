package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.updatedocumentationfields;

import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.core.BlueprintVersionCrudService;
import org.opendatamesh.platform.pp.blueprint.utils.usecases.TransactionalOutboundPort;
import org.opendatamesh.platform.pp.blueprint.utils.usecases.UseCase;
import org.springframework.stereotype.Component;

@Component
public class UpdateBlueprintVersionDocumentationFieldsFactory {

    private final BlueprintVersionCrudService blueprintVersionCrudService;
    private final TransactionalOutboundPort transactionalOutboundPort;

    public UpdateBlueprintVersionDocumentationFieldsFactory(
            BlueprintVersionCrudService blueprintVersionCrudService,
            TransactionalOutboundPort transactionalOutboundPort
    ) {
        this.blueprintVersionCrudService = blueprintVersionCrudService;
        this.transactionalOutboundPort = transactionalOutboundPort;
    }

    public UseCase buildUpdateBlueprintVersionDocumentationFields(
            UpdateBlueprintVersionDocumentationFieldsCommand command,
            UpdateBlueprintVersionDocumentationFieldsPresenter presenter
    ) {
        UpdateBlueprintVersionDocumentationFieldsValidationOutboundPort validation =
                new UpdateBlueprintVersionDocumentationFieldsValidationOutboundPortImpl();
        UpdateBlueprintVersionDocumentationFieldsPersistenceOutboundPort persistence =
                new UpdateBlueprintVersionDocumentationFieldsPersistenceOutboundPortImpl(blueprintVersionCrudService);
        return new UpdateBlueprintVersionDocumentationFields(
                command,
                presenter,
                validation,
                persistence,
                transactionalOutboundPort
            );
    }
}
