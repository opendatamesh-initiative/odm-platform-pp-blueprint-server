package org.opendatamesh.platform.pp.blueprint.blueprintversion.repositories;

import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.utils.repositories.PagingAndSortingAndSpecificationExecutorRepository;
import org.opendatamesh.platform.pp.blueprint.utils.repositories.SpecsUtils;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

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

    class Specs extends SpecsUtils {

        public static Specification<BlueprintVersion> hasBlueprintUuid(String blueprintUuid) {
            return (root, query, cb) -> {
                if (!StringUtils.hasText(blueprintUuid)) {
                    return cb.conjunction();
                }
                return cb.equal(root.get("blueprintUuid"), blueprintUuid);
            };
        }

        public static Specification<BlueprintVersion> hasBlueprintName(String blueprintName) {
            return (root, query, cb) -> {
                if (!StringUtils.hasText(blueprintName)) {
                    return cb.conjunction();
                }
                return cb.equal(cb.lower(root.get("blueprint").get("name")), blueprintName.toLowerCase());
            };
        }

        public static Specification<BlueprintVersion> hasName(String name) {
            return (root, query, cb) -> {
                if (!StringUtils.hasText(name)) {
                    return cb.conjunction();
                }
                return cb.equal(cb.lower(root.get("name")), name.toLowerCase());
            };
        }

        public static Specification<BlueprintVersion> hasTag(String tag) {
            return (root, query, cb) -> {
                if (!StringUtils.hasText(tag)) {
                    return cb.conjunction();
                }
                return cb.equal(cb.lower(root.get("tag")), tag.toLowerCase());
            };
        }

        public static Specification<BlueprintVersion> hasVersionNumber(String versionNumber) {
            return (root, query, cb) -> {
                if (!StringUtils.hasText(versionNumber)) {
                    return cb.conjunction();
                }
                return cb.equal(cb.lower(root.get("versionNumber")), versionNumber.toLowerCase());
            };
        }
    }
}
