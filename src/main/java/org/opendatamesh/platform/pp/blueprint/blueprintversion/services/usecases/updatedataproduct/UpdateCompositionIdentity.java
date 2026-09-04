package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.updatedataproduct;

/**
 * Composition module identity extracted from stored parent manifest content.
 */
record UpdateCompositionIdentity(
        String moduleAlias,
        String blueprintName,
        String blueprintVersion,
        String fieldPath
) {
}
