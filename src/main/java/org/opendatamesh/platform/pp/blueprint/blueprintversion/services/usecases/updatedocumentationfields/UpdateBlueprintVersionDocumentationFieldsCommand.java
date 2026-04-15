package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.updatedocumentationfields;

public record UpdateBlueprintVersionDocumentationFieldsCommand(
        String uuid,
        String name,
        String description,
        String updatedBy
) {
}
