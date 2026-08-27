package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Canonical digest rules for protected-resource paths.
 * Scenarios trace to {@code spdd/prompt/BDMD-5124-202608210930-[Feat]-service-protected-resources-integrity-policy-adapter.md} (Gherkin).
 */
class EvaluateProtectedResourcesIntegrityDigestOutboundPortImplTest {

    private final EvaluateProtectedResourcesIntegrityDigestOutboundPortImpl digestPort =
            new EvaluateProtectedResourcesIntegrityDigestOutboundPortImpl();

    /**
     * Feature: Protected-resources integrity evaluation
     *
     * Scenario: File digest is SHA-256 of raw bytes
     *   Given a working tree with file "README.md" containing "hello"
     *   When computeDigest is called for "README.md"
     *   Then the hex digest is the lowercase SHA-256 of the raw bytes
     */
    @Test
    void fileDigestIsSha256OfRawBytes(@TempDir Path repo) throws Exception {
        Path file = repo.resolve("README.md");
        Files.writeString(file, "hello");
        DigestResult result = digestPort.computeDigest(tree(repo), "README.md");
        assertThat(result.hasError()).isFalse();
        assertThat(result.fileDigests()).containsKey("README.md");
        assertThat(result.fileDigests().get("README.md"))
                .isEqualTo(EvaluateProtectedResourcesIntegrityDigestOutboundPortImpl.sha256Hex("hello".getBytes(StandardCharsets.UTF_8)));
        assertThat(result.fileDigests().get("README.md")).isLowerCase();
    }

    /**
     * Feature: Protected-resources integrity evaluation
     *
     * Scenario: Directory digest concatenates files in lexicographic relative-path order
     *   Given a working tree with "dir/b.txt" and "dir/a.txt"
     *   When computeDigest is called for "dir"
     *   Then matched files are "dir/a.txt" then "dir/b.txt"
     */
    @Test
    void directoryDigestConcatenatesSortedRelativePaths(@TempDir Path repo) throws Exception {
        Files.createDirectories(repo.resolve("dir"));
        Files.writeString(repo.resolve("dir/b.txt"), "b");
        Files.writeString(repo.resolve("dir/a.txt"), "a");
        DigestResult result = digestPort.computeDigest(tree(repo), "dir");
        assertThat(result.hasError()).isFalse();
        assertThat(result.fileDigests().keySet()).containsExactly("dir/a.txt", "dir/b.txt");
        Map<String, String> expectedOrder = result.fileDigests();
        String combined = EvaluateProtectedResourcesIntegrityDigestOutboundPortImpl.combinedDigest(expectedOrder);
        assertThat(combined).hasSize(64).isLowerCase();
    }

    /**
     * Feature: Protected-resources integrity evaluation
     *
     * Scenario: Glob matches repository-relative paths
     *   Given a working tree with "infrastructure/core/network.tf" and "infrastructure/other.tf"
     *   When computeDigest is called for "infrastructure/core/**"
     *   Then only "infrastructure/core/network.tf" is matched
     */
    @Test
    void globMatchesRepositoryRelativePaths(@TempDir Path repo) throws Exception {
        Files.createDirectories(repo.resolve("infrastructure/core"));
        Files.writeString(repo.resolve("infrastructure/core/network.tf"), "net");
        Files.writeString(repo.resolve("infrastructure/other.tf"), "other");
        DigestResult result = digestPort.computeDigest(tree(repo), "infrastructure/core/**");
        assertThat(result.hasError()).isFalse();
        assertThat(result.fileDigests()).containsOnlyKeys("infrastructure/core/network.tf");
    }

    /**
     * Feature: Protected-resources integrity evaluation
     *
     * Scenario: Missing path is an empty match
     *   Given a working tree that does not contain "missing.txt"
     *   When computeDigest is called for "missing.txt"
     *   Then the result is an empty match and not an error
     */
    @Test
    void emptyMatchWhenPathMissing(@TempDir Path repo) {
        DigestResult result = digestPort.computeDigest(tree(repo), "missing.txt");
        assertThat(result.hasError()).isFalse();
        assertThat(result.isEmptyMatch()).isTrue();
    }

    /**
     * Feature: Protected-resources integrity evaluation
     *
     * Scenario: Symbolic link fails the declared path
     *   Given a working tree where "link.txt" is a symbolic link
     *   When computeDigest is called for "link.txt"
     *   Then the result is a SYMLINK error
     */
    @Test
    void symlinkFailsDeclaredPath(@TempDir Path repo) throws Exception {
        Path target = repo.resolve("real.txt");
        Files.writeString(target, "x");
        Path link = repo.resolve("link.txt");
        Files.createSymbolicLink(link, target.getFileName());
        DigestResult result = digestPort.computeDigest(tree(repo), "link.txt");
        assertThat(result.hasError()).isTrue();
        assertThat(result.error()).isEqualTo(MismatchKind.SYMLINK);
    }

    /**
     * Feature: Protected-resources integrity evaluation
     *
     * Scenario: Path traversal is an invalid path
     *   Given any working tree
     *   When computeDigest is called for "../secret"
     *   Then the result is an INVALID_PATH error
     */
    @Test
    void pathTraversalIsInvalid(@TempDir Path repo) {
        DigestResult result = digestPort.computeDigest(tree(repo), "../secret");
        assertThat(result.hasError()).isTrue();
        assertThat(result.error()).isEqualTo(MismatchKind.INVALID_PATH);
    }

    /**
     * Feature: Protected-resources integrity evaluation
     *
     * Scenario: File digest is SHA-256 of raw bytes
     *   Given a working tree with file "a.txt" containing "x"
     *   When computeDigest is called for "a.txt"
     *   Then the hex digest is the lowercase SHA-256 of the raw bytes
     */
    @Test
    void sha256AlgorithmIsAcceptedOnFile(@TempDir Path repo) throws Exception {
        Files.writeString(repo.resolve("a.txt"), "x");
        DigestResult result = digestPort.computeDigest(tree(repo), "a.txt");
        assertThat(result.hasError()).isFalse();
        assertThat(EvaluateProtectedResourcesIntegrityDigestOutboundPortImpl.sha256Hex("x".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(result.fileDigests().get("a.txt"));
    }

    private static WorkingTree tree(Path path) {
        return new WorkingTree() {
            @Override
            public Path path() {
                return path;
            }

            @Override
            public void close() {
            }
        };
    }
}
