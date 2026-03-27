package org.opendatamesh.platform.pp.blueprint.blueprint.services.usecases.register;

import org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.BlueprintRepo;
import org.opendatamesh.platform.pp.blueprint.exceptions.BadRequestException;

import java.net.URI;

/**
 * Semantic validation for blueprint registration (URLs, paths). Does not duplicate CRUD required/length rules.
 * Plain class — not a Spring bean; constructed by {@link RegisterBlueprintFactory}.
 */
class RegisterBlueprintSemanticValidationOutboundPortImpl implements RegisterBlueprintSemanticValidationOutboundPort {

    @Override
    public void validate(Blueprint blueprint) {
        if (blueprint == null || blueprint.getBlueprintRepo() == null) {
            return;
        }
        BlueprintRepo repo = blueprint.getBlueprintRepo();
        validateHttpUrl(repo.getRemoteUrlHttp(), "HTTP remote URL");
        validateSshUrl(repo.getRemoteUrlSsh(), "SSH remote URL");
        validateHttpUrl(repo.getProviderBaseUrl(), "Provider base URL");
        validatePath(repo.getManifestRootPath(), "Manifest root path");
        validatePath(repo.getDescriptorTemplatePath(), "Descriptor template path");
        validatePath(repo.getReadmePath(), "Readme path");
    }

    private void validateHttpUrl(String value, String fieldName) {
        if (!hasText(value)) {
            return;
        }
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new BadRequestException(fieldName + " must be a valid http(s) URL");
            }
            if (uri.getHost() == null && !hasText(uri.getAuthority())) {
                throw new BadRequestException(fieldName + " must be a valid http(s) URL");
            }
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(fieldName + " must be a valid http(s) URL");
        }
    }

    private void validateSshUrl(String value, String fieldName) {
        if (!hasText(value)) {
            return;
        }
        String t = value.trim();
        if (t.startsWith("git@")) {
            if (!t.contains(":")) {
                throw new BadRequestException(fieldName + " must be a valid git SSH URL");
            }
            return;
        }
        if (t.startsWith("ssh://")) {
            try {
                URI uri = URI.create(t);
                if (!"ssh".equalsIgnoreCase(uri.getScheme())) {
                    throw new BadRequestException(fieldName + " must be a valid SSH URL");
                }
            } catch (IllegalArgumentException e) {
                throw new BadRequestException(fieldName + " must be a valid SSH URL");
            }
            return;
        }
        throw new BadRequestException(fieldName + " must be a valid git SSH URL (git@...) or ssh:// URL");
    }

    private void validatePath(String value, String fieldName) {
        if (!hasText(value)) {
            return;
        }
        if (value.contains("..") || value.contains("\\")) {
            throw new BadRequestException(fieldName + " must not contain '..' or backslashes");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
