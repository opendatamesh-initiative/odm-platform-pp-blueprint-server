package org.opendatamesh.platform.pp.blueprint.blueprint.services;

import org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint;
import org.opendatamesh.platform.pp.blueprint.blueprint.services.usecases.register.RegisterBlueprintCommand;
import org.opendatamesh.platform.pp.blueprint.blueprint.services.usecases.register.RegisterBlueprintFactory;
import org.opendatamesh.platform.pp.blueprint.blueprint.services.usecases.register.RegisterBlueprintPresenter;
import org.opendatamesh.platform.pp.blueprint.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintMapper;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.usecases.register.RegisterBlueprintCommandRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.usecases.register.RegisterBlueprintResponseRes;
import org.springframework.stereotype.Service;

@Service
public class BlueprintUseCasesService {

    private final RegisterBlueprintFactory registerBlueprintFactory;
    private final BlueprintMapper blueprintMapper;

    public BlueprintUseCasesService(
            RegisterBlueprintFactory registerBlueprintFactory,
            BlueprintMapper blueprintMapper
    ) {
        this.registerBlueprintFactory = registerBlueprintFactory;
        this.blueprintMapper = blueprintMapper;
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
}
