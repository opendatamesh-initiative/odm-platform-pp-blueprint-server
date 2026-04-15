package org.opendatamesh.platform.pp.blueprint.blueprint.services.usecases.updatedocumentationfields;

import org.opendatamesh.platform.pp.blueprint.blueprint.services.core.BlueprintService;
import org.opendatamesh.platform.pp.blueprint.utils.usecases.TransactionalOutboundPort;
import org.opendatamesh.platform.pp.blueprint.utils.usecases.UseCase;
import org.springframework.stereotype.Component;

@Component
public class UpdateBlueprintDocumentationFieldsFactory {

    private final BlueprintService blueprintService;
    private final TransactionalOutboundPort transactionalOutboundPort;

    public UpdateBlueprintDocumentationFieldsFactory(
            BlueprintService blueprintService,
            TransactionalOutboundPort transactionalOutboundPort
    ) {
        this.blueprintService = blueprintService;
        this.transactionalOutboundPort = transactionalOutboundPort;
    }

    public UseCase buildUpdateBlueprintDocumentationFields(
            UpdateBlueprintDocumentationFieldsCommand command,
            UpdateBlueprintDocumentationFieldsPresenter presenter
    ) {
        UpdateBlueprintDocumentationFieldsStructuralValidationOutboundPort structural =
                new UpdateBlueprintDocumentationFieldsStructuralValidationOutboundPortImpl();
        UpdateBlueprintDocumentationFieldsSemanticValidationOutboundPort semantic =
                new UpdateBlueprintDocumentationFieldsSemanticValidationOutboundPortImpl();
        UpdateBlueprintDocumentationFieldsPersistenceOutboundPort persistence =
                new UpdateBlueprintDocumentationFieldsPersistenceOutboundPortImpl(blueprintService);
        return new UpdateBlueprintDocumentationFields(
                command,
                presenter,
                structural,
                semantic,
                persistence,
                transactionalOutboundPort
        );
    }
}
