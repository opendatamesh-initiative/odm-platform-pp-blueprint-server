package org.opendatamesh.platform.pp.blueprint.blueprintversion.services;

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

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class BlueprintVersionUseCasesService {

    private final PublishBlueprintVersionFactory publishBlueprintVersionFactory;
    private final BlueprintVersionMapper blueprintVersionMapper;
    private final ObjectMapper objectMapper;

    public BlueprintVersionUseCasesService(
        PublishBlueprintVersionFactory publishBlueprintVersionFactory,
        BlueprintVersionMapper blueprintVersionMapper,
        ObjectMapper objectMapper
    ) {
        this.publishBlueprintVersionFactory = publishBlueprintVersionFactory;
        this.blueprintVersionMapper = blueprintVersionMapper;
        this.objectMapper = objectMapper;
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

    private void validateCommand(PublishBlueprintVersionCommandRes command) {
        if (command == null || command.getBlueprintVersion() == null) {
            throw new BadRequestException("Blueprint version is required");
        }
        if (!StringUtils.hasText(command.getBlueprintVersion().getBlueprint().getName()) && !StringUtils.hasText(command.getBlueprintVersion().getBlueprint().getUuid())) {
            throw new BadRequestException("Blueprint name or uuid is required to publish a blueprint version");
        }
    }
}
