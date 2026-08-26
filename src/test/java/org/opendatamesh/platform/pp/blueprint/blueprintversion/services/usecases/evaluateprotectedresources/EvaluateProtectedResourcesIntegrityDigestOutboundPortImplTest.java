package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluateProtectedResourcesIntegrityDigestOutboundPortImplTest {

    private final EvaluateProtectedResourcesIntegrityDigestOutboundPortImpl digestPort =
            new EvaluateProtectedResourcesIntegrityDigestOutboundPortImpl();

    @Test
    void fileDigestIsSha256OfRawBytes(@TempDir Path repo) throws Exception {
        Path file = repo.resolve("README.md");
        Files.writeString(file, "hello");
        DigestResult result = digestPort.digest(repo, "README.md");
        assertThat(result.hasError()).isFalse();
        assertThat(result.fileDigests()).containsKey("README.md");
        assertThat(result.fileDigests().get("README.md"))
                .isEqualTo(EvaluateProtectedResourcesIntegrityDigestOutboundPortImpl.sha256Hex("hello".getBytes(StandardCharsets.UTF_8)));
        assertThat(result.fileDigests().get("README.md")).isLowerCase();
    }

    @Test
    void directoryDigestConcatenatesSortedRelativePaths(@TempDir Path repo) throws Exception {
        Files.createDirectories(repo.resolve("dir"));
        Files.writeString(repo.resolve("dir/b.txt"), "b");
        Files.writeString(repo.resolve("dir/a.txt"), "a");
        DigestResult result = digestPort.digest(repo, "dir");
        assertThat(result.hasError()).isFalse();
        assertThat(result.fileDigests().keySet()).containsExactly("dir/a.txt", "dir/b.txt");
        Map<String, String> expectedOrder = result.fileDigests();
        String combined = EvaluateProtectedResourcesIntegrityDigestOutboundPortImpl.combinedDigest(expectedOrder);
        assertThat(combined).hasSize(64).isLowerCase();
    }

    @Test
    void globMatchesRepositoryRelativePaths(@TempDir Path repo) throws Exception {
        Files.createDirectories(repo.resolve("infrastructure/core"));
        Files.writeString(repo.resolve("infrastructure/core/network.tf"), "net");
        Files.writeString(repo.resolve("infrastructure/other.tf"), "other");
        DigestResult result = digestPort.digest(repo, "infrastructure/core/**");
        assertThat(result.hasError()).isFalse();
        assertThat(result.fileDigests()).containsOnlyKeys("infrastructure/core/network.tf");
    }

    @Test
    void emptyMatchWhenPathMissing(@TempDir Path repo) {
        DigestResult result = digestPort.digest(repo, "missing.txt");
        assertThat(result.hasError()).isFalse();
        assertThat(result.isEmptyMatch()).isTrue();
    }

    @Test
    void symlinkFailsDeclaredPath(@TempDir Path repo) throws Exception {
        Path target = repo.resolve("real.txt");
        Files.writeString(target, "x");
        Path link = repo.resolve("link.txt");
        Files.createSymbolicLink(link, target.getFileName());
        DigestResult result = digestPort.digest(repo, "link.txt");
        assertThat(result.hasError()).isTrue();
        assertThat(result.error()).isEqualTo(MismatchKind.SYMLINK);
    }

    @Test
    void pathTraversalIsInvalid(@TempDir Path repo) {
        DigestResult result = digestPort.digest(repo, "../secret");
        assertThat(result.hasError()).isTrue();
        assertThat(result.error()).isEqualTo(MismatchKind.INVALID_PATH);
    }

    @Test
    void sha256AlgorithmIsAcceptedOnFile(@TempDir Path repo) throws Exception {
        Files.writeString(repo.resolve("a.txt"), "x");
        DigestResult result = digestPort.digest(repo, "a.txt");
        assertThat(result.hasError()).isFalse();
        assertThat(EvaluateProtectedResourcesIntegrityDigestOutboundPortImpl.sha256Hex("x".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(result.fileDigests().get("a.txt"));
    }
}
