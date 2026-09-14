package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Closeable map of locally re-instantiated working trees keyed by logical destination.
 */
final class TargetWorkingTrees implements AutoCloseable {

    private final Map<String, WorkingTree> trees;

    TargetWorkingTrees(Map<String, WorkingTree> trees) {
        this.trees = trees == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(trees));
    }

    WorkingTree get(String repositoryKey) {
        return trees.get(repositoryKey);
    }

    Set<String> keys() {
        return trees.keySet();
    }

    @Override
    public void close() {
        RuntimeException firstFailure = null;
        for (WorkingTree tree : trees.values()) {
            if (tree == null) {
                continue;
            }
            try {
                tree.close();
            } catch (RuntimeException e) {
                if (firstFailure == null) {
                    firstFailure = e;
                }
            }
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    static TargetWorkingTrees of(Map<String, WorkingTree> trees) {
        return new TargetWorkingTrees(new LinkedHashMap<>(trees));
    }
}
