package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases;

import org.opendatamesh.platform.pp.blueprint.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.blueprint.manifest.model.Manifest;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestInstantiation;
import org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation.ManifestInstantiationRepository;
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
        if (manifest == null || manifest.getInstantiation() == null) {
            throw new BadRequestException("Manifest instantiation is required");
        }
        ManifestInstantiation instantiation = manifest.getInstantiation();
        List<ManifestInstantiationRepository> repositories = instantiation.getRepositories();
        if (CollectionUtils.isEmpty(repositories)) {
            throw new BadRequestException("Manifest instantiation.repositories is required");
        }

        Set<String> keys = new LinkedHashSet<>();
        for (ManifestInstantiationRepository repository : repositories) {
            if (repository == null || !StringUtils.hasText(repository.getKey())) {
                throw new BadRequestException("Manifest instantiation.repositories[].key is required");
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

    public static String soleRepositoryKey(Manifest manifest) {
        if (manifest == null || manifest.getInstantiation() == null
                || CollectionUtils.isEmpty(manifest.getInstantiation().getRepositories())) {
            throw new BadRequestException("Manifest instantiation.repositories is required");
        }
        List<ManifestInstantiationRepository> repositories = manifest.getInstantiation().getRepositories();
        if (repositories.size() != 1 || repositories.getFirst() == null
                || !StringUtils.hasText(repositories.getFirst().getKey())) {
            throw new BadRequestException("Exactly one instantiation.repositories[].key is required for this operation");
        }
        return repositories.getFirst().getKey().trim();
    }
}
