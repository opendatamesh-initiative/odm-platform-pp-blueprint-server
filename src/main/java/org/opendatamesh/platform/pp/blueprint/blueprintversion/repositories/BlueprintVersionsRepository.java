package org.opendatamesh.platform.pp.blueprint.blueprintversion.repositories;

import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.utils.repositories.PagingAndSortingAndSpecificationExecutorRepository;

public interface BlueprintVersionsRepository extends PagingAndSortingAndSpecificationExecutorRepository<BlueprintVersion, String> {

    // JPA named methods for uniqueness validation

    /**
     * Check if a BlueprintVersion exists by versionNumber and blueprintUuid (case-insensitive)
     */
    boolean existsByVersionNumberIgnoreCaseAndBlueprintUuid(String versionNumber, String blueprintUuid);

    /**
     * Check if a BlueprintVersion exists by versionNumber and blueprintUuid excluding a specific UUID (case-insensitive)
     */
    boolean existsByVersionNumberIgnoreCaseAndBlueprintUuidAndUuidNot(String versionNumber, String blueprintUuid, String uuid);

    boolean existsByBlueprint_UuidAndNameIgnoreCaseAndTagIgnoreCase(String blueprintUuid, String name, String tag);
}
