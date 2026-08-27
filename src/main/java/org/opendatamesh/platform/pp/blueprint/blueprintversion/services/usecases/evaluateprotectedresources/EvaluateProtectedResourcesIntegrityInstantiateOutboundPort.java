package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources;

import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;

interface EvaluateProtectedResourcesIntegrityInstantiateOutboundPort {

    WorkingTree reinstantiateBlueprintLocally(
            BlueprintVersion blueprintVersion,
            EvaluateProtectedResourcesIntegrityCommand command
    );
}
