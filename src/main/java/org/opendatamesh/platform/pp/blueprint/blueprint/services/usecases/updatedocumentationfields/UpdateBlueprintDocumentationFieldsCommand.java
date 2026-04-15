package org.opendatamesh.platform.pp.blueprint.blueprint.services.usecases.updatedocumentationfields;

import org.opendatamesh.platform.pp.blueprint.blueprint.entities.BlueprintRepo;

public record UpdateBlueprintDocumentationFieldsCommand(
        String blueprintUuid,
        String displayName,
        String description,
        BlueprintRepo blueprintRepo
) {
}
