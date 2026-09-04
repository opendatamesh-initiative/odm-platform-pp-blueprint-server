package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.updatedataproduct;

/**
 * One collected update validation finding with a how-to-fix hint.
 */
record UpdateValidationIssue(
        String fieldPath,
        String problem,
        String hint
) {
    String format() {
        String path = fieldPath == null || fieldPath.isBlank() ? "" : fieldPath + ": ";
        String hintSuffix = hint == null || hint.isBlank() ? "" : ". Hint: " + hint;
        return path + problem + hintSuffix;
    }
}
