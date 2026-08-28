package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.publish;

import com.fasterxml.jackson.databind.JsonNode;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersionShort;
import org.opendatamesh.platform.pp.blueprint.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.blueprint.exceptions.ResourceConflictException;
import org.opendatamesh.platform.pp.blueprint.utils.usecases.TransactionalOutboundPort;
import org.opendatamesh.platform.pp.blueprint.utils.usecases.UseCase;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

class PublishBlueprintVersion implements UseCase {

    private final PublishBlueprintVersionCommand command;
    private final PublishBlueprintVersionPresenter presenter;
    private final PublishBlueprintVersionManifestOutboundPort manifestOutboundPort;
    private final PublishBlueprintVersionPersistenceOutboundPort blueprintVersionPersistencePort;
    private final PublishBlueprintPersistenceOutboundPort blueprintPersistenceOutboundPort;
    private final TransactionalOutboundPort transactionalOutboundPort;

    PublishBlueprintVersion(
            PublishBlueprintVersionCommand command,
            PublishBlueprintVersionPresenter presenter,
            PublishBlueprintVersionManifestOutboundPort manifestOutboundPort,
            PublishBlueprintVersionPersistenceOutboundPort blueprintVersionPersistencePort,
            PublishBlueprintPersistenceOutboundPort blueprintPersistenceOutboundPort,
            TransactionalOutboundPort transactionalOutboundPort) {
        this.command = command;
        this.presenter = presenter;
        this.manifestOutboundPort = manifestOutboundPort;
        this.blueprintVersionPersistencePort = blueprintVersionPersistencePort;
        this.blueprintPersistenceOutboundPort = blueprintPersistenceOutboundPort;
        this.transactionalOutboundPort = transactionalOutboundPort;
    }

    @Override
    public void execute() {
        BlueprintVersion blueprintVersion = command.blueprintVersion();
        blueprintVersion.setUuid(null);

        transactionalOutboundPort.doInTransaction(() -> {

            Blueprint blueprint = blueprintPersistenceOutboundPort.findByUuidOrName(blueprintVersion.getBlueprintUuid(), blueprintVersion.getBlueprint().getName());
            blueprintVersion.setBlueprint(blueprint);
            validateExistingReadme(blueprint, blueprintVersion);

            JsonNode filled = manifestOutboundPort.autofillManifest(blueprintVersion.getSpec(), blueprintVersion.getSpecVersion(), blueprintVersion.getContent(), blueprint.getName());
            blueprintVersion.setContent(filled);
            manifestOutboundPort.validateManifest(blueprintVersion.getSpec(), blueprintVersion.getSpecVersion(), blueprintVersion.getContent());
            validateCompositionModules(blueprintVersion);

            String versionNumber = manifestOutboundPort.extractVersionNumber(blueprintVersion.getContent());
            String specNumber = manifestOutboundPort.extractSpecNumber(blueprintVersion.getContent());
            String specVersion = manifestOutboundPort.extractSpecVersion(blueprintVersion.getContent());

            blueprintVersion.setVersionNumber(versionNumber);
            blueprintVersion.setSpec(specNumber);
            blueprintVersion.setSpecVersion(specVersion);

            Optional<BlueprintVersionShort> existentBlueprintVersion = blueprintVersionPersistencePort.findByBlueprintUuidAndVersionNumber(blueprintVersion.getBlueprintUuid(), blueprintVersion.getVersionNumber());
            if (existentBlueprintVersion.isPresent()) {
                throw new ResourceConflictException("Impossible to publish a Blueprint version already existent");
            }
            BlueprintVersion created = blueprintVersionPersistencePort.createBlueprintVersion(blueprintVersion);
            presenter.presentPublished(created);
        });
    }

    private void validateExistingReadme(Blueprint blueprint, BlueprintVersion blueprintVersion) {
        if (blueprint.getBlueprintRepo() != null && StringUtils.hasText(blueprint.getBlueprintRepo().getReadmePath()) && !StringUtils.hasText(blueprintVersion.getReadme())) {
            throw new BadRequestException("Readme is required to publish a blueprint version if readme path is specified");
        }
    }

    private void validateCompositionModules(BlueprintVersion parentVersion) {
        List<String> issues = new ArrayList<>();
        for (PublishCompositionIdentity composition : manifestOutboundPort.listCompositionIdentities(parentVersion.getContent())) {
            BlueprintVersion moduleVersion = blueprintVersionPersistencePort.findModuleBlueprintVersion(composition.blueprintName(), composition.blueprintVersion());
            //For now the only supported modules are the ones from monorepo no-composition blueprints
            if (!manifestOutboundPort.isMonorepoNoComposition(moduleVersion.getContent())) {
                issues.add(formatModuleIssue(
                        composition,
                        "Composition module '%s' (%s@%s) is not a monorepo with no composition"
                                .formatted(
                                        composition.moduleAlias(),
                                        composition.blueprintName(),
                                        composition.blueprintVersion()),
                        "Composition modules must be monorepo with no composition "
                                + "(one repository key, empty composition)."));
            }
            if (hasDescriptorTemplatePath(moduleVersion)) {
                issues.add(formatModuleIssue(
                        composition,
                        "Composition module '%s' (%s@%s) declares descriptorTemplatePath"
                                .formatted(
                                        composition.moduleAlias(),
                                        composition.blueprintName(),
                                        composition.blueprintVersion()),
                        "Only the parent (root) blueprint may have descriptorTemplatePath; remove it from the module."));
            }
            collectMissingModuleParameterMappings(parentVersion, composition, moduleVersion, issues);
        }

        if (!issues.isEmpty()) {
            throw new BadRequestException("Manifest blueprint validation failed:\n  "
                    + String.join("\n  ", issues));
        }
    }

    private void collectMissingModuleParameterMappings(
            BlueprintVersion parentVersion,
            PublishCompositionIdentity composition,
            BlueprintVersion moduleVersion,
            List<String> issues
    ) {
        Set<String> mappedKeys = new HashSet<>(manifestOutboundPort.listMappedChildParameterKeys(
                parentVersion.getContent(), composition.fieldPath()));
        for (String childKey : manifestOutboundPort.listModuleParameterKeysWithoutDefault(moduleVersion.getContent())) {
            if (mappedKeys.contains(childKey)) {
                continue;
            }
            issues.add(formatModuleIssue(
                    composition.fieldPath() + ".parameterMapping." + childKey,
                    "Composition module '%s' (%s@%s) is missing a mapping for parameter '%s' that has no default"
                            .formatted(
                                    composition.moduleAlias(),
                                    composition.blueprintName(),
                                    composition.blueprintVersion(),
                                    childKey),
                    "Add a parameterMapping entry for this child key ({ $param: parentKey } or { value: actualValue }), "
                            + "or declare a default on the module parameter."));
        }
    }

    private String formatModuleIssue(
            PublishCompositionIdentity composition,
            String problem,
            String hint
    ) {
        return formatModuleIssue(composition.fieldPath(), problem, hint);
    }

    private String formatModuleIssue(String fieldPath, String problem, String hint) {
        return "%s: %s. Hint: %s".formatted(fieldPath, problem, hint);
    }

    private boolean hasDescriptorTemplatePath(BlueprintVersion version) {
        if (version == null || version.getBlueprint() == null || version.getBlueprint().getBlueprintRepo() == null) {
            return false;
        }
        return StringUtils.hasText(version.getBlueprint().getBlueprintRepo().getDescriptorTemplatePath());
    }
}
