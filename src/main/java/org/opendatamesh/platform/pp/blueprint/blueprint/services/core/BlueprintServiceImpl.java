package org.opendatamesh.platform.pp.blueprint.blueprint.services.core;

import org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.BlueprintType;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.BlueprintRepo;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.BlueprintRepoOwnerType;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.BlueprintRepoProviderType;
import org.opendatamesh.platform.pp.blueprint.blueprint.repositories.BlueprintsRepository;
import org.opendatamesh.platform.pp.blueprint.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.blueprint.exceptions.ResourceConflictException;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintTypeRes;
import org.opendatamesh.platform.pp.blueprint.label.entities.Label;
import org.opendatamesh.platform.pp.blueprint.label.repositories.LabelsRepository;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintMapper;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintSearchOptions;
import org.opendatamesh.platform.pp.blueprint.utils.repositories.PagingAndSortingAndSpecificationExecutorRepository;
import org.opendatamesh.platform.pp.blueprint.utils.repositories.SpecsUtils;
import org.opendatamesh.platform.pp.blueprint.utils.services.GenericMappedAndFilteredCrudServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class BlueprintServiceImpl extends GenericMappedAndFilteredCrudServiceImpl<BlueprintSearchOptions, BlueprintRes, Blueprint, String> implements BlueprintService {

    private final BlueprintMapper mapper;
    private final BlueprintsRepository repository;
    private final LabelsRepository labelsRepository;

    @Autowired
    public BlueprintServiceImpl(BlueprintMapper mapper, BlueprintsRepository repository, LabelsRepository labelsRepository) {
        this.mapper = mapper;
        this.repository = repository;
        this.labelsRepository = labelsRepository;
    }

    @Override
    protected PagingAndSortingAndSpecificationExecutorRepository<Blueprint, String> getRepository() {
        return repository;
    }

    @Override
    protected Specification<Blueprint> getSpecFromFilters(BlueprintSearchOptions filters) {
        List<Specification<Blueprint>> specs = new ArrayList<>();
        if (filters != null) {
            if (StringUtils.hasText(filters.getUuid())) {
                specs.add(BlueprintsRepository.Specs.hasUuid(filters.getUuid()));
            }
            if (StringUtils.hasText(filters.getName())) {
                specs.add(BlueprintsRepository.Specs.hasName(filters.getName()));
            }
            if (filters.getBlueprintType() != null) {
                specs.add(BlueprintsRepository.Specs.hasBlueprintType(BlueprintType.valueOf(filters.getBlueprintType().name())));
            }
            if (!CollectionUtils.isEmpty(filters.getLabelUuids())) {
                specs.add(BlueprintsRepository.Specs.hasAnyLabelUuid(filters.getLabelUuids()));
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
        validateBlueprintTypeAndDescriptorPath(objectToValidate);
        if (objectToValidate.getBlueprintRepo() != null) {
            validateBlueprintRepo(objectToValidate.getBlueprintRepo());
        }
        validateLabels(objectToValidate);
    }

    private void validateRequiredFields(Blueprint blueprint) {
        validateRequired("Name", blueprint.getName());
        validateRequired("Display name", blueprint.getDisplayName());
        if (blueprint.getBlueprintType() == null) {
            throw new BadRequestException("Blueprint type is required");
        }
    }

    private void validateFieldConstraints(Blueprint blueprint) {
        validateLength("Name", blueprint.getName(), 255);
        validateLength("Display name", blueprint.getDisplayName(), 255);
    }

    private void validateBlueprintTypeAndDescriptorPath(Blueprint blueprint) {
        if (blueprint.getBlueprintType() == null) {
            return;
        }
        if (blueprint.getBlueprintRepo() == null) {
            throw new BadRequestException("Blueprint repository is required");
        }
        boolean hasDescriptorPath = StringUtils.hasText(blueprint.getBlueprintRepo().getDescriptorTemplatePath());
        if (blueprint.getBlueprintType() == BlueprintType.BLUEPRINT && !hasDescriptorPath) {
            throw new BadRequestException("Descriptor template path is required for a Blueprint");
        }
        if (blueprint.getBlueprintType() == BlueprintType.MODULE && hasDescriptorPath) {
            throw new BadRequestException(
                    "A Blueprint module must not have descriptorTemplatePath; remove it from the module.");
        }
    }

    private void validateBlueprintRepo(BlueprintRepo blueprintRepo) {
        if (blueprintRepo == null) {
            return;
        }

        validateRequired("Repository name", blueprintRepo.getName());
        validateRequired("External identifier", blueprintRepo.getExternalIdentifier());
        validateRequired("Manifest root path", blueprintRepo.getManifestRootPath());
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

        validateLength("Repository name", blueprintRepo.getName(), 255);
        validateLength("External identifier", blueprintRepo.getExternalIdentifier(), 255);
        validateLength("Default branch", blueprintRepo.getDefaultBranch(), 255);
        validateLength("Manifest root path", blueprintRepo.getManifestRootPath(), 500);
        if (StringUtils.hasText(blueprintRepo.getDescriptorTemplatePath())) {
            validateLength("Descriptor template path", blueprintRepo.getDescriptorTemplatePath(), 500);
        }
        if (StringUtils.hasText(blueprintRepo.getReadmePath())) {
            validateLength("Readme path", blueprintRepo.getReadmePath(), 500);
        }
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
        reconcileLabels(objectToReconcile);
    }

    private void reconcileBlueprintRepo(BlueprintRepo blueprintRepo, Blueprint parentBlueprint) {
        blueprintRepo.setBlueprint(parentBlueprint);
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
        if (resource.getBlueprintType() == null) {
            Blueprint stored = findOne(uuid);
            if (stored.getBlueprintType() != null) {
                resource.setBlueprintType(BlueprintTypeRes.valueOf(stored.getBlueprintType().name()));
            }
        }
        return super.overwriteResource(uuid, resource);
    }

    private void validateLabels(Blueprint blueprint) {
        if (blueprint.getLabels() == null) {
            return;
        }
        Set<String> seenUuids = new HashSet<>();
        for (Label label : blueprint.getLabels()) {
            if (label == null || !StringUtils.hasText(label.getUuid())) {
                throw new BadRequestException("Label uuid is required");
            }
            if (!seenUuids.add(label.getUuid())) {
                throw new BadRequestException("A blueprint cannot have the same label twice");
            }
        }
    }

    private void reconcileLabels(Blueprint blueprint) {
        if (blueprint.getLabels() == null) {
            blueprint.setLabels(new HashSet<>());
            return;
        }
        Set<Label> managedLabels = new HashSet<>();
        for (Label label : blueprint.getLabels()) {
            String uuid = label.getUuid();
            Label managed = labelsRepository.findById(uuid)
                    .orElseThrow(() -> new BadRequestException("Unknown label uuid: " + uuid));
            managedLabels.add(managed);
        }
        blueprint.setLabels(managedLabels);
    }

    private void validateNaturalKeyConstraints(Blueprint blueprint, String excludeUuid) {
        boolean existsByName;
        if (StringUtils.hasText(excludeUuid)) {
            existsByName = repository.existsByNameIgnoreCaseAndUuidNot(blueprint.getName(), excludeUuid);
        } else {
            existsByName = repository.existsByNameIgnoreCase(blueprint.getName());
        }
        if (existsByName) {
            throw new ResourceConflictException("A blueprint with name '" + blueprint.getName() + "' already exists");
        }
    }
}
