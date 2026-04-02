package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.publish;

import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersionShort;
import java.util.Optional;

interface PublishBlueprintVersionPersistenceOutboundPort {

    BlueprintVersion createBlueprintVersion(BlueprintVersion blueprintVersion);

    Optional<BlueprintVersionShort> findByBlueprintUuidAndVersionNumber(String blueprintUuid, String versionNumber);
}
