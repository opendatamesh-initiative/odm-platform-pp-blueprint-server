package org.opendatamesh.platform.pp.blueprint.blueprint.services.usecases.updatedocumentationfields;

import org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint;

interface UpdateBlueprintDocumentationFieldsSemanticValidationOutboundPort {

    void validate(Blueprint blueprint);
}
