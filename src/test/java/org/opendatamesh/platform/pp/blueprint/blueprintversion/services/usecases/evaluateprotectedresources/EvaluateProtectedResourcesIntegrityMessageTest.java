package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * User-facing policy failure messages for protected-resource mismatches.
 * Scenarios trace to {@code spdd/prompt/BDMD-5124-202608210930-[Feat]-service-protected-resources-integrity-policy-adapter.md} (Gherkin).
 */
class EvaluateProtectedResourcesIntegrityMessageTest {

    /**
     * Feature: Protected-resources integrity evaluation
     *
     * Scenario: Failure message names declared path, kind, and files
     *   Given mismatches for missing on published, contents differ, not produced by the blueprint, and unsupported algorithm
     *   When the failure message is formatted
     *   Then the message lists each declared path and the user-facing reason without mentioning digest
     */
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
