package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate;

import org.opendatamesh.platform.pp.blueprint.blueprint.services.core.BlueprintService;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.core.BlueprintVersionCrudService;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.BlueprintDataProductDescriptorService;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.BlueprintRenderService;
import org.opendatamesh.platform.pp.blueprint.git.provider.GitProviderFactory;
import org.opendatamesh.platform.pp.blueprint.utils.usecases.UseCase;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class InstantiateBlueprintVersionFactory {
    private final GitProviderFactory gitProviderFactory;
    private final BlueprintService blueprintService;
    private final BlueprintVersionCrudService blueprintVersionCrudService;
    private final BlueprintRenderService blueprintRenderService;
    private final BlueprintDataProductDescriptorService blueprintDataProductDescriptorService;

    public InstantiateBlueprintVersionFactory(
            GitProviderFactory gitProviderFactory,
            BlueprintService blueprintService,
            BlueprintVersionCrudService blueprintVersionCrudService,
            BlueprintRenderService blueprintRenderService,
            BlueprintDataProductDescriptorService blueprintDataProductDescriptorService
    ) {
        this.gitProviderFactory = gitProviderFactory;
        this.blueprintService = blueprintService;
        this.blueprintVersionCrudService = blueprintVersionCrudService;
        this.blueprintRenderService = blueprintRenderService;
        this.blueprintDataProductDescriptorService = blueprintDataProductDescriptorService;
    }

    public UseCase buildInstantiateBlueprintVersion(
            InstantiateBlueprintVersionCommand command,
            InstantiateBlueprintVersionPresenter presenter,
            HttpHeaders headers
    ) {
        InstantiateBlueprintVersionGitOutboundPort gitPort =
                new InstantiateBlueprintVersionGitOutboundPortImpl(headers, gitProviderFactory);
        InstantiateBlueprintVersionPersistencyOutboundPort persistencyPort =
                new InstantiateBlueprintVersionPersistencyOutboundPortImpl(blueprintService, blueprintVersionCrudService);
        InstantiateBlueprintVersionManifestOutboundPort manifestPort =
                new InstantiateBlueprintVersionOdmBlueprintManifestOutboundPortImpl();
        InstantiateBlueprintVersionTemplatingOutboundPort templatingPort =
                new InstantiateBlueprintVersionTemplatingOutboundPortImpl(
                        blueprintRenderService, blueprintDataProductDescriptorService);
        return new InstantiateBlueprintVersion(
                command,
                presenter,
                persistencyPort,
                manifestPort,
                templatingPort,
                gitPort
        );
    }

    /**
     * Same persistency / manifest / templating wiring as production instantiate, but a local
     * Git port that no-ops pushes and uses a throwaway target instead of cloning the live
     * product integration branch.
     */
    public UseCase buildInstantiateBlueprintVersionForLocalValidation(
            InstantiateBlueprintVersionCommand command,
            InstantiateBlueprintVersionPresenter presenter,
            HttpHeaders serviceHeaders,
            RenderedTreeSnapshot snapshot
    ) {
        InstantiateBlueprintVersionGitOutboundPort gitPort =
                new InstantiateBlueprintVersionLocalGitOutboundPort(serviceHeaders, gitProviderFactory, snapshot);
        InstantiateBlueprintVersionPersistencyOutboundPort persistencyPort =
                new InstantiateBlueprintVersionPersistencyOutboundPortImpl(blueprintService, blueprintVersionCrudService);
        InstantiateBlueprintVersionManifestOutboundPort manifestPort =
                new InstantiateBlueprintVersionOdmBlueprintManifestOutboundPortImpl();
        InstantiateBlueprintVersionTemplatingOutboundPort templatingPort =
                new InstantiateBlueprintVersionTemplatingOutboundPortImpl(
                        blueprintRenderService, blueprintDataProductDescriptorService);
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
