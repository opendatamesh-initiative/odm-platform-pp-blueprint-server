package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.updatedataproduct;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * Domain input for the update-data-product use case (mapped from the REST command).
 * Holds blueprint identity, current/next versions, parameters, targets, author fields,
 * and whether to open pull requests after a successful update.
 */
public record UpdateDataProductCommand(
        String blueprintName,
        String currentVersionNumber,
        String nextVersionNumber,
        Map<String, JsonNode> parameters,
        List<UpdateDataProductTargetRepositoryDto> targetRepositories,
        String commitAuthorName,
        String commitAuthorEmail,
        boolean createPullRequest
) {
}
