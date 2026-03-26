package org.opendatamesh.platform.pp.blueprint.blueprint.services.core;

import org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintSearchOptions;
import org.opendatamesh.platform.pp.blueprint.utils.services.GenericMappedAndFilteredCrudService;

public interface BlueprintService extends GenericMappedAndFilteredCrudService<BlueprintSearchOptions, BlueprintRes, Blueprint, String>{
}
