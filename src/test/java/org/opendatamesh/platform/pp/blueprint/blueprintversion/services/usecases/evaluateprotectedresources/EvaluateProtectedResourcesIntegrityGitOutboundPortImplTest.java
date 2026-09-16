package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendatamesh.platform.git.git.GitOperation;
import org.opendatamesh.platform.git.model.Repository;
import org.opendatamesh.platform.git.provider.GitProvider;
import org.opendatamesh.platform.git.provider.GitProviderIdentifier;
import org.opendatamesh.platform.pp.blueprint.git.provider.GitProviderFactory;
import org.opendatamesh.platform.pp.blueprint.validator.config.BlueprintValidatorProperties;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvaluateProtectedResourcesIntegrityGitOutboundPortImplTest {

    /**
     * Feature: Lasting protected-resources integrity
     *
     * Scenario: Snapshot symbolic link fails safely
     *   Given a protected match is a symbolic link in a cloned or expected snapshot
     *   When the path is digested
     *   Then the outcome contains a SYMLINK mismatch
     */
    @Test
    void publishedSnapshotPreservesSymbolicLinks(@TempDir Path cloneDir) throws Exception {
        Files.writeString(cloneDir.resolve("real.txt"), "x");
        Files.createSymbolicLink(cloneDir.resolve("link.txt"), cloneDir.resolve("real.txt").getFileName());

        GitProviderFactory gitProviderFactory = mock(GitProviderFactory.class);
        GitProvider gitProvider = mock(GitProvider.class);
        GitOperation gitOperation = mock(GitOperation.class);
        when(gitProviderFactory.buildGitProvider(any(GitProviderIdentifier.class), any())).thenReturn(gitProvider);
        when(gitProvider.gitOperation()).thenReturn(gitOperation);
        doAnswer(invocation -> {
            Consumer<File> consumer = invocation.getArgument(2);
            consumer.accept(cloneDir.toFile());
            return null;
        }).when(gitOperation).readRepository(any(), any(), any());

        EvaluateProtectedResourcesIntegrityGitOutboundPortImpl port =
                new EvaluateProtectedResourcesIntegrityGitOutboundPortImpl(gitProviderFactory, validatorProperties());
        ProductRepoLocator locator = new ProductRepoLocator(
                "https://github.com/org/product.git",
                "GITHUB",
                "https://github.com",
                "product",
                "main",
                "org",
                "id");
        try (CloseableWorkingTree tree = port.clonePublishedDataProductVersion(locator, "v1.0.0")) {
            assertThat(Files.isSymbolicLink(tree.path().resolve("link.txt"))).isTrue();
            DigestResult digest = new EvaluateProtectedResourcesIntegrityDigestOutboundPortImpl()
                    .computeDigest(tree, "link.txt");
            assertThat(digest.error()).isEqualTo(MismatchKind.SYMLINK);
        }
    }

    private static BlueprintValidatorProperties validatorProperties() {
        BlueprintValidatorProperties properties = new BlueprintValidatorProperties();
        BlueprintValidatorProperties.GitCredential credential = new BlueprintValidatorProperties.GitCredential();
        credential.setProviderType("GITHUB");
        credential.setToken("test-token");
        properties.getGit().setCredentials(List.of(credential));
        return properties;
    }
}
