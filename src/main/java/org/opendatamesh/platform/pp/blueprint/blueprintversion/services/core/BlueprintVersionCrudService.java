package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.core;

import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.BlueprintVersionRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.BlueprintVersionSearchOptions;
import org.opendatamesh.platform.pp.blueprint.utils.services.GenericMappedAndFilteredCrudService;

/**
 * Service for CRUD operations on individual BlueprintVersion entities.
 * 
 * 
 * <p><strong>Supported Operations:</strong></p>
 * <ul>
 *   <li>Create new blueprint versions</li>
 *   <li>Read individual blueprint versions by UUID</li>
 *   <li>Update existing blueprint versions</li>
 *   <li>Delete blueprint versions</li>
 * </ul>
 * 
 * 
 * @see BlueprintVersionsQueryService for paginated read operations
 */

public interface BlueprintVersionCrudService extends GenericMappedAndFilteredCrudService<BlueprintVersionSearchOptions, BlueprintVersionRes, BlueprintVersion, String> {
    
}
