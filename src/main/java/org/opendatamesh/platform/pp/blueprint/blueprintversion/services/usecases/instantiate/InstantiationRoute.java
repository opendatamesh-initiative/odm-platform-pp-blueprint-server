package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate;

/**
 * One file-routing instruction from a parent {@code root.targets} or {@code composition[].targets} entry.
 */
record InstantiationRoute(
        String sourceId,
        String sourcePath,
        String repositoryKey,
        String destinationPath,
        boolean fromParent
) {
}
