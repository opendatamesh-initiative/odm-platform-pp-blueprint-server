package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Holds locally re-instantiated working trees copied out of throwaway Git clones,
 * keyed by logical destination repository key, so hashing can run after git-utils
 * deletes the clone directories.
 */
public final class RenderedTreeSnapshot {

    private final Map<String, Path> expectedTrees = new LinkedHashMap<>();

    public void putExpectedTree(String repositoryKey, Path root) {
        Path previous = expectedTrees.put(repositoryKey, root);
        if (previous != null && !previous.equals(root)) {
            deleteRecursively(previous);
        }
    }

    public Path getExpectedTree(String repositoryKey) {
        return expectedTrees.get(repositoryKey);
    }

    public Collection<Path> values() {
        return expectedTrees.values();
    }

    private static void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            });
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }
}
