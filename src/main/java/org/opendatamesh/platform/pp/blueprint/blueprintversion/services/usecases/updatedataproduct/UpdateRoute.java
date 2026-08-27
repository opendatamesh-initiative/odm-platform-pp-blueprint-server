package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.updatedataproduct;

/**
 * One file-routing instruction from a parent {@code root.targets} or {@code composition[].targets} entry.
 */
record UpdateRoute(
        String sourceId,
        String sourcePath,
        String repositoryKey,
        String destinationPath,
        boolean fromParent
) {
}
