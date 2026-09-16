package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources;

import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.exceptions.NotFoundException;
import org.opendatamesh.platform.pp.blueprint.manifest.model.Manifest;

interface EvaluateProtectedResourcesIntegrityPersistencyOutboundPort {

    BlueprintVersion findByBlueprintNameAndVersion(String blueprintName, String blueprintVersion) throws NotFoundException;

    Manifest readManifest(BlueprintVersion blueprintVersion);
}
