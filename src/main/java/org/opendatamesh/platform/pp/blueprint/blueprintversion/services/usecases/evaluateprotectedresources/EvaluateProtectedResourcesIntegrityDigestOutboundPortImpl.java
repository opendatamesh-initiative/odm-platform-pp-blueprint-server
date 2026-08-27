package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

class EvaluateProtectedResourcesIntegrityDigestOutboundPortImpl
        implements EvaluateProtectedResourcesIntegrityDigestOutboundPort {

    private static final char[] GLOB_META = {'*', '?', '[', '{'};

    @Override
    public DigestResult computeDigest(WorkingTree tree, String declaredPath) {
        Path repoRoot = tree == null ? null : tree.path();
        if (repoRoot == null || declaredPath == null || declaredPath.isBlank()) {
            return new DigestResult(MismatchKind.INVALID_PATH, "the protected path is empty", Map.of());
        }
        String normalizedDeclared = declaredPath.replace('\\', '/').replaceFirst("^/+", "");
        if (normalizedDeclared.isEmpty() || hasPathTraversal(normalizedDeclared)) {
            return new DigestResult(
                    MismatchKind.INVALID_PATH,
                    "the protected path is not allowed: " + declaredPath,
                    Map.of()
            );
        }

        Path root = repoRoot.toAbsolutePath().normalize();
        Path resolved = root.resolve(normalizedDeclared).normalize();
        if (!resolved.startsWith(root)) {
            return new DigestResult(
                    MismatchKind.INVALID_PATH,
                    "the protected path is not allowed: " + declaredPath,
                    Map.of()
            );
        }

        try {
            if (!hasGlobMetacharacters(normalizedDeclared)) {
                return digestLiteral(root, resolved, normalizedDeclared);
            }
            return digestGlob(root, normalizedDeclared);
        } catch (DigestSignal signal) {
            return new DigestResult(signal.kind, signal.detail, Map.of());
        } catch (IOException e) {
            return new DigestResult(
                    MismatchKind.INVALID_PATH,
                    "the protected path could not be read: %s".formatted(declaredPath),
                    Map.of()
            );
        }
    }

    private DigestResult digestLiteral(Path root, Path resolved, String relativePath) throws IOException, DigestSignal {
        if (Files.isSymbolicLink(resolved)) {
            throw new DigestSignal(MismatchKind.SYMLINK, "the path is a symbolic link: " + relativePath);
        }
        if (Files.isRegularFile(resolved)) {
            Map<String, String> files = new LinkedHashMap<>();
            files.put(relativePath, sha256Hex(Files.readAllBytes(resolved)));
            return new DigestResult(null, null, files);
        }
        if (Files.isDirectory(resolved)) {
            return digestDirectory(root, resolved);
        }
        return new DigestResult(null, null, Map.of());
    }

    private DigestResult digestDirectory(Path root, Path directory) throws IOException, DigestSignal {
        TreeMap<String, String> files = new TreeMap<>();
        Files.walkFileTree(directory, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws DigestSignal {
                if (dir.getFileName() != null && ".git".equals(dir.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                if (Files.isSymbolicLink(dir) && !dir.equals(directory)) {
                    throw new DigestSignal(MismatchKind.SYMLINK, "the path contains a symbolic link: " + relative(root, dir));
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException, DigestSignal {
                if (file.getFileName() != null && ".git".equals(file.getFileName().toString())) {
                    return FileVisitResult.CONTINUE;
                }
                if (Files.isSymbolicLink(file)) {
                    throw new DigestSignal(MismatchKind.SYMLINK, "the path contains a symbolic link: " + relative(root, file));
                }
                if (Files.isRegularFile(file)) {
                    files.put(relative(root, file), sha256Hex(Files.readAllBytes(file)));
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return new DigestResult(null, null, files);
    }

    private DigestResult digestGlob(Path root, String globPattern) throws IOException, DigestSignal {
        FileSystem fileSystem = root.getFileSystem();
        PathMatcher matcher = fileSystem.getPathMatcher("glob:" + globPattern);
        TreeMap<String, String> files = new TreeMap<>();
        Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (dir.getFileName() != null && ".git".equals(dir.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException, DigestSignal {
                String relative = relative(root, file);
                if (!matcher.matches(fileSystem.getPath(relative)) && !matcher.matches(root.relativize(file))) {
                    return FileVisitResult.CONTINUE;
                }
                if (Files.isSymbolicLink(file)) {
                    throw new DigestSignal(MismatchKind.SYMLINK, "the path contains a symbolic link: " + relative);
                }
                if (Files.isRegularFile(file)) {
                    files.put(relative, sha256Hex(Files.readAllBytes(file)));
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return new DigestResult(null, null, files);
    }

    static String combinedDigest(Map<String, String> fileDigests) {
        if (fileDigests == null || fileDigests.isEmpty()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Map.Entry<String, String> entry : fileDigests.entrySet()) {
                digest.update(entry.getKey().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(HexFormat.of().parseHex(entry.getValue()));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static boolean hasGlobMetacharacters(String path) {
        for (char meta : GLOB_META) {
            if (path.indexOf(meta) >= 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasPathTraversal(String relativePath) {
        for (String segment : relativePath.split("/")) {
            if ("..".equals(segment)) {
                return true;
            }
        }
        return false;
    }

    private static String relative(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    static final class DigestSignal extends RuntimeException {
        private final MismatchKind kind;
        private final String detail;

        DigestSignal(MismatchKind kind, String detail) {
            super(detail);
            this.kind = kind;
            this.detail = detail;
        }
    }
}
