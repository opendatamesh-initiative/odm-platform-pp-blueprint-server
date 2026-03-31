package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.publish;

import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;

interface PublishBlueprintVersionSemanticOutboundPort {

    void verifySpecAndSpecVersion(BlueprintVersion blueprintVersion);

    void verifyNoDuplicateNameAndTag(BlueprintVersion blueprintVersion);
}
