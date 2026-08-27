package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources;

public enum MismatchKind {
    MISSING_ON_PUBLISHED,
    MISSING_ON_REINSTANTIATED,
    CONTENT_DIFFERS,
    INVALID_PATH,
    SYMLINK,
    UNSUPPORTED_ALGORITHM
}
