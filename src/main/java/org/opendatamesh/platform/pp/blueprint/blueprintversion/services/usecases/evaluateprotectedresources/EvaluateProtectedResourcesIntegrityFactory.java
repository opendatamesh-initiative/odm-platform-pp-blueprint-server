package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources;

import org.opendatamesh.platform.pp.blueprint.blueprint.services.core.BlueprintService;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.core.BlueprintVersionCrudService;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate.InstantiateBlueprintVersionFactory;
import org.opendatamesh.platform.pp.blueprint.git.provider.GitProviderFactory;
import org.opendatamesh.platform.pp.blueprint.utils.usecases.UseCase;
import org.opendatamesh.platform.pp.blueprint.validator.config.BlueprintValidatorProperties;
import org.springframework.stereotype.Component;

@Component
public class EvaluateProtectedResourcesIntegrityFactory {

    private final BlueprintService blueprintService;
    private final BlueprintVersionCrudService blueprintVersionCrudService;
    private final GitProviderFactory gitProviderFactory;
    private final InstantiateBlueprintVersionFactory instantiateBlueprintVersionFactory;
    private final BlueprintValidatorProperties validatorProperties;

    public EvaluateProtectedResourcesIntegrityFactory(
            BlueprintService blueprintService,
            BlueprintVersionCrudService blueprintVersionCrudService,
            GitProviderFactory gitProviderFactory,
            InstantiateBlueprintVersionFactory instantiateBlueprintVersionFactory,
            BlueprintValidatorProperties validatorProperties
    ) {
        this.blueprintService = blueprintService;
        this.blueprintVersionCrudService = blueprintVersionCrudService;
        this.gitProviderFactory = gitProviderFactory;
        this.instantiateBlueprintVersionFactory = instantiateBlueprintVersionFactory;
        this.validatorProperties = validatorProperties;
    }

    public UseCase buildEvaluateProtectedResourcesIntegrity(
            EvaluateProtectedResourcesIntegrityCommand command,
            EvaluateProtectedResourcesIntegrityPresenter presenter
    ) {
        return new EvaluateProtectedResourcesIntegrity(
                command,
                presenter,
                new EvaluateProtectedResourcesIntegrityPersistencyOutboundPortImpl(
                        blueprintService, blueprintVersionCrudService),
                new EvaluateProtectedResourcesIntegrityGitOutboundPortImpl(gitProviderFactory, validatorProperties),
                new EvaluateProtectedResourcesIntegrityInstantiateOutboundPortImpl(
                        instantiateBlueprintVersionFactory, validatorProperties),
                new EvaluateProtectedResourcesIntegrityDigestOutboundPortImpl()
        );
    }
}
