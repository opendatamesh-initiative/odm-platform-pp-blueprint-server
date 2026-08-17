package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.updatedataproduct;

import java.util.List;

/**
 * Domain outcome presented by the use case after all targets are processed.
 * Aggregates per-target Git results and any best-effort side-operation warnings.
 */
public record UpdateDataProductResult(
        List<UpdateDataProductTargetResult> results,
        List<String> warnings
) {
}
