package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources;

import org.springframework.http.HttpHeaders;

import java.nio.file.Path;
import java.util.function.Consumer;

interface EvaluateProtectedResourcesIntegrityGitOutboundPort {

    void withClonedProductAtTag(
            ProductRepoLocator repo,
            String tag,
            HttpHeaders productGitHeaders,
            Consumer<Path> operation
    );
}
