package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources;

import java.util.List;

public record IntegrityOutcome(
        OutcomeKind kind,
        String message,
        List<ProtectedResourceMismatch> mismatches
) {
    public static IntegrityOutcome notApplicable(String message) {
        return new IntegrityOutcome(OutcomeKind.NOT_APPLICABLE, message, List.of());
    }

    public static IntegrityOutcome passed(String message) {
        return new IntegrityOutcome(OutcomeKind.PASSED, message, List.of());
    }

    public static IntegrityOutcome failed(List<ProtectedResourceMismatch> mismatches, String message) {
        return new IntegrityOutcome(OutcomeKind.FAILED, message, mismatches == null ? List.of() : List.copyOf(mismatches));
    }

    public static IntegrityOutcome infrastructureFailed(String message) {
        return new IntegrityOutcome(OutcomeKind.INFRASTRUCTURE_FAILED, message, List.of());
    }
}
