package org.opendatamesh.platform.pp.blueprint.blueprintversion.repositories;

import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersionShort;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersionShort_;
import org.opendatamesh.platform.pp.blueprint.utils.repositories.PagingAndSortingAndSpecificationExecutorRepository;
import org.opendatamesh.platform.pp.blueprint.utils.repositories.SpecsUtils;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public interface BlueprintVersionsShortRepository extends PagingAndSortingAndSpecificationExecutorRepository<BlueprintVersionShort, String> {

    class Specs extends SpecsUtils {

        public static Specification<BlueprintVersionShort> hasBlueprintUuid(String blueprintUuid) {
            return (root, query, cb) -> {
                if (!StringUtils.hasText(blueprintUuid)) {
                    return cb.conjunction();
                }
                return cb.equal(root.get(BlueprintVersionShort_.blueprintUuid), blueprintUuid);
            };
        }

        public static Specification<BlueprintVersionShort> hasName(String name) {
            return (root, query, cb) -> {
                if (!StringUtils.hasText(name)) {
                    return cb.conjunction();
                }
                return cb.equal(cb.lower(root.get(BlueprintVersionShort_.name)), name.toLowerCase());
            };
        }

        public static Specification<BlueprintVersionShort> hasTag(String tag) {
            return (root, query, cb) -> {
                if (!StringUtils.hasText(tag)) {
                    return cb.conjunction();
                }
                return cb.equal(cb.lower(root.get(BlueprintVersionShort_.tag)), tag.toLowerCase());
            };
        }

        public static Specification<BlueprintVersionShort> hasVersionNumber(String versionNumber) {
            return (root, query, cb) -> {
                if (!StringUtils.hasText(versionNumber)) {
                    return cb.conjunction();
                }
                return cb.equal(cb.lower(root.get(BlueprintVersionShort_.versionNumber)), versionNumber.toLowerCase());
            };
        }

        public static Specification<BlueprintVersionShort> matchSearch(String search) {
            return (root, query, cb) -> {
                if (!StringUtils.hasText(search)) {
                    return cb.conjunction();
                }
                final String pattern = String.format("%%%s%%", escapeLikeParameter(search.toLowerCase(), LIKE_ESCAPE_CHAR));
                return cb.like(cb.lower(root.get(BlueprintVersionShort_.name)), pattern, LIKE_ESCAPE_CHAR);
            };
        }
    }
}
