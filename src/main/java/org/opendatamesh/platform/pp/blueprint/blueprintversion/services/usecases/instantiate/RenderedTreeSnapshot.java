package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate;

import java.nio.file.Path;

/**
 * Holds the locally re-instantiated working tree copied out of the throwaway Git clone
 * so hashing can run after git-utils deletes the clone directories.
 */
public final class RenderedTreeSnapshot {

    private Path expectedTreeRoot;

    public Path getExpectedTreeRoot() {
        return expectedTreeRoot;
    }

    public void setExpectedTreeRoot(Path expectedTreeRoot) {
        this.expectedTreeRoot = expectedTreeRoot;
    }
}
