package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.core;

import org.opendatamesh.platform.pp.blueprint.blueprint.services.core.BlueprintService;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.repositories.BlueprintVersionsRepository;
import org.opendatamesh.platform.pp.blueprint.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.blueprint.exceptions.ResourceConflictException;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.BlueprintVersionMapper;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.BlueprintVersionRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.BlueprintVersionSearchOptions;
import org.opendatamesh.platform.pp.blueprint.utils.repositories.PagingAndSortingAndSpecificationExecutorRepository;
import org.opendatamesh.platform.pp.blueprint.utils.services.GenericMappedAndFilteredCrudServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Implementation of {@link BlueprintVersionCrudService} for CRUD operations on individual BlueprintVersion entities.
 * <p>
 * This service implementation provides full CRUD functionality including all manifest and descriptor template content.
 * Paginated reads are explicitly disabled to encourage the use of the query service for listing operations.
 */

@Service
public class BlueprintVersionCrudServiceImpl extends GenericMappedAndFilteredCrudServiceImpl<BlueprintVersionSearchOptions, BlueprintVersionRes, BlueprintVersion, String> implements BlueprintVersionCrudService {

    private final BlueprintVersionMapper mapper;
    private final BlueprintVersionsRepository repository;
    private final BlueprintService blueprintService;

    @Autowired
    public BlueprintVersionCrudServiceImpl(
            BlueprintVersionMapper mapper,
            BlueprintVersionsRepository repository,
            BlueprintService blueprintService) {
        this.mapper = mapper;
        this.repository = repository;
        this.blueprintService = blueprintService;
    }

    @Override
    protected BlueprintVersion toEntity(BlueprintVersionRes resource) {
        return mapper.toEntity(resource);
    }

    @Override
    protected void validate(BlueprintVersion blueprintVersion) {
        validateRequiredFields(blueprintVersion);
        validateFieldConstraints(blueprintVersion);
    }

    private void validateRequiredFields(BlueprintVersion blueprintVersion) {
        if (!StringUtils.hasText(blueprintVersion.getBlueprintUuid())) {
            throw new BadRequestException("Missing Blueprint on BlueprintVersion");
        }
        if (!StringUtils.hasText(blueprintVersion.getName())) {
            throw new BadRequestException("Missing Blueprint Version name");
        }
        if (!StringUtils.hasText(blueprintVersion.getVersionNumber())) {
            throw new BadRequestException("Missing Blueprint Version version number");
        }
        if (blueprintVersion.getContent() == null) {
            throw new BadRequestException("Missing Blueprint Version content");
        }
    }

    private void validateFieldConstraints(BlueprintVersion blueprintVersion) {
        validateLength("Name", blueprintVersion.getName(), 255);
        validateLength("Description", blueprintVersion.getDescription(), 10000);
        validateLength("Tag", blueprintVersion.getTag(), 255);
        validateLength("Spec", blueprintVersion.getSpec(), 255);
        validateLength("Spec version", blueprintVersion.getSpecVersion(), 255);
        validateLength("Version number", blueprintVersion.getVersionNumber(), 255);
    }

    private void validateLength(String fieldName, String value, int maxLength) {
        if (StringUtils.hasText(value) && value.length() > maxLength) {
            throw new BadRequestException(fieldName + " cannot exceed " + maxLength + " characters");
        }
    }

    @Override
    protected void reconcile(BlueprintVersion blueprintVersion) {
        blueprintVersion.setBlueprint(
                blueprintService.findOne(blueprintVersion.getBlueprintUuid())
        );
    }

    @Override
    protected Specification<BlueprintVersion> getSpecFromFilters(BlueprintVersionSearchOptions searchOptions) {
        return (root, query, cb) -> cb.conjunction();
    }

    @Override
    protected PagingAndSortingAndSpecificationExecutorRepository<BlueprintVersion, String> getRepository() {
        return repository;
    }

    @Override
    protected BlueprintVersionRes toRes(BlueprintVersion entity) {
        return mapper.toRes(entity);
    }

    @Override
    protected void beforeCreation(BlueprintVersion blueprintVersion) {
        validateNaturalKeyConstraints(blueprintVersion, null);
    }

    @Override
    protected void beforeOverwrite(BlueprintVersion blueprintVersion) {
        validateNaturalKeyConstraints(blueprintVersion, blueprintVersion.getUuid());
    }

    private void validateNaturalKeyConstraints(BlueprintVersion blueprintVersion, String excludeUuid) {
        boolean existsByVersionNumber;
        if (StringUtils.hasText(excludeUuid)) {
            existsByVersionNumber = repository.existsByVersionNumberIgnoreCaseAndBlueprintUuidAndUuidNot(
                    blueprintVersion.getVersionNumber(),
                    blueprintVersion.getBlueprint().getUuid(),
                    excludeUuid);
        } else {
            existsByVersionNumber = repository.existsByVersionNumberIgnoreCaseAndBlueprintUuid(
                    blueprintVersion.getVersionNumber(),
                    blueprintVersion.getBlueprint().getUuid());
        }
        if (existsByVersionNumber) {
            throw new ResourceConflictException(
                    String.format("A blueprint version with version number '%s' already exists for this blueprint",
                            blueprintVersion.getVersionNumber()));
        }
    }

    @Override
    public BlueprintVersionRes overwriteResource(String uuid, BlueprintVersionRes resource) {
        resource.setUuid(uuid);
        return super.overwriteResource(uuid, resource);
    }
}
