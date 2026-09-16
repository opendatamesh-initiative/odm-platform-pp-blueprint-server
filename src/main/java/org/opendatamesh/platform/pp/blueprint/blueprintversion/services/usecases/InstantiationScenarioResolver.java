package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases;

import org.opendatamesh.platform.pp.blueprint.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.blueprint.manifest.model.Manifest;
import org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation.ManifestTargetRepository;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Derives {@link InstantiationScenario} from repository-key cardinality and composition presence.
 */
public final class InstantiationScenarioResolver {

    private InstantiationScenarioResolver() {
    }

    public static InstantiationScenario resolve(Manifest manifest) {
        if (manifest == null || CollectionUtils.isEmpty(manifest.getTargetRepositories())) {
            throw new BadRequestException("Manifest targetRepositories is required");
        }
        List<ManifestTargetRepository> repositories = manifest.getTargetRepositories();

        Set<String> keys = new LinkedHashSet<>();
        for (ManifestTargetRepository repository : repositories) {
            if (repository == null || !StringUtils.hasText(repository.getKey())) {
                throw new BadRequestException("Manifest targetRepositories[].key is required");
            }
            keys.add(repository.getKey().trim());
        }
        int repoCount = keys.size();
        boolean hasComposition = !CollectionUtils.isEmpty(manifest.getComposition());

        if (repoCount == 1) {
            return hasComposition
                    ? InstantiationScenario.MONOREPO_WITH_COMPOSITION
                    : InstantiationScenario.MONOREPO_NO_COMPOSITION;
        }
        return hasComposition
                ? InstantiationScenario.POLYREPO_WITH_COMPOSITION
                : InstantiationScenario.POLYREPO_NO_COMPOSITION;
    }

    public static boolean isMonorepoNoComposition(Manifest manifest) {
        return resolve(manifest) == InstantiationScenario.MONOREPO_NO_COMPOSITION;
    }
}
