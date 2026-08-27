package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate;

/**
 * Composition module identity extracted from stored parent manifest content.
 */
record InstantiationCompositionIdentity(
        String moduleAlias,
        String blueprintName,
        String blueprintVersion,
        String fieldPath
) {
}
