package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.publish;

import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;

public interface PublishBlueprintVersionPresenter {
    
    void presentPublished(BlueprintVersion blueprintVersion);
}
