package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate;

import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;

interface InstantiateBlueprintVersionPersistencyOutboundPort {

    BlueprintVersion findByBlueprintNameAndVersion(String blueprintName, String blueprintVersion);

    /**
     * Locates a published composition module by name and version (same lookup as parent).
     * Callers that are collecting validation issues should translate not-found into an issue with a hint.
     */
    BlueprintVersion findModuleBlueprintVersion(String blueprintName, String blueprintVersion);
}
