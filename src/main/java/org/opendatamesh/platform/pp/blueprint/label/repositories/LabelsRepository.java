package org.opendatamesh.platform.pp.blueprint.label.repositories;

import org.opendatamesh.platform.pp.blueprint.label.entities.Label;
import org.opendatamesh.platform.pp.blueprint.label.entities.Label_;
import org.opendatamesh.platform.pp.blueprint.utils.repositories.PagingAndSortingAndSpecificationExecutorRepository;
import org.opendatamesh.platform.pp.blueprint.utils.repositories.SpecsUtils;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public interface LabelsRepository extends PagingAndSortingAndSpecificationExecutorRepository<Label, String> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndUuidNot(String name, String uuid);

    class Specs extends SpecsUtils {

        public static Specification<Label> hasName(String name) {
            return (root, query, cb) -> {
                if (!StringUtils.hasText(name)) {
                    return cb.conjunction();
                }
                final String pattern = String.format("%%%s%%", escapeLikeParameter(name.toLowerCase(), LIKE_ESCAPE_CHAR));
                return cb.like(cb.lower(root.get(Label_.name)), pattern, LIKE_ESCAPE_CHAR);
            };
        }
    }
}
