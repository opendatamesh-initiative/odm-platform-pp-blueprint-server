package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.publish;

import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.utils.usecases.TransactionalOutboundPort;
import org.opendatamesh.platform.pp.blueprint.utils.usecases.UseCase;

import com.fasterxml.jackson.databind.JsonNode;

class PublishBlueprintVersion implements UseCase {

    private final PublishBlueprintVersionCommand command;
    private final PublishBlueprintVersionPresenter presenter;
    private final PublishBlueprintVersionSemanticOutboundPort semanticOutboundPort;
    private final PublishBlueprintVersionManifestOutboundPort manifestOutboundPort;
    private final PublishBlueprintVersionPersistenceOutboundPort persistenceOutboundPort;
    private final TransactionalOutboundPort transactionalOutboundPort;

    PublishBlueprintVersion(
            PublishBlueprintVersionCommand command,
            PublishBlueprintVersionPresenter presenter,
            PublishBlueprintVersionSemanticOutboundPort semanticOutboundPort,
            PublishBlueprintVersionManifestOutboundPort manifestOutboundPort,
            PublishBlueprintVersionPersistenceOutboundPort persistenceOutboundPort,
            TransactionalOutboundPort transactionalOutboundPort
    ) {
        this.command = command;
        this.presenter = presenter;
        this.semanticOutboundPort = semanticOutboundPort;
        this.manifestOutboundPort = manifestOutboundPort;
        this.persistenceOutboundPort = persistenceOutboundPort;
        this.transactionalOutboundPort = transactionalOutboundPort;
    }

    @Override
    public void execute() {
        BlueprintVersion version = command.blueprintVersion();
        version.setUuid(null);

        transactionalOutboundPort.doInTransaction(() -> {
            semanticOutboundPort.verifySpecAndSpecVersion(version);
            JsonNode filled = manifestOutboundPort.autofillManifest(version.getContent(), version);
            version.setContent(filled);
            manifestOutboundPort.validateManifest(version.getContent());
            manifestOutboundPort.setVersionFieldsFromManifestContent(version);
            semanticOutboundPort.verifyNoDuplicateNameAndTag(version);
            BlueprintVersion created = persistenceOutboundPort.createBlueprintVersion(version);
            presenter.presentPublished(created);
        });
    }
}
