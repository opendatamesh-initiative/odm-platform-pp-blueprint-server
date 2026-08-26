package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources;

import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate.InstantiateBlueprintVersionCommand;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate.RenderedTreeSnapshot;
import org.springframework.http.HttpHeaders;

interface EvaluateProtectedResourcesIntegrityInstantiateOutboundPort {

    void executeLocalInstantiation(
            InstantiateBlueprintVersionCommand command,
            HttpHeaders blueprintGitHeaders,
            RenderedTreeSnapshot snapshot
    );
}
