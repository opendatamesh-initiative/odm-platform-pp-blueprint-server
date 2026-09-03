package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.manifestvalidator;

class OdmBlueprintValidatorErrorMessage {
    private final String fieldPath;
    private final String message;
    private final String hint;

    OdmBlueprintValidatorErrorMessage(String fieldPath, String message) {
        this(fieldPath, message, null);
    }

    OdmBlueprintValidatorErrorMessage(String fieldPath, String message, String hint) {
        this.fieldPath = fieldPath;
        this.message = message;
        this.hint = hint;
    }

    String getFieldPath() {
        return fieldPath;
    }

    String getMessage() {
        return message;
    }

    String getHint() {
        return hint;
    }

    String format() {
        String base;
        if (fieldPath != null && !fieldPath.isEmpty()) {
            base = String.format("%s: %s", fieldPath, message);
        } else {
            base = message;
        }
        if (hint != null && !hint.isEmpty()) {
            return base + ". Hint: " + hint;
        }
        return base;
    }
}
