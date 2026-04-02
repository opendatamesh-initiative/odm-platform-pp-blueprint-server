package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.publish;

import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.utils.usecases.TransactionalOutboundPort;
import org.opendatamesh.platform.pp.blueprint.utils.usecases.UseCase;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint;
import org.opendatamesh.platform.pp.blueprint.exceptions.ResourceConflictException;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersionShort;
import java.util.Optional;
import com.fasterxml.jackson.databind.JsonNode;

class PublishBlueprintVersion implements UseCase {

    private final PublishBlueprintVersionCommand command;
    private final PublishBlueprintVersionPresenter presenter;
    private final PublishBlueprintVersionManifestOutboundPort manifestOutboundPort;
    private final PublishBlueprintVersionPersistenceOutboundPort blueprintVersionPersistencePort;
    private final PublishBlueprintPersistenceOutboundPort blueprintPersistenceOutboundPort;
    private final TransactionalOutboundPort transactionalOutboundPort;
    PublishBlueprintVersion(
            PublishBlueprintVersionCommand command,
            PublishBlueprintVersionPresenter presenter,
            PublishBlueprintVersionManifestOutboundPort manifestOutboundPort,
            PublishBlueprintVersionPersistenceOutboundPort blueprintVersionPersistencePort,
            PublishBlueprintPersistenceOutboundPort blueprintPersistenceOutboundPort,
            TransactionalOutboundPort transactionalOutboundPort
    ) {
        this.command = command;
        this.presenter = presenter;
        this.manifestOutboundPort = manifestOutboundPort;
        this.blueprintVersionPersistencePort = blueprintVersionPersistencePort;
        this.blueprintPersistenceOutboundPort = blueprintPersistenceOutboundPort;
        this.transactionalOutboundPort = transactionalOutboundPort;
    }

    @Override
    public void execute() {
        BlueprintVersion blueprintVersion = command.blueprintVersion();
        blueprintVersion.setUuid(null);

        transactionalOutboundPort.doInTransaction(() -> {

            Blueprint blueprint = blueprintPersistenceOutboundPort.findByUuidOrName(blueprintVersion.getBlueprintUuid(), blueprintVersion.getBlueprint().getName());
            blueprintVersion.setBlueprint(blueprint);

            JsonNode filled = manifestOutboundPort.autofillManifest(blueprintVersion.getSpec(), blueprintVersion.getSpecVersion(), blueprintVersion.getContent(), blueprint.getName());
            blueprintVersion.setContent(filled);
            manifestOutboundPort.validateManifest(blueprintVersion.getSpec(), blueprintVersion.getSpecVersion(), blueprintVersion.getContent());
            String versionNumber = manifestOutboundPort.extractVersionNumber(blueprintVersion.getContent());
            String specNumber = manifestOutboundPort.extractSpecNumber(blueprintVersion.getContent());
            String specVersion = manifestOutboundPort.extractSpecVersion(blueprintVersion.getContent());

            blueprintVersion.setVersionNumber(versionNumber);
            blueprintVersion.setSpec(specNumber);
            blueprintVersion.setSpecVersion(specVersion);

            Optional<BlueprintVersionShort> existentBlueprintVersion = blueprintVersionPersistencePort.findByBlueprintUuidAndVersionNumber(blueprintVersion.getBlueprintUuid(), blueprintVersion.getVersionNumber());
            if (existentBlueprintVersion.isPresent()) {
                throw new ResourceConflictException("Impossible to publish a Blueprint version already existent");
            }
            BlueprintVersion created = blueprintVersionPersistencePort.createBlueprintVersion(blueprintVersion);
            presenter.presentPublished(created);
        });
    }
}
