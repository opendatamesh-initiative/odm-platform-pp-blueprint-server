package org.opendatamesh.platform.pp.blueprint.blueprintversion.services;

import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate.*;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.publish.PublishBlueprintVersionFactory;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.updatedataproduct.UpdateDataProductCommand;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.updatedataproduct.UpdateDataProductFromBlueprintVersionFactory;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.updatedataproduct.UpdateDataProductPresenter;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.updatedataproduct.UpdateDataProductResult;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.updatedataproduct.UpdateDataProductTargetRepositoryDto;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.updatedataproduct.UpdateDataProductTargetResult;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.updatedocumentationfields.UpdateBlueprintVersionDocumentationFieldsCommand;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.updatedocumentationfields.UpdateBlueprintVersionDocumentationFieldsFactory;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.updatedocumentationfields.UpdateBlueprintVersionDocumentationFieldsPresenter;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.BlueprintVersionMapper;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.publish.PublishBlueprintVersionResponseRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.publish.PublishBlueprintVersionCommandRes;
import org.opendatamesh.platform.pp.blueprint.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.publish.PublishBlueprintVersionCommand;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.publish.PublishBlueprintVersionPresenter;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.BlueprintVersionRes;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.updatedocumentationfields.UpdateBlueprintVersionDocumentationFieldsReponseRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.instantiate.InstantiateBlueprintVersionCommandRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.instantiate.InstantiateBlueprintVersionResponseRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.updatedocumentationfields.UpdateBlueprintVersionDocumentationFieldsCommandRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.updatedataproduct.UpdateDataProductCommandRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.updatedataproduct.UpdateDataProductResultRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.updatedataproduct.UpdateDataProductTargetResultRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.gitproviders.RepositoryMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BlueprintVersionUseCasesService {

    private final InstantiateBlueprintVersionFactory instantiateBlueprintVersionFactory;
    private final RepositoryMapper repositoryMapper;
    private final PublishBlueprintVersionFactory publishBlueprintVersionFactory;
    private final UpdateBlueprintVersionDocumentationFieldsFactory updateBlueprintVersionDocumentationFieldsFactory;
    private final UpdateDataProductFromBlueprintVersionFactory updateDataProductFromBlueprintVersionFactory;
    private final BlueprintVersionMapper blueprintVersionMapper;
    private final ObjectMapper objectMapper;

    public BlueprintVersionUseCasesService(
            InstantiateBlueprintVersionFactory instantiateBlueprintVersionFactory,
            RepositoryMapper repositoryMapper,
            PublishBlueprintVersionFactory publishBlueprintVersionFactory,
            UpdateBlueprintVersionDocumentationFieldsFactory updateBlueprintVersionDocumentationFieldsFactory,
            UpdateDataProductFromBlueprintVersionFactory updateDataProductFromBlueprintVersionFactory,
            BlueprintVersionMapper blueprintVersionMapper,
            ObjectMapper objectMapper) {
        this.instantiateBlueprintVersionFactory = instantiateBlueprintVersionFactory;
        this.repositoryMapper = repositoryMapper;
        this.publishBlueprintVersionFactory = publishBlueprintVersionFactory;
        this.updateBlueprintVersionDocumentationFieldsFactory = updateBlueprintVersionDocumentationFieldsFactory;
        this.updateDataProductFromBlueprintVersionFactory = updateDataProductFromBlueprintVersionFactory;
        this.blueprintVersionMapper = blueprintVersionMapper;
        this.objectMapper = objectMapper;
    }

    public InstantiateBlueprintVersionResponseRes instantiateBlueprintVersion(
            InstantiateBlueprintVersionCommandRes command,
            HttpHeaders headers) {

        if (command.getTargetRepositories() == null || command.getTargetRepositories().isEmpty()) {
            throw new BadRequestException("At least one target repository is required");
        }
        InstantiateBlueprintVersionCommand domainCommand = mapResToInternalCommand(command);

        InstantiateResultHolder presenter = new InstantiateResultHolder();
        instantiateBlueprintVersionFactory.buildInstantiateBlueprintVersion(domainCommand, presenter, headers)
                .execute();
        return new InstantiateBlueprintVersionResponseRes();
    }

    public UpdateDataProductResultRes updateDataProduct(
            UpdateDataProductCommandRes command,
            HttpHeaders headers) {
        UpdateDataProductCommand domainCommand = mapUpdateDataProductCommand(command);
        UpdateDataProductResultHolder presenter = new UpdateDataProductResultHolder();
        updateDataProductFromBlueprintVersionFactory.buildUpdateDataProduct(domainCommand, presenter, headers)
                .execute();
        return mapUpdateDataProductResult(presenter.getResult());
    }

    public PublishBlueprintVersionResponseRes publishBlueprintVersion(PublishBlueprintVersionCommandRes command) {
        validateCommand(command);

        BlueprintVersionRes res = objectMapper.convertValue(
            command.getBlueprintVersion(),
            BlueprintVersionRes.class);
        BlueprintVersion blueprintVersion = blueprintVersionMapper.toEntity(res);

        PublishBlueprintVersionCommand domainCommand = new PublishBlueprintVersionCommand(blueprintVersion);
        ResultHolder presenter = new ResultHolder();
        publishBlueprintVersionFactory.buildPublishBlueprintVersion(domainCommand, presenter).execute();
        PublishBlueprintVersionResponseRes response = new PublishBlueprintVersionResponseRes();
        response.setBlueprintVersion(blueprintVersionMapper.toRes(presenter.getResult()));
        return response;
    }

    public UpdateBlueprintVersionDocumentationFieldsReponseRes updateBlueprintVersionDocumentationFields(UpdateBlueprintVersionDocumentationFieldsCommandRes command) {
        validateUpdateCommand(command);
        
        UpdateBlueprintVersionDocumentationFieldsCommand domainCommand = new UpdateBlueprintVersionDocumentationFieldsCommand(
                command.getUuid(),
                command.getName(),
                command.getDescription(),
                command.getUpdatedBy()
        );
        
        UpdateDocumentationFieldsResultHolder presenter = new UpdateDocumentationFieldsResultHolder();
        updateBlueprintVersionDocumentationFieldsFactory.buildUpdateBlueprintVersionDocumentationFields(domainCommand, presenter).execute();
        UpdateBlueprintVersionDocumentationFieldsReponseRes response = new UpdateBlueprintVersionDocumentationFieldsReponseRes();
        response.setBlueprintVersion(blueprintVersionMapper.toRes(presenter.getResult()));
        return response;
    }

    private InstantiateBlueprintVersionCommand mapResToInternalCommand(
            InstantiateBlueprintVersionCommandRes command) {
        return new InstantiateBlueprintVersionCommand(
                command.getBlueprintName(),
                command.getBlueprintVersionNumber(),
                command.getTargetRepositories().stream()
                        .map(res -> new TargetRepositoryDto(res.getTargetId(), res.getBranch(),
                                repositoryMapper.toEntity(res.getRepository())))
                        .toList(),
                command.getParameters() == null ? Map.of() : new LinkedHashMap<>(command.getParameters()),
                command.getCommitAuthorName(),
                command.getCommitAuthorEmail());
    }

    private UpdateDataProductCommand mapUpdateDataProductCommand(UpdateDataProductCommandRes command) {
        if (command == null) {
            throw new BadRequestException("Update data product command is required");
        }
        List<UpdateDataProductTargetRepositoryDto> targets = command.getTargetRepositories() == null
                ? List.of()
                : command.getTargetRepositories().stream()
                .map(res -> new UpdateDataProductTargetRepositoryDto(
                        res.getTargetId(),
                        res.getBranch(),
                        repositoryMapper.toEntity(res.getRepository()),
                        res.getPullRequestTargetBranch()))
                .toList();
        return new UpdateDataProductCommand(
                command.getBlueprintName(),
                command.getCurrentVersionNumber(),
                command.getNextVersionNumber(),
                command.getParameters(),
                targets,
                command.getCommitAuthorName(),
                command.getCommitAuthorEmail(),
                Boolean.TRUE.equals(command.getCreatePullRequest())
        );
    }

    private UpdateDataProductResultRes mapUpdateDataProductResult(UpdateDataProductResult result) {
        UpdateDataProductResultRes response = new UpdateDataProductResultRes();
        if (result == null) {
            return response;
        }
        List<UpdateDataProductTargetResultRes> results = new ArrayList<>();
        if (result.results() != null) {
            for (UpdateDataProductTargetResult targetResult : result.results()) {
                UpdateDataProductTargetResultRes res = new UpdateDataProductTargetResultRes();
                res.setTargetId(targetResult.targetId());
                res.setRepository(repositoryMapper.toRes(targetResult.repository()));
                res.setUpdateBranchName(targetResult.updateBranchName());
                res.setCheckpointTag(targetResult.checkpointTag());
                res.setCommitHash(targetResult.commitHash());
                res.setPullRequestWebUrl(targetResult.pullRequestWebUrl());
                results.add(res);
            }
        }
        response.setResults(results);
        response.setWarnings(result.warnings() == null ? List.of() : new ArrayList<>(result.warnings()));
        return response;
    }

    private static final class ResultHolder implements PublishBlueprintVersionPresenter {

        private BlueprintVersion result;

        @Override
        public void presentPublished(BlueprintVersion blueprintVersion) {
            this.result = blueprintVersion;
        }

        BlueprintVersion getResult() {
            return result;
        }
    }

    private static final class InstantiateResultHolder implements InstantiateBlueprintVersionPresenter {

        @Override
        public void presentResults(InstantiateBlueprintVersionResult result) {
        }
    }

    private static final class UpdateDataProductResultHolder implements UpdateDataProductPresenter {

        private UpdateDataProductResult result;

        @Override
        public void presentResult(UpdateDataProductResult result) {
            this.result = result;
        }

        UpdateDataProductResult getResult() {
            return result;
        }
    }

    private static final class UpdateDocumentationFieldsResultHolder implements UpdateBlueprintVersionDocumentationFieldsPresenter {

        private BlueprintVersion result;

        @Override
        public void presentUpdated(BlueprintVersion blueprintVersion) {
            this.result = blueprintVersion;
        }

        BlueprintVersion getResult() {
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

    private void validateUpdateCommand(UpdateBlueprintVersionDocumentationFieldsCommandRes command) {
        if (command == null) {
            throw new BadRequestException("Command is required");
        }
        if (!StringUtils.hasText(command.getUuid())) {
            throw new BadRequestException("Blueprint version uuid is required");
        }
        if (!StringUtils.hasText(command.getName())) {
            throw new BadRequestException("Missing Blueprint Version name");
        }
        if (!StringUtils.hasText(command.getUpdatedBy())) {
            throw new BadRequestException("updatedBy is required");
        }
    }
}
