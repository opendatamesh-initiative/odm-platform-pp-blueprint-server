package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources;

import java.nio.file.Path;
import java.util.Map;

record DigestResult(
        MismatchKind error,
        String detail,
        Map<String, String> fileDigests
) {
    boolean hasError() {
        return error != null;
    }

    boolean isEmptyMatch() {
        return !hasError() && (fileDigests == null || fileDigests.isEmpty());
    }
}
