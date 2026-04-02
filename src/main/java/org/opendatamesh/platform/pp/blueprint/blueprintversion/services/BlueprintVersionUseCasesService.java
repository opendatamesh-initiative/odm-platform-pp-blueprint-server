package org.opendatamesh.platform.pp.blueprint.blueprintversion.services;

import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate.*;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.publish.PublishBlueprintVersionFactory;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.BlueprintVersionMapper;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.publish.PublishBlueprintVersionResponseRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.publish.PublishBlueprintVersionCommandRes;
import org.opendatamesh.platform.pp.blueprint.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.publish.PublishBlueprintVersionCommand;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.publish.PublishBlueprintVersionPresenter;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.BlueprintVersionRes;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.instantiate.InstantiateBlueprintVersionCommandRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.instantiate.InstantiateBlueprintVersionResponseRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.instantiate.InstantiateBlueprintVersionTargetRepositoryRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.gitproviders.RepositoryMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class BlueprintVersionUseCasesService {

    private final InstantiateBlueprintVersionFactory instantiateBlueprintVersionFactory;
    private final RepositoryMapper repositoryMapper;
    private final PublishBlueprintVersionFactory publishBlueprintVersionFactory;
    private final BlueprintVersionMapper blueprintVersionMapper;
    private final ObjectMapper objectMapper;

    public BlueprintVersionUseCasesService(
            InstantiateBlueprintVersionFactory instantiateBlueprintVersionFactory,
            RepositoryMapper repositoryMapper) {
        this.instantiateBlueprintVersionFactory = instantiateBlueprintVersionFactory;
        this.repositoryMapper = repositoryMapper;
        PublishBlueprintVersionFactory publishBlueprintVersionFactory,
        BlueprintVersionMapper blueprintVersionMapper,
        ObjectMapper objectMapper
    ) {
        this.publishBlueprintVersionFactory = publishBlueprintVersionFactory;
        this.blueprintVersionMapper = blueprintVersionMapper;
        this.objectMapper = objectMapper;
    }

    public InstantiateBlueprintVersionResponseRes instantiateBlueprintVersion(
            InstantiateBlueprintVersionCommandRes command,
            HttpHeaders headers) {

        if (command.getTargetRepositories() == null || command.getTargetRepositories().isEmpty()) {
            throw new BadRequestException("At least one target repository is required");
        }
        InstantiateBlueprintVersionTargetRepositoryRes targetRes = command.getTargetRepositories().getFirst();
        InstantiateBlueprintVersionCommand domainCommand = mapResToInternalCommand(command, headers, targetRes);

    public PublishBlueprintVersionResponseRes publishBlueprintVersion(PublishBlueprintVersionCommandRes command) {
        validateCommand(command);

        BlueprintVersionRes res = objectMapper.convertValue(
            command.getBlueprintVersion(),
            BlueprintVersionRes.class);
        BlueprintVersion blueprintVersion = blueprintVersionMapper.toEntity(res);

        InstantiateResultHolder presenter = new InstantiateResultHolder();
        instantiateBlueprintVersionFactory.buildInstantiateBlueprintVersion(domainCommand, presenter, headers)
                .execute();
        InstantiateBlueprintVersionResult result = presenter.getResult();
        return new InstantiateBlueprintVersionResponseRes();
        PublishBlueprintVersionCommand domainCommand = new PublishBlueprintVersionCommand(blueprintVersion);
        ResultHolder presenter = new ResultHolder();
        publishBlueprintVersionFactory.buildPublishBlueprintVersion(domainCommand, presenter).execute();
        PublishBlueprintVersionResponseRes response = new PublishBlueprintVersionResponseRes();
        response.setBlueprintVersion(blueprintVersionMapper.toRes(presenter.getResult()));
        return response;
    }

    private InstantiateBlueprintVersionCommand mapResToInternalCommand(
            InstantiateBlueprintVersionCommandRes command,
            HttpHeaders headers,
            InstantiateBlueprintVersionTargetRepositoryRes targetRes) {
        return new InstantiateBlueprintVersionCommand(
                command.getBlueprintName(),
                command.getBlueprintVersionNumber(),
                command.getTargetRepositories().stream()
                        .map(res -> new TargetRepositoryDto(null, res.getType(), targetRes.getBranch(),
                                repositoryMapper.toEntity(targetRes.getRepository())))
                        .toList(),
                command.getParameters() == null ? Map.of() : new LinkedHashMap<>(command.getParameters()),
                toHeaderMap(headers),
                command.getCommitAuthorName(),
                command.getCommitAuthorEmail());
    }
    private static final class ResultHolder implements PublishBlueprintVersionPresenter {

        private BlueprintVersion result;
    private Map<String, String> toHeaderMap(HttpHeaders headers) {
        Map<String, String> headerMap = new LinkedHashMap<>();
        if (headers == null) {
            return headerMap;
        }
        headers.forEach((name, values) -> {
            if (!values.isEmpty() && StringUtils.hasText(values.getFirst())) {
                headerMap.put(name, values.getFirst());
            }
        });
        return headerMap;
    }

    private static final class InstantiateResultHolder implements InstantiateBlueprintVersionPresenter {

        private InstantiateBlueprintVersionResult result;

        @Override
        public void presentPublished(BlueprintVersion blueprintVersion) {
            this.result = blueprintVersion;
        public void presentResults(InstantiateBlueprintVersionResult result) {
            this.result = result;
        }

        BlueprintVersion getResult() {
        InstantiateBlueprintVersionResult getResult() {
            return result;
        }
    }

    private void validateCommand(PublishBlueprintVersionCommandRes command) {
        if (command == null || command.getBlueprintVersion() == null) {
            throw new BadRequestException("Blueprint version is required");
        }
        if (!StringUtils.hasText(command.getBlueprintVersion().getBlueprint().getName()) && !StringUtils.hasText(command.getBlueprintVersion().getBlueprint().getUuid())) {
            throw new BadRequestException("Blueprint name or uuid is required to publish a blueprint version");
        }
    }
}
