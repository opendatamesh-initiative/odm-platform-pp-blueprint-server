package org.opendatamesh.platform.pp.blueprint.rest.v2.controllers;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.opendatamesh.platform.pp.blueprint.blueprint.services.BlueprintRepositoryUtilsService;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.repository.InitRepositoryCommandRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.repository.RepositoryContentReadReq;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.repository.RepositoryContentReadRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.ErrorRes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/v2/pp/blueprint/blueprints", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Blueprints", description = "Endpoints for managing blueprints")
public class BlueprintRepositoryUtilsController {

    private final BlueprintRepositoryUtilsService repositoryUtilsService;

    public BlueprintRepositoryUtilsController(BlueprintRepositoryUtilsService repositoryUtilsService) {
        this.repositoryUtilsService = repositoryUtilsService;
    }

    @PostMapping("/{uuid}/repository-content")
    @Operation(
            summary = "Initialize blueprint repository content",
            description = """
                    Writes the given file payloads (text in any format) into a local clone of the blueprint's linked Git repository,
                    creates an initial commit, and pushes to the remote. Authentication for the Git provider is taken
                    from the same headers used elsewhere for Git provider APIs (e.g. PAT).
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Files were written, committed, and pushed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request body, repository not reachable, or Git operation failed",
                    content = @Content(schema = @Schema(implementation = ErrorRes.class))),
            @ApiResponse(responseCode = "404", description = "Blueprint not found",
                    content = @Content(schema = @Schema(implementation = ErrorRes.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected error while writing files",
                    content = @Content(schema = @Schema(implementation = ErrorRes.class)))
    })
    @ResponseStatus(HttpStatus.OK)
    public void initRepository(
            @PathVariable @Parameter(description = "Blueprint UUID", required = true) String uuid,
            @Parameter(description = "Branch override, author identity, and files to add under the repository root")
            @RequestBody InitRepositoryCommandRes initRepositoryCommand,
            @Parameter(description = "HTTP headers for Git provider authentication (same convention as Git provider endpoints)")
            @RequestHeader HttpHeaders headers) {
        repositoryUtilsService.initBlueprintRepository(uuid, initRepositoryCommand, headers);
    }

    @GetMapping("/{uuid}/repository-content")
    @Operation(
            summary = "Read blueprint repository file content",
            description = """
                    Reads UTF-8 text from one or more paths relative to the repository root in a local clone
                    of the blueprint's linked Git repository at the given repository pointer (branch, tag, or commit).
                    When no path query parameters are sent, the server reads the paths configured on the blueprint
                    repository metadata (readme, manifest root, descriptor template). Authentication uses the same
                    Git provider headers as POST repository-content. This operation does not commit or push.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Files were read successfully",
                    content = @Content(schema = @Schema(implementation = RepositoryContentReadRes.class))),
            @ApiResponse(responseCode = "400", description = "Invalid pointer, path, or Git read failure",
                    content = @Content(schema = @Schema(implementation = ErrorRes.class))),
            @ApiResponse(responseCode = "404", description = "Blueprint not found, or requested file not present at the pointer",
                    content = @Content(schema = @Schema(implementation = ErrorRes.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected error while reading files",
                    content = @Content(schema = @Schema(implementation = ErrorRes.class)))
    })
    @ResponseStatus(HttpStatus.OK)
    public RepositoryContentReadRes readRepositoryContent(
            @ModelAttribute RepositoryContentReadReq request,
            @Parameter(description = "HTTP headers for Git provider authentication (same convention as Git provider endpoints)")
            @RequestHeader HttpHeaders headers) {
        return repositoryUtilsService.readBlueprintRepositoryContent(
                request,
                headers
        );
    }
}
