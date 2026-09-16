package org.opendatamesh.platform.pp.blueprint.old.v1;

/**
 * Fail-closed reconstruction error. Mapped to HTTP 200 with {@code evaluationResult=false},
 * not to a 5xx, so publication is blocked rather than silently passed.
 */
class RegistryReconstructionException extends RuntimeException {

    RegistryReconstructionException(String message) {
        super(message);
    }

    RegistryReconstructionException(String message, Throwable cause) {
        super(message, cause);
    }
}
