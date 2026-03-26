package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.core;

import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.BlueprintVersionRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.BlueprintVersionSearchOptions;
import org.opendatamesh.platform.pp.blueprint.utils.services.GenericMappedAndFilteredCrudService;

/**
 * Service for CRUD operations on individual BlueprintVersion entities.
 * 
 * This service provides full CRUD functionality for managing single BlueprintVersion instances,
 * including all manifest and descriptor template content. It is designed for operations that require complete entity
 * data such as creating, updating, or retrieving individual versions.
 * 
 * <p><strong>Supported Operations:</strong></p>
 * <ul>
 *   <li>Create new blueprint versions</li>
 *   <li>Read individual blueprint versions by UUID</li>
 *   <li>Update existing blueprint versions</li>
 *   <li>Delete blueprint versions</li>
 * </ul>
 * 
 * <p><strong>Note:</strong> Paginated reads are disabled for this service to encourage
 * the use of {@link BlueprintVersionsQueryService} for listing operations, which provides
 * better performance by excluding manifest and descriptor template content.</p>
 * 
 * @see BlueprintVersionsQueryService for paginated read operations
 */

public interface BlueprintVersionCrudService extends GenericMappedAndFilteredCrudService<BlueprintVersionSearchOptions, BlueprintVersionRes, BlueprintVersion, String> {
    
}
