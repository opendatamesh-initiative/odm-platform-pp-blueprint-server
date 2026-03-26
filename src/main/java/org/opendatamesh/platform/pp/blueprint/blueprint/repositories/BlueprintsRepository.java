package org.opendatamesh.platform.pp.blueprint.blueprint.repositories;

import org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint_;
import org.opendatamesh.platform.pp.blueprint.utils.repositories.PagingAndSortingAndSpecificationExecutorRepository;
import org.opendatamesh.platform.pp.blueprint.utils.repositories.SpecsUtils;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public interface BlueprintsRepository extends PagingAndSortingAndSpecificationExecutorRepository<Blueprint, String> {

    // JPA named methods for uniqueness validation

    /**
     * Check if a Blueprint exists by name (case-insensitive)
     */
    boolean existsByNameIgnoreCase(String name);

    /**
     * Check if a Blueprint exists by name (case-insensitive) excluding a specific UUID
     */
    boolean existsByNameIgnoreCaseAndUuidNot(String name, String uuid);

    class Specs extends SpecsUtils {

        public static Specification<Blueprint> hasName(String name) {
            return (root, query, cb) -> {
                if (!StringUtils.hasText(name)) {
                    return cb.conjunction();
                }
                return cb.equal(cb.lower(root.get(Blueprint_.name)), name.toLowerCase());
            };
        }
    }
}
