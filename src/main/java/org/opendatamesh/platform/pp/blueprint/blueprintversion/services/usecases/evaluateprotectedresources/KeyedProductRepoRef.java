package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources;

public record KeyedProductRepoRef(
        String repositoryKey,
        String ref
) {
}
