package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.manifestvalidator;

import org.opendatamesh.platform.pp.blueprint.exceptions.BadRequestException;
import java.util.ArrayList;
import java.util.List;

class OdmBlueprintManifestValidatorContext {
    private final List<OdmBlueprintValidatorErrorMessage> errors = new ArrayList<>();

    private final List<String> parameters = new ArrayList<>();

    void addError(String fieldPath, String message) {
        errors.add(new OdmBlueprintValidatorErrorMessage(fieldPath, message));
    }

    boolean hasErrors() {
        return !errors.isEmpty();
    }

    List<OdmBlueprintValidatorErrorMessage> getErrors() {
        return new ArrayList<>(errors);
    }

    void throwIfHasErrors() {
        if (hasErrors()) {
            StringBuilder messageBuilder = new StringBuilder("Manifest blueprint validation failed:");
            for (OdmBlueprintValidatorErrorMessage error : errors) {
                messageBuilder.append("\n  ").append(error.format());
            }
            throw new BadRequestException(messageBuilder.toString());
        }
    }

    boolean addParameterKey(String key, String fieldPath) {
        if (key == null || key.trim().isEmpty()) {
            addError(fieldPath, "Parameter key is required");
            return false;
        }
        if (parameters.contains(key)) {
            addError(fieldPath, String.format("Parameter key '%s' must be unique within parameters", key));
            return false;
        }
        parameters.add(key);
        return true;
    }
}
