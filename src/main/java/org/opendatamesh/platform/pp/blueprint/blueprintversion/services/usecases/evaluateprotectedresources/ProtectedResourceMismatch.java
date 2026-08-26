package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources;

import java.util.List;

public record ProtectedResourceMismatch(
        String declaredPath,
        MismatchKind kind,
        List<String> affectedFiles,
        String detail
) {
}
