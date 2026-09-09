package org.opendatamesh.platform.pp.blueprint.blueprint.repositories;

import jakarta.persistence.criteria.Join;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.BlueprintType;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint_;
import org.opendatamesh.platform.pp.blueprint.label.entities.Label;
import org.opendatamesh.platform.pp.blueprint.label.entities.Label_;
import org.opendatamesh.platform.pp.blueprint.utils.repositories.PagingAndSortingAndSpecificationExecutorRepository;
import org.opendatamesh.platform.pp.blueprint.utils.repositories.SpecsUtils;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.Collection;

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

        public static Specification<Blueprint> hasUuid(String uuid) {
            return (root, query, cb) -> {
                if (!StringUtils.hasText(uuid)) {
                    return cb.conjunction();
                }
                return cb.equal(cb.lower(root.get(Blueprint_.uuid)), uuid.toLowerCase());
            };
        }

        public static Specification<Blueprint> hasBlueprintType(BlueprintType blueprintType) {
            return (root, query, cb) -> {
                if (blueprintType == null) {
                    return cb.conjunction();
                }
                return cb.equal(root.get(Blueprint_.blueprintType), blueprintType);
            };
        }

        public static Specification<Blueprint> hasAnyLabelUuid(Collection<String> labelUuids) {
            return (root, query, cb) -> {
                if (labelUuids == null || labelUuids.isEmpty()) {
                    return cb.conjunction();
                }
                if (query != null) {
                    query.distinct(true);
                }
                Join<Blueprint, Label> labels = root.join(Blueprint_.labels);
                return labels.get(Label_.uuid).in(labelUuids);
            };
        }
    }
}
