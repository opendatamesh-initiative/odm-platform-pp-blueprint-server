package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.publish;

import org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint;

public interface PublishBlueprintPersistenceOutboundPort {

    Blueprint findByUuidOrName(String uuid, String name);
    
}
