package org.opendatamesh.platform.pp.blueprint.blueprint.services.usecases.updatedocumentationfields;

import org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.BlueprintType;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.BlueprintRepo;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.BlueprintRepoOwnerType;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.BlueprintRepoProviderType;
import org.opendatamesh.platform.pp.blueprint.exceptions.BadRequestException;
import org.springframework.util.StringUtils;

/**
 * Structural validation aligned with {@link org.opendatamesh.platform.pp.blueprint.blueprint.services.core.BlueprintServiceImpl}
 */
class UpdateBlueprintDocumentationFieldsStructuralValidationOutboundPortImpl implements UpdateBlueprintDocumentationFieldsStructuralValidationOutboundPort {

    @Override
    public void validate(Blueprint blueprint) {
        if (blueprint == null) {
            throw new BadRequestException("Blueprint cannot be null");
        }
        validateRequiredFields(blueprint);
        validateFieldConstraints(blueprint);
        validateBlueprintTypeAndDescriptorPath(blueprint);
        if (blueprint.getBlueprintRepo() != null) {
            validateBlueprintRepo(blueprint.getBlueprintRepo());
        }
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
}
