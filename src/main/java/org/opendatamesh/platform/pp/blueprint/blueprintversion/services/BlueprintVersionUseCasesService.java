package org.opendatamesh.platform.pp.blueprint.blueprintversion.services;

import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate.*;
import org.opendatamesh.platform.pp.blueprint.exceptions.BadRequestException;
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

    public BlueprintVersionUseCasesService(
            InstantiateBlueprintVersionFactory instantiateBlueprintVersionFactory,
            RepositoryMapper repositoryMapper) {
        this.instantiateBlueprintVersionFactory = instantiateBlueprintVersionFactory;
        this.repositoryMapper = repositoryMapper;
    }

    public InstantiateBlueprintVersionResponseRes instantiateBlueprintVersion(
            InstantiateBlueprintVersionCommandRes command,
            HttpHeaders headers) {

        if (command.getTargetRepositories() == null || command.getTargetRepositories().isEmpty()) {
            throw new BadRequestException("At least one target repository is required");
        }
        InstantiateBlueprintVersionTargetRepositoryRes targetRes = command.getTargetRepositories().getFirst();
        InstantiateBlueprintVersionCommand domainCommand = mapResToInternalCommand(command, headers, targetRes);

        InstantiateResultHolder presenter = new InstantiateResultHolder();
        instantiateBlueprintVersionFactory.buildInstantiateBlueprintVersion(domainCommand, presenter, headers)
                .execute();
        InstantiateBlueprintVersionResult result = presenter.getResult();
        return new InstantiateBlueprintVersionResponseRes();
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
        public void presentResults(InstantiateBlueprintVersionResult result) {
            this.result = result;
        }

        InstantiateBlueprintVersionResult getResult() {
            return result;
        }
    }
}
