package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate;

import com.fasterxml.jackson.databind.JsonNode;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.blueprint.exceptions.InternalException;
import org.opendatamesh.platform.pp.blueprint.manifest.model.Manifest;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestParameter;
import org.opendatamesh.platform.pp.blueprint.manifest.parser.ManifestParserFactory;
import org.opendatamesh.platform.pp.blueprint.utils.usecases.UseCase;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class InstantiateBlueprintVersion implements UseCase {

    private final InstantiateBlueprintVersionCommand command;
    private final InstantiateBlueprintVersionPresenter presenter;
    private final InstantiateBlueprintVersionPersistencyOutboundPort persistencyPort;
    private final InstantiateBlueprintVersionManifestOutboundPort manifestPort;
    private final InstantiateBlueprintVersionTemplatingOutboundPort templatingPort;
    private final InstantiateBlueprintVersionGitOutboundPort gitPort;
    private final BlueprintDataProductDescriptorService blueprintDataProductDescriptorService;

    InstantiateBlueprintVersion(
            InstantiateBlueprintVersionCommand command,
            InstantiateBlueprintVersionPresenter presenter,
            InstantiateBlueprintVersionPersistencyOutboundPort persistencyPort,
            InstantiateBlueprintVersionManifestOutboundPort manifestPort,
            InstantiateBlueprintVersionTemplatingOutboundPort templatingPort,
            InstantiateBlueprintVersionGitOutboundPort gitPort,
            BlueprintDataProductDescriptorService blueprintDataProductDescriptorService
    ) {
        this.command = command;
        this.presenter = presenter;
        this.persistencyPort = persistencyPort;
        this.manifestPort = manifestPort;
        this.templatingPort = templatingPort;
        this.gitPort = gitPort;
        this.blueprintDataProductDescriptorService = blueprintDataProductDescriptorService;
    }

    @Override
    public void execute() {
        validateCommand(command);
        BlueprintVersion blueprintVersion = persistencyPort.findByBlueprintNameAndVersion(command.blueprintName(), command.blueprintVersion());
        manifestPort.validateManifestAndParameters(blueprintVersion.getSpec(), blueprintVersion.getSpecVersion(), blueprintVersion.getContent(), command.blueprintParameters());
        //Source repositories are retrieved from the manifest,which contains pointer to other blueprint versions.
        //This enables to have [clone Url + tag] for cloning.
        List<SourceRepositoryDto> sourceRepositories = manifestPort.retrieveAllSourceRepositories(blueprintVersion, blueprintVersion.getContent());
        //Target repositories have [Id, Type] which allows them to be identified and handled by the manifest logics
        manifestPort.validateTargetRepositories(blueprintVersion, command.targetRepositories());
        //Git Operation initialization after retrieving the blueprint, used to set Git Provider and Git Provider base url
        gitPort.init(blueprintVersion.getBlueprint());

        Manifest manifest = parseManifest(blueprintVersion);
        Map<String, JsonNode> resolvedParameters = mergeParametersForLineage(manifest, command.blueprintParameters());

        gitPort.cloneRepositories(sourceRepositories, command.targetRepositories(),
                (sourceRepositoriesPaths, targetRepositoryPaths) -> {
                    Map<SourceRepositoryDto, Path> sourceRepositoriesPathsMap = buildSourceRepositoriesMap(sourceRepositoriesPaths, sourceRepositories);
                    Map<TargetRepositoryDto, Path> targetRepositoryPathsMap = buildTargetRepositoriesMap(targetRepositoryPaths);

                    templatingPort.renderAndCopy(
                            blueprintVersion,
                            command.blueprintParameters(),
                            sourceRepositoriesPathsMap,
                            targetRepositoryPathsMap
                    );

                    Path rootTargetPath = resolveRootTargetPath(targetRepositoryPaths, command.targetRepositories());
                    blueprintDataProductDescriptorService.enrichDescriptorWithBlueprintMetadata(
                            rootTargetPath,
                            blueprintVersion,
                            resolvedParameters);

                    for (Path targetRepositoryPath : targetRepositoryPaths) {
                        gitPort.commitAndPush(
                                targetRepositoryPath,
                                "Populate repository from blueprint " + command.blueprintName() + "@" + command.blueprintVersion(),
                                command.commitAuthorName(),
                                command.commitAuthorEmail()
                        );
                    }
                }
        );
        presenter.presentResults(new InstantiateBlueprintVersionResult());
    }

    private Map<TargetRepositoryDto, Path> buildTargetRepositoriesMap(List<Path> targetRepositoryPaths) {
        Map<TargetRepositoryDto, Path> targetRepositoryPathsMap = new HashMap<>();
        for (int i = 0; i < targetRepositoryPaths.size(); i++) {
            targetRepositoryPathsMap.put(command.targetRepositories().get(i), targetRepositoryPaths.get(i));
        }
        return targetRepositoryPathsMap;
    }

    // Retrieve the root target path from the list of target repository paths
    private Path resolveRootTargetPath(List<Path> targetRepositoryPaths, List<TargetRepositoryDto> targets) {
        for (int i = 0; i < targets.size(); i++) {
            if (targets.get(i).type() == BlueprintRepositoryLogicalType.ROOT) {
                return targetRepositoryPaths.get(i);
            }
        }
        throw new InternalException(
                "Blueprint instantiation requires a target repository with type 'root'; none found in command targets");
    }

    private Map<SourceRepositoryDto, Path> buildSourceRepositoriesMap(List<Path> sourceRepositoriesPaths, List<SourceRepositoryDto> sourceRepositories) {
        Map<SourceRepositoryDto, Path> sourceRepositoriesPathsMap = new HashMap<>();
        for (int i = 0; i < sourceRepositoriesPaths.size(); i++) {
            sourceRepositoriesPathsMap.put(sourceRepositories.get(i), sourceRepositoriesPaths.get(i));
        }
        return sourceRepositoriesPathsMap;
    }

    private void validateCommand(InstantiateBlueprintVersionCommand command) {
        if (command == null) {
            throw new BadRequestException("Instantiate command is required");
        }
        if (!StringUtils.hasText(command.blueprintName())) {
            throw new BadRequestException("Blueprint name is required");
        }
        if (!StringUtils.hasText(command.blueprintVersion())) {
            throw new BadRequestException("Blueprint version is required");
        }
        if (command.targetRepositories() == null || command.targetRepositories().isEmpty()) {
            throw new BadRequestException("At least one target repository is required");
        }
        if (command.blueprintParameters() == null) {
            throw new BadRequestException("Blueprint parameters are required");
        }
        if (command.authHeaders() == null) {
            throw new BadRequestException("Auth headers are required");
        }
        for (int i = 0; i < command.targetRepositories().size(); i++) {
            TargetRepositoryDto target = command.targetRepositories().get(i);
            if (target == null) {
                throw new BadRequestException("Target repository at index %s is required".formatted(i));
            }
            if (target.type() == null) {
                throw new BadRequestException("Target repository type is required at index %s".formatted(i));
            }
            if (target.repository() == null) {
                throw new BadRequestException("Target repository reference is required at index %s".formatted(i));
            }
        }
    }

    private Manifest parseManifest(BlueprintVersion blueprintVersion) {
        JsonNode raw = blueprintVersion.getContent();
        try {
            return ManifestParserFactory.getParser().deserialize(raw);
        } catch (IOException e) {
            throw new InternalException(
                    "Could not parse manifest content for blueprint version '%s' (versionNumber=%s)"
                            .formatted(blueprintVersion.getName(), blueprintVersion.getVersionNumber()),
                    e);
        }
    }

    private Map<String, JsonNode> mergeParametersForLineage(Manifest manifest,
            Map<String, JsonNode> requestParameters) {
        Map<String, JsonNode> out = new LinkedHashMap<>();
        if (manifest.getParameters() == null) {
            return out;
        }
        for (ManifestParameter p : manifest.getParameters()) {
            String key = p.getKey();
            JsonNode fromRequest = requestParameters.get(key);
            if (fromRequest != null && !fromRequest.isNull()) {
                out.put(key, fromRequest);
            } else if (p.getDefaultValue() != null && !p.getDefaultValue().isNull()) {
                out.put(key, p.getDefaultValue());
            }
        }
        return out;
    }
}
