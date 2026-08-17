package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.updatedataproduct;

import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.core.BlueprintVersionCrudService;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.BlueprintDataProductDescriptorService;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.BlueprintRenderService;
import org.opendatamesh.platform.pp.blueprint.git.provider.GitProviderFactory;
import org.opendatamesh.platform.pp.blueprint.utils.usecases.UseCase;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class UpdateDataProductFromBlueprintVersionFactory {

    private final GitProviderFactory gitProviderFactory;
    private final BlueprintVersionCrudService blueprintVersionCrudService;
    private final BlueprintRenderService blueprintRenderService;
    private final BlueprintDataProductDescriptorService blueprintDataProductDescriptorService;

    public UpdateDataProductFromBlueprintVersionFactory(
            GitProviderFactory gitProviderFactory,
            BlueprintVersionCrudService blueprintVersionCrudService,
            BlueprintRenderService blueprintRenderService,
            BlueprintDataProductDescriptorService blueprintDataProductDescriptorService
    ) {
        this.gitProviderFactory = gitProviderFactory;
        this.blueprintVersionCrudService = blueprintVersionCrudService;
        this.blueprintRenderService = blueprintRenderService;
        this.blueprintDataProductDescriptorService = blueprintDataProductDescriptorService;
    }

    public UseCase buildUpdateDataProduct(
            UpdateDataProductCommand command,
            UpdateDataProductPresenter presenter,
            HttpHeaders headers
    ) {
        UpdateDataProductGitOutboundPort gitPort =
                new UpdateDataProductGitOutboundPortImpl(headers, gitProviderFactory);
        UpdateDataProductPersistencyOutboundPort persistencyPort =
                new UpdateDataProductPersistencyOutboundPortImpl(blueprintVersionCrudService);
        UpdateDataProductManifestOutboundPort manifestPort =
                new UpdateDataProductOdmBlueprintManifestOutboundPortImpl();
        UpdateDataProductTemplatingOutboundPort templatingPort =
                new UpdateDataProductTemplatingOutboundPortImpl(
                        blueprintRenderService, blueprintDataProductDescriptorService);
        return new UpdateDataProductFromBlueprintVersion(
                command,
                presenter,
                persistencyPort,
                manifestPort,
                templatingPort,
                gitPort
        );
    }
}
