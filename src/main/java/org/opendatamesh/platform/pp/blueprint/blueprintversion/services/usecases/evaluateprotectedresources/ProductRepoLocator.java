package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources;

public record ProductRepoLocator(
        String remoteUrlHttp,
        String providerType,
        String providerBaseUrl,
        String name,
        String defaultBranch,
        String ownerId,
        String externalIdentifier
) {
}
