package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

class CloseableWorkingTree implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(CloseableWorkingTree.class);

    private final Path root;

    CloseableWorkingTree(Path root) {
        this.root = root;
    }

    Path path() {
        return root;
    }

    @Override
    public void close() {
        deleteRecursively(root);
    }

    static void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("Failed to delete working-tree file {}", p, e);
                }
            });
        } catch (IOException e) {
            log.warn("Failed to walk working tree for cleanup {}", path, e);
        }
    }
}
