package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.updatedocumentationfields;

import org.opendatamesh.platform.pp.blueprint.exceptions.BadRequestException;
import org.springframework.util.StringUtils;

class UpdateBlueprintVersionDocumentationFieldsValidationOutboundPortImpl
        implements UpdateBlueprintVersionDocumentationFieldsValidationOutboundPort {

    @Override
    public void validate(UpdateBlueprintVersionDocumentationFieldsCommand command) {
        validateLength("Name", command.name(), 255);
        validateLength("Description", command.description(), 10000);
    }

    private void validateLength(String fieldName, String value, int maxLength) {
        if (StringUtils.hasText(value) && value.length() > maxLength) {
            throw new BadRequestException(fieldName + " cannot exceed " + maxLength + " characters");
        }
    }
}
