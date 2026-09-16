package org.opendatamesh.platform.pp.blueprint.blueprint.repositories;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.BlueprintType;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint_;
import org.opendatamesh.platform.pp.blueprint.label.entities.Label;
import org.opendatamesh.platform.pp.blueprint.label.entities.Label_;
import org.opendatamesh.platform.pp.blueprint.utils.repositories.PagingAndSortingAndSpecificationExecutorRepository;
import org.opendatamesh.platform.pp.blueprint.utils.repositories.SpecsUtils;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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

        public static Specification<Blueprint> hasAllLabelUuids(Collection<String> labelUuids) {
            return (root, query, cb) -> {
                if (labelUuids == null || labelUuids.isEmpty()) {
                    return cb.conjunction();
                }
                List<Predicate> predicates = new ArrayList<>();
                for (String labelUuid : labelUuids) {
                    if (!StringUtils.hasText(labelUuid)) {
                        continue;
                    }
                    Subquery<String> subquery = query.subquery(String.class);
                    Root<Blueprint> subRoot = subquery.from(Blueprint.class);
                    Join<Blueprint, Label> labels = subRoot.join(Blueprint_.labels);
                    subquery.select(subRoot.get(Blueprint_.uuid))
                            .where(
                                    cb.equal(subRoot.get(Blueprint_.uuid), root.get(Blueprint_.uuid)),
                                    cb.equal(labels.get(Label_.uuid), labelUuid)
                            );
                    predicates.add(cb.exists(subquery));
                }
                if (predicates.isEmpty()) {
                    return cb.conjunction();
                }
                return cb.and(predicates.toArray(Predicate[]::new));
            };
        }
    }
}
