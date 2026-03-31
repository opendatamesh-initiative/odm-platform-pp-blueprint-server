package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.publish;

import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.repositories.BlueprintVersionsRepository;
import org.opendatamesh.platform.pp.blueprint.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.blueprint.exceptions.ResourceConflictException;
import org.opendatamesh.platform.pp.blueprint.manifest.model.Manifest;
import org.springframework.util.StringUtils;
import java.util.regex.Pattern;

class PublishBlueprintVersionSemanticOutboundPortImpl implements PublishBlueprintVersionSemanticOutboundPort {

    private static final Pattern SEMVER = Pattern.compile(
        "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)"
                + "(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?"
                + "(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$"
);

    private final BlueprintVersionsRepository blueprintVersionsRepository;

    PublishBlueprintVersionSemanticOutboundPortImpl(BlueprintVersionsRepository blueprintVersionsRepository) {
        this.blueprintVersionsRepository = blueprintVersionsRepository;
    }

    @Override
    public void verifySpecAndSpecVersion(BlueprintVersion blueprintVersion) {
        if (!Manifest.SPEC_NAME.equals(blueprintVersion.getSpec())) {
            throw new BadRequestException("Blueprint version spec must be '" + Manifest.SPEC_NAME + "'");
        }
        if (!StringUtils.hasText(blueprintVersion.getSpecVersion())
                || !SEMVER.matcher(blueprintVersion.getSpecVersion().trim()).matches()) {
            throw new BadRequestException("Invalid blueprint version specVersion");
        }
    }

    @Override
    public void verifyNoDuplicateNameAndTag(BlueprintVersion blueprintVersion) {
        if (!StringUtils.hasText(blueprintVersion.getTag())) {
            return;
        }
        String blueprintUuid = blueprintVersion.getBlueprint().getUuid();
        if (blueprintVersionsRepository.existsByBlueprint_UuidAndNameIgnoreCaseAndTagIgnoreCase(
                blueprintUuid,
                blueprintVersion.getName(),
                blueprintVersion.getTag())) {
            throw new ResourceConflictException(
                    "A blueprint version with the same name and tag already exists for this blueprint");
        }
    }
}
