package org.opendatamesh.platform.pp.blueprint.blueprint.services.core;

import org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.BlueprintRepo;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.BlueprintRepoOwnerType;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.BlueprintRepoProviderType;
import org.opendatamesh.platform.pp.blueprint.blueprint.repositories.BlueprintsRepository;
import org.opendatamesh.platform.pp.blueprint.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.blueprint.exceptions.ResourceConflictException;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintMapper;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintSearchOptions;
import org.opendatamesh.platform.pp.blueprint.utils.repositories.PagingAndSortingAndSpecificationExecutorRepository;
import org.opendatamesh.platform.pp.blueprint.utils.repositories.SpecsUtils;
import org.opendatamesh.platform.pp.blueprint.utils.services.GenericMappedAndFilteredCrudServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class BlueprintServiceImpl extends GenericMappedAndFilteredCrudServiceImpl<BlueprintSearchOptions, BlueprintRes, Blueprint, String> implements BlueprintService {

    private final BlueprintMapper mapper;
    private final BlueprintsRepository repository;

    @Autowired
    public BlueprintServiceImpl(BlueprintMapper mapper, BlueprintsRepository repository) {
        this.mapper = mapper;
        this.repository = repository;
    }

    @Override
    protected PagingAndSortingAndSpecificationExecutorRepository<Blueprint, String> getRepository() {
        return repository;
    }

    @Override
    protected Specification<Blueprint> getSpecFromFilters(BlueprintSearchOptions filters) {
        List<Specification<Blueprint>> specs = new ArrayList<>();
        if (filters != null) {
            if (StringUtils.hasText(filters.getName())){
                specs.add(BlueprintsRepository.Specs.hasName(filters.getName()));
            }
        }
        return SpecsUtils.combineWithAnd(specs);
    }

    @Override
    protected BlueprintRes toRes(Blueprint entity) {
        return mapper.toRes(entity);
    }

    @Override
    protected Blueprint toEntity(BlueprintRes resource) {
        return mapper.toEntity(resource);
    }

    @Override
    protected void validate(Blueprint objectToValidate) {
        if (objectToValidate == null) {
            throw new BadRequestException("Blueprint cannot be null");
        }
        validateRequiredFields(objectToValidate);
        validateFieldConstraints(objectToValidate);
        if (objectToValidate.getBlueprintRepo() != null) {
            validateBlueprintRepo(objectToValidate.getBlueprintRepo());
        }
    }

    private void validateRequiredFields(Blueprint blueprint) {
        validateRequired("Name", blueprint.getName());
        validateRequired("Display name", blueprint.getDisplayName());
        validateRequired("Description", blueprint.getDescription());
    }

    private void validateFieldConstraints(Blueprint blueprint) {
        validateLength("Name", blueprint.getName(), 255);
        validateLength("Display name", blueprint.getDisplayName(), 255);
    }

    private void validateBlueprintRepo(BlueprintRepo blueprintRepo) {
        if (blueprintRepo == null) return;

        // Required fields (except description)
        validateRequired("Repository name", blueprintRepo.getName());
        validateRequired("External identifier", blueprintRepo.getExternalIdentifier());
        validateRequired("Manifest root path", blueprintRepo.getManifestRootPath());
        validateRequired("Descriptor template path", blueprintRepo.getDescriptorTemplatePath());
        validateRequired("HTTP remote URL", blueprintRepo.getRemoteUrlHttp());
        validateRequired("SSH remote URL", blueprintRepo.getRemoteUrlSsh());
        validateRequired("Default branch", blueprintRepo.getDefaultBranch());
        validateRequired("Provider base URL", blueprintRepo.getProviderBaseUrl());
        validateRequired("Owner ID", blueprintRepo.getOwnerId());

        if (blueprintRepo.getProviderType() == null) {
            throw new BadRequestException("Provider type is required");
        }
        try {
            BlueprintRepoProviderType.fromString(blueprintRepo.getProviderType().name());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid provider type: " + blueprintRepo.getProviderType());
        }

        if (blueprintRepo.getOwnerType() == null) {
            throw new BadRequestException("Owner type is required");
        }
        try {
            BlueprintRepoOwnerType.fromString(blueprintRepo.getOwnerType().name());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid owner type: " + blueprintRepo.getOwnerType());
        }

        // Length constraints
        validateLength("Repository name", blueprintRepo.getName(), 255);
        validateLength("External identifier", blueprintRepo.getExternalIdentifier(), 255);
        validateLength("Default branch", blueprintRepo.getDefaultBranch(), 255);
        validateLength("Manifest root path", blueprintRepo.getManifestRootPath(), 500);
        validateLength("Descriptor template path", blueprintRepo.getDescriptorTemplatePath(), 500);
        validateLength("HTTP remote URL", blueprintRepo.getRemoteUrlHttp(), 500);
        validateLength("SSH remote URL", blueprintRepo.getRemoteUrlSsh(), 500);
        validateLength("Provider base URL", blueprintRepo.getProviderBaseUrl(), 500);
        validateLength("Owner ID", blueprintRepo.getOwnerId(), 255);
    }

    private void validateRequired(String fieldName, String value) {
        if (!StringUtils.hasText(value)) {
            throw new BadRequestException(fieldName + " is required");
        }
    }

    private void validateLength(String fieldName, String value, int maxLength) {
        if (StringUtils.hasText(value) && value.length() > maxLength) {
            throw new BadRequestException(fieldName + " cannot exceed " + maxLength + " characters");
        }
    }

    @Override
    protected void reconcile(Blueprint objectToReconcile) {
        if (objectToReconcile == null) {
            return;
        }
        if (objectToReconcile.getBlueprintRepo() != null) {
            reconcileBlueprintRepo(objectToReconcile.getBlueprintRepo(), objectToReconcile);
        }
    }

    private void reconcileBlueprintRepo(BlueprintRepo blueprintRepo, Blueprint parentBlueprint) {
        // Set the parent Blueprint reference
        blueprintRepo.setBlueprint(parentBlueprint);
        // Set the blueprintUuid to maintain consistency
        if (parentBlueprint.getUuid() != null) {
            blueprintRepo.setBlueprintUuid(parentBlueprint.getUuid());
        }
    }

    @Override
    protected void beforeCreation(Blueprint objectToCreate) {
        validateNaturalKeyConstraints(objectToCreate, null);
    }

    @Override
    protected void beforeOverwrite(Blueprint objectToOverwrite) {
        validateNaturalKeyConstraints(objectToOverwrite, objectToOverwrite.getUuid());
    }

    @Override
    public BlueprintRes overwriteResource(String uuid, BlueprintRes resource) {
        resource.setUuid(uuid);
        return super.overwriteResource(uuid, resource);
    }

    private void validateNaturalKeyConstraints(Blueprint blueprint, String excludeUuid) {
        // Validate name uniqueness
        boolean existsByName;
        if(StringUtils.hasText(excludeUuid)) {
            existsByName = repository.existsByNameIgnoreCaseAndUuidNot(blueprint.getName(), excludeUuid);
        } else {
            existsByName = repository.existsByNameIgnoreCase(blueprint.getName());
        }
        if (existsByName) {
            throw new ResourceConflictException("A blueprint with name '" + blueprint.getName() + "' already exists");
        }
    }
}
