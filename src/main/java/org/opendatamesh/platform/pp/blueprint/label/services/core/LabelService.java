package org.opendatamesh.platform.pp.blueprint.label.services.core;

import org.opendatamesh.platform.pp.blueprint.label.entities.Label;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.label.LabelRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.label.LabelSearchOptions;
import org.opendatamesh.platform.pp.blueprint.utils.services.GenericMappedAndFilteredCrudService;

public interface LabelService extends GenericMappedAndFilteredCrudService<LabelSearchOptions, LabelRes, Label, String> {
}
