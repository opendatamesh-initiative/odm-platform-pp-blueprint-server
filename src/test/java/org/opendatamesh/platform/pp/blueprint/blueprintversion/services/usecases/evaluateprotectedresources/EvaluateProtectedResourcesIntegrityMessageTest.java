package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluateProtectedResourcesIntegrityMessageTest {

    @Test
    void failureMessageNamesDeclaredPathKindAndFiles() {
        List<ProtectedResourceMismatch> mismatches = List.of(
                new ProtectedResourceMismatch(
                        "infrastructure/core/**",
                        MismatchKind.MISSING_ON_PUBLISHED,
                        List.of("infrastructure/core/main.tf"),
                        null
                ),
                new ProtectedResourceMismatch(
                        "README.md",
                        MismatchKind.CONTENT_DIFFERS,
                        List.of(),
                        null
                ),
                new ProtectedResourceMismatch(
                        "docs/**",
                        MismatchKind.MISSING_ON_REINSTANTIATED,
                        List.of("docs/a.md", "docs/b.md"),
                        null
                ),
                new ProtectedResourceMismatch(
                        "secret",
                        MismatchKind.UNSUPPORTED_ALGORITHM,
                        List.of(),
                        "md5"
                )
        );
        String message = EvaluateProtectedResourcesIntegrity.formatFailureMessage(mismatches);
        assertThat(message).isEqualTo(
                "Protected resource 'infrastructure/core/**' is missing file 'infrastructure/core/main.tf' from the data product version; "
                        + "Protected resource 'README.md': file contents differ from the blueprint; "
                        + "Protected resource 'docs/**': files 'docs/a.md', 'docs/b.md' are in the data product version but are not produced by the blueprint; "
                        + "Protected resource 'secret' uses an integrity check that is not supported"
        );
    }
}
