package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.core;

import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersionShort;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.BlueprintVersionShortRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.BlueprintVersionSearchOptions;

    /**
 * Service for querying and retrieving multiple BlueprintVersion entities.
 * <p>
 * This service is optimized for read operations that involve multiple BlueprintVersion instances,
 * such as listing, searching, and filtering. It returns lightweight resources without manifest and descriptor template
 * content to provide better performance when dealing with large datasets.
 *
 * <p><strong>Supported Operations:</strong></p>
 * <ul>
 *   <li>Find individual blueprint version by UUID</li>
 *   <li>Paginated search and listing of blueprint versions</li>
 *   <li>Filtering by various criteria (blueprint UUID, name, tag)</li>
 *   <li>Sorting by multiple fields</li>
 * </ul>
 *
 * <p><strong>Performance Benefits:</strong></p>
 * <ul>
 *   <li>Excludes manifest content and descriptor template content to reduce payload size</li>
 *   <li>Optimized for pagination and large result sets</li>
 *   <li>Returns {@link BlueprintVersionShortRes} for faster serialization</li>
 * </ul>
 *
 * <p><strong>Use Cases:</strong></p>
 * <ul>
 *   <li>blueprint version listings in UI</li>
 *   <li>Search functionality</li>
 *   <li>Bulk operations that don't require full entity data</li>
 * </ul>
 *
 * @see BlueprintVersionCrudService for individual entity operations
 * @see BlueprintVersionShortRes for the lightweight resource format
 */
public interface BlueprintVersionQueryService {

    /**
     * Find a blueprint version by UUID, returning the short entity (without manifest).
     * Throws {@link org.opendatamesh.platform.pp.registry.exceptions.NotFoundException} if not found.
     *
     * @param uuid the UUID of the blueprint version
     * @return the blueprint version short entity
     * @throws org.opendatamesh.platform.pp.registry.exceptions.NotFoundException if the version is not found
     */
    BlueprintVersionShort findOne(String uuid);

    /**
     * Find all blueprint versions with pagination, returning short resources (without manifest)
     * for better performance when listing multiple versions.
     *
     * @param pageable      pagination and sorting blueprintParameters
     * @param searchOptions filtering criteria for the search
     * @return paginated list of blueprint version short resources
     */
    Page<BlueprintVersionShortRes> findAllResourcesShort(Pageable pageable, BlueprintVersionSearchOptions searchOptions);

    Page<BlueprintVersionShort> findAllShort(Pageable pageable, BlueprintVersionSearchOptions searchOptions);

}
