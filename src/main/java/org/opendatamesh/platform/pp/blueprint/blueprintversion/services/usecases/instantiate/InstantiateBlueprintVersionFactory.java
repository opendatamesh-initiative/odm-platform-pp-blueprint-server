package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate;

import org.opendatamesh.platform.pp.blueprint.blueprint.services.core.BlueprintService;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.core.BlueprintVersionCrudService;
import org.opendatamesh.platform.pp.blueprint.git.provider.GitProviderFactory;
import org.opendatamesh.platform.pp.blueprint.utils.usecases.UseCase;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class InstantiateBlueprintVersionFactory {
    private final GitProviderFactory gitProviderFactory;
    private final BlueprintService blueprintService;
    private final BlueprintVersionCrudService blueprintVersionCrudService;

    public InstantiateBlueprintVersionFactory(
            GitProviderFactory gitProviderFactory,
            BlueprintService blueprintService,
            BlueprintVersionCrudService blueprintVersionCrudService
    ) {
        this.gitProviderFactory = gitProviderFactory;
        this.blueprintService = blueprintService;
        this.blueprintVersionCrudService = blueprintVersionCrudService;
    }

    public UseCase buildInstantiateBlueprintVersion(
            InstantiateBlueprintVersionCommand command,
            InstantiateBlueprintVersionPresenter presenter,
            HttpHeaders headers
    ) {
        InstantiateBlueprintVersionGitOutboundPort gitPort = new InstantiateBlueprintVersionGitOutboundPortImpl(headers, gitProviderFactory);
        InstantiateBlueprintVersionPersistencyOutboundPort persistencyPort =
                new InstantiateBlueprintVersionPersistencyOutboundPortImpl(blueprintService, blueprintVersionCrudService);
        InstantiateBlueprintVersionManifestOutboundPort manifestPort =
                new InstantiateBlueprintVersionOdmBlueprintManifestOutboundPortImpl();
        InstantiateBlueprintVersionTemplatingOutboundPort templatingPort =
                new InstantiateBlueprintVersionTemplatingOutboundPortImpl();
        return new InstantiateBlueprintVersion(
                command,
                presenter,
                persistencyPort,
                manifestPort,
                templatingPort,
                gitPort
        );
    }
}
