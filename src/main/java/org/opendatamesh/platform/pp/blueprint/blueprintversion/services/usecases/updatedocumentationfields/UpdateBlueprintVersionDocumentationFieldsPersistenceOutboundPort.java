package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.updatedocumentationfields;

import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;

interface UpdateBlueprintVersionDocumentationFieldsPersistenceOutboundPort {

    BlueprintVersion findByUuid(String uuid);

    BlueprintVersion update(BlueprintVersion blueprintVersion);
}
