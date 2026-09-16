package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources;

public record KeyedProductRepoLocator(
        String repositoryKey,
        ProductRepoLocator locator
) {
}
