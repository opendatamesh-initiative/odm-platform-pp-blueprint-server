package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Copies a Git working tree into a {@code .git}-free snapshot. Symbolic-link entries
 * are preserved and never followed so digest can report {@code SYMLINK}.
 */
final class WorkingTreeSnapshotCopier {

    private WorkingTreeSnapshotCopier() {
    }

    static void copySkippingGitPreservingSymlinks(Path source, Path destination) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (dir.getFileName() != null && ".git".equals(dir.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                if (Files.isSymbolicLink(dir) && !dir.equals(source)) {
                    copySymlink(source, dir, destination);
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Path relative = source.relativize(dir);
                if (!relative.toString().isEmpty()) {
                    Files.createDirectories(destination.resolve(relative));
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (Files.isSymbolicLink(file)) {
                    copySymlink(source, file, destination);
                    return FileVisitResult.CONTINUE;
                }
                if (!Files.isRegularFile(file)) {
                    return FileVisitResult.CONTINUE;
                }
                Path target = destination.resolve(source.relativize(file));
                if (target.getParent() != null) {
                    Files.createDirectories(target.getParent());
                }
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void copySymlink(Path sourceRoot, Path source, Path destinationRoot) throws IOException {
        Path destination = destinationRoot.resolve(sourceRoot.relativize(source));
        if (destination.getParent() != null) {
            Files.createDirectories(destination.getParent());
        }
        Files.deleteIfExists(destination);
        Files.createSymbolicLink(destination, Files.readSymbolicLink(source));
    }
}
