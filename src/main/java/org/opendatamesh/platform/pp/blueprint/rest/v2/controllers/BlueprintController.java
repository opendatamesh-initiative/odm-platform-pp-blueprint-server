package org.opendatamesh.platform.pp.blueprint.rest.v2.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.opendatamesh.platform.pp.blueprint.blueprint.services.core.BlueprintService;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.BlueprintSearchOptions;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import io.swagger.v3.oas.annotations.Hidden;

@RestController
@RequestMapping(value = "/api/v2/pp/blueprint/blueprints", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Blueprints", description = "Endpoints for managing blueprints")
public class BlueprintController {

    /**
     * Service for CRUD operations on individual Blueprint entities.
     * Used for create, read (single), update, and delete operations.
     */
    @Autowired
    private BlueprintService blueprintCrudService;

    @Hidden
    @Operation(summary = "Create a new blueprint", description = "Creates a new blueprint")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Blueprint created successfully",
            content = @Content(schema = @Schema(implementation = BlueprintRes.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
        @ApiResponse(responseCode = "500", description = "Internal server error"),
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BlueprintRes createBlueprint(
        @Parameter(description = "Blueprint creation request")
        @RequestBody BlueprintRes blueprint
    ) {
        return blueprintCrudService.createResource(blueprint);
    }

    @Operation(summary = "Get a blueprint by ID", description = "Retrieves a blueprint by its ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Blueprint retrieved successfully",
            content = @Content(schema = @Schema(implementation = BlueprintRes.class))),
        @ApiResponse(responseCode = "404", description = "Blueprint not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error"),
    })
    @GetMapping("/{uuid}")
    public BlueprintRes getBlueprint(
        @Parameter(description = "Blueprint UUID")
        @PathVariable("uuid") String uuid) {
        return blueprintCrudService.findOneResource(uuid);
    }

    @Operation(summary = "Search blueprints", description = "Retrieves a paginated list of blueprints based on search criteria. " +
            "The results can be sorted by any of the following properties: uuid, name, displayName, description, " +
            "createdAt, updatedAt. Sort direction can be specified using 'asc' or 'desc' (e.g., 'sort=name,desc').")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Blueprints found",
                    content = @Content(schema = @Schema(implementation = Page.class))),
            @ApiResponse(responseCode = "400", description = "Invalid search parameters or invalid sort property"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public Page<BlueprintRes> searchBlueprints(
            @Parameter(description = "Search options for filtering data products")
            BlueprintSearchOptions searchOptions,
            @Parameter(description = "Pagination and sorting parameters. Default sort is by createdAt in descending order. " +
                    "Valid sort properties are: uuid, name, displayName, description, createdAt, updatedAt")
            @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return blueprintCrudService.findAllResourcesFiltered(pageable, searchOptions);
    }

    @Hidden
    @Operation(summary = "Update blueprint", description = "Updates an existing blueprint by its UUID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Blueprint updated successfully",
                    content = @Content(schema = @Schema(implementation = BlueprintRes.class))),
            @ApiResponse(responseCode = "404", description = "Blueprint not found"),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PutMapping("/{uuid}")
    @ResponseStatus(HttpStatus.OK)
    public BlueprintRes updateBlueprint(
            @Parameter(description = "Blueprint UUID", required = true)
            @PathVariable("uuid") String uuid,
            @Parameter(description = "Updated blueprint details", required = true)
            @RequestBody BlueprintRes blueprint
    ) {
        return blueprintCrudService.overwriteResource(uuid, blueprint);
    }

    @Hidden
    @Operation(summary = "Delete blueprint", description = "Deletes a blueprint by its UUID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Blueprint deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Blueprint not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @DeleteMapping("/{uuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBlueprint(
            @Parameter(description = "Blueprint UUID", required = true)
            @PathVariable("uuid") String uuid
    ) {
        blueprintCrudService.delete(uuid);
    }
}
