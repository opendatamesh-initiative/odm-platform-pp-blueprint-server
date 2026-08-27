package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources;

import java.nio.file.Path;

/**
 * A local working tree produced by an outbound port for hashing.
 * Adapters own the filesystem lifetime; {@link #close()} deletes the tree.
 */
interface WorkingTree extends AutoCloseable {

    Path path();

    @Override
    void close();
}
