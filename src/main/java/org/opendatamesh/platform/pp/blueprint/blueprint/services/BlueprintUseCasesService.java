package org.opendatamesh.platform.pp.blueprint.blueprint.services;

import org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.BlueprintRepo;
import org.opendatamesh.platform.pp.blueprint.blueprint.services.usecases.register.RegisterBlueprintCommand;
import org.opendatamesh.platform.pp.blueprint.blueprint.services.usecases.register.RegisterBlueprintFactory;
import org.opendatamesh.platform.pp.blueprint.blueprint.services.usecases.register.RegisterBlueprintPresenter;
import org.opendatamesh.platform.pp.blueprint.blueprint.services.usecases.updatedocumentationfields.UpdateBlueprintDocumentationFieldsCommand;
import org.opendatamesh.platform.pp.blueprint.blueprint.services.usecases.updatedocumentationfields.UpdateBlueprintDocumentationFieldsFactory;
import org.opendatamesh.platform.pp.blueprint.blueprint.services.usecases.updatedocumentationfields.UpdateBlueprintDocumentationFieldsPresenter;
import org.opendatamesh.platform.pp.blueprint.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintMapper;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintRepoMapper;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.usecases.register.RegisterBlueprintCommandRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.usecases.register.RegisterBlueprintResponseRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.usecases.updatedocumentationfields.BlueprintUpdateDocumentationFieldsCommandRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.usecases.updatedocumentationfields.UpdateBlueprintDocumentationFieldsResponseRes;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class BlueprintUseCasesService {

    private final RegisterBlueprintFactory registerBlueprintFactory;
    private final UpdateBlueprintDocumentationFieldsFactory updateBlueprintDocumentationFieldsFactory;
    private final BlueprintMapper blueprintMapper;
    private final BlueprintRepoMapper blueprintRepoMapper;
    private final ObjectMapper objectMapper;

    public BlueprintUseCasesService(
            RegisterBlueprintFactory registerBlueprintFactory,
            UpdateBlueprintDocumentationFieldsFactory updateBlueprintDocumentationFieldsFactory,
            BlueprintMapper blueprintMapper,
            BlueprintRepoMapper blueprintRepoMapper,
            ObjectMapper objectMapper
    ) {
        this.registerBlueprintFactory = registerBlueprintFactory;
        this.updateBlueprintDocumentationFieldsFactory = updateBlueprintDocumentationFieldsFactory;
        this.blueprintMapper = blueprintMapper;
        this.blueprintRepoMapper = blueprintRepoMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * Registers a blueprint (use-case entry point).
     */
    public RegisterBlueprintResponseRes registerBlueprint(RegisterBlueprintCommandRes command) {
        if (command == null || command.getBlueprint() == null) {
            throw new BadRequestException("Blueprint is required");
        }
        Blueprint blueprint = blueprintMapper.toEntity(command.getBlueprint());
        RegisterBlueprintCommand domainCommand = new RegisterBlueprintCommand(blueprint);
        ResultHolder presenter = new ResultHolder();
        registerBlueprintFactory.buildRegisterBlueprint(domainCommand, presenter).execute();
        RegisterBlueprintResponseRes response = new RegisterBlueprintResponseRes();
        response.setBlueprint(blueprintMapper.toRes(presenter.getResult()));
        return response;
    }

    /**
     * Updates display name, description, and optionally nested repository configuration for an existing blueprint.
     */
    public UpdateBlueprintDocumentationFieldsResponseRes updateBlueprintDocumentationFields(BlueprintUpdateDocumentationFieldsCommandRes command) {
        validateUpdateCommand(command);

        BlueprintRepo blueprintRepo = null;
        if (command.getBlueprintRepo() != null) {
            BlueprintRes.BlueprintRepoRes res = objectMapper.convertValue(
                    command.getBlueprintRepo(),
                    BlueprintRes.BlueprintRepoRes.class);
                    blueprintRepo = blueprintRepoMapper.toEntity(res);
        }
        
        UpdateBlueprintDocumentationFieldsCommand domainCommand = new UpdateBlueprintDocumentationFieldsCommand(
                command.getUuid(),
                command.getDisplayName(),
                command.getDescription(),
                blueprintRepo
        );
        UpdateResultHolder presenter = new UpdateResultHolder();
        updateBlueprintDocumentationFieldsFactory.buildUpdateBlueprintDocumentationFields(domainCommand, presenter).execute();
        UpdateBlueprintDocumentationFieldsResponseRes response = new UpdateBlueprintDocumentationFieldsResponseRes();
        response.setBlueprint(blueprintMapper.toRes(presenter.getResult()));
        return response;
    }

    private static final class ResultHolder implements RegisterBlueprintPresenter {

        private Blueprint result;

        @Override
        public void presentRegistered(Blueprint blueprint) {
            this.result = blueprint;
        }

        Blueprint getResult() {
            return result;
        }
    }

    private static final class UpdateResultHolder implements UpdateBlueprintDocumentationFieldsPresenter {

        private Blueprint result;

        @Override
        public void presentUpdated(Blueprint blueprint) {
            this.result = blueprint;
        }

        Blueprint getResult() {
            return result;
        }
    }

    private void validateUpdateCommand(BlueprintUpdateDocumentationFieldsCommandRes command) {
        if (command == null) {
            throw new BadRequestException("Command is required");
        }
        if (!StringUtils.hasText(command.getUuid())) {
            throw new BadRequestException("Blueprint uuid is required");
        }
        if (!StringUtils.hasText(command.getDisplayName())) {
            throw new BadRequestException("Display name is required");
        }
    }
}
