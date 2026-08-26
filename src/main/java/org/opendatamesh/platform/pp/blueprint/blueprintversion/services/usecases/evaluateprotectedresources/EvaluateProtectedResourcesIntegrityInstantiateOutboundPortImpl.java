package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources;

import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate.InstantiateBlueprintVersionCommand;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate.InstantiateBlueprintVersionFactory;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate.RenderedTreeSnapshot;
import org.springframework.http.HttpHeaders;

class EvaluateProtectedResourcesIntegrityInstantiateOutboundPortImpl
        implements EvaluateProtectedResourcesIntegrityInstantiateOutboundPort {

    private final InstantiateBlueprintVersionFactory instantiateFactory;

    EvaluateProtectedResourcesIntegrityInstantiateOutboundPortImpl(InstantiateBlueprintVersionFactory instantiateFactory) {
        this.instantiateFactory = instantiateFactory;
    }

    @Override
    public void executeLocalInstantiation(
            InstantiateBlueprintVersionCommand command,
            HttpHeaders blueprintGitHeaders,
            RenderedTreeSnapshot snapshot
    ) {
        instantiateFactory.buildInstantiateBlueprintVersionForLocalValidation(
                command,
                result -> {
                    // expected tree is captured by the local Git port into the snapshot
                },
                blueprintGitHeaders,
                snapshot
        ).execute();
    }
}
