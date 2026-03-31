package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.publish;

import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;

interface PublishBlueprintVersionPersistenceOutboundPort {

    BlueprintVersion createBlueprintVersion(BlueprintVersion blueprintVersion);
}
