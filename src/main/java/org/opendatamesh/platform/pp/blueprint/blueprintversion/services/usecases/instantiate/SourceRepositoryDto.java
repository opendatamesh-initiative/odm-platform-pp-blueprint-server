package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate;

import org.opendatamesh.platform.git.model.Repository;

/**
 * Domain description of one blueprint source Git repository to clone for templating
 * (use-case-internal {@code Dto}).
 * Frozen at a release {@code tag} (typically from {@code BlueprintVersion.tag}).
 */
public record SourceRepositoryDto(
        String id,
        String tag,
        Repository repository
) {
}
