package org.opendatamesh.platform.pp.blueprint.blueprint.services.usecases.updatedocumentationfields;

import org.opendatamesh.platform.pp.blueprint.blueprint.entities.BlueprintRepo;
import org.opendatamesh.platform.pp.blueprint.label.entities.Label;

import java.util.List;

public record UpdateBlueprintDocumentationFieldsCommand(
        String blueprintUuid,
        String displayName,
        String description,
        BlueprintRepo blueprintRepo,
        List<Label> labels
) {
}
