package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate;

import org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint;

import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;

interface InstantiateBlueprintVersionGitOutboundPort {

    void init(Blueprint blueprint);

    void cloneRepositories(List<SourceRepositoryDto> sourceRepositories, List<TargetRepositoryDto> targetRepositories, BiConsumer<List<Path>, List<Path>> clonedRepositoriesConsumer);

    void commitAndPush(Path targetRepositoryPath, String commitMessage, String commitAuthorName, String commitAuthorEmail);
}
