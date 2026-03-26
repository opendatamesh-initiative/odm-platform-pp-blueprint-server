package org.opendatamesh.platform.pp.blueprint.rest.v2.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.core.BlueprintVersionCrudService;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.core.BlueprintVersionQueryService;
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
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.BlueprintVersionRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.BlueprintVersionSearchOptions;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.BlueprintVersionShortRes;

@RestController
@RequestMapping(value = "/api/v2/pp/blueprint/blueprints-versions", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Blueprints versions", description = "Endpoints for managing blueprint versions")
public class BlueprintVersionsController {

    /**
     * Service for CRUD operations on individual Blueprint Version entities.
     * Used for create, read (single), update, and delete operations.
     */
    @Autowired
    private BlueprintVersionCrudService blueprintVersionCrudService;

    /**
     * Service for querying multiple Blueprint Version entities.
     * Used for paginated search and listing operations with better performance.
     */
    @Autowired
    private BlueprintVersionQueryService blueprintVersionQueryService;

    @Hidden
    @Operation(summary = "Create a new blueprint version", description = "Creates a new blueprint version")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Blueprint version created successfully",
                    content = @Content(schema = @Schema(implementation = BlueprintVersionRes.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BlueprintVersionRes createBlueprintVersion(
            @Parameter(description = "Blueprint version details", required = true)
            @RequestBody BlueprintVersionRes blueprintVersion
    ) {
        return blueprintVersionCrudService.createResource(blueprintVersion);
    }

    @Operation(summary = "Get data product version by UUID", description = "Retrieves a data product version by its UUID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Blueprint version found",
                    content = @Content(schema = @Schema(implementation = BlueprintVersionRes.class))),
            @ApiResponse(responseCode = "404", description = "Blueprint version not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/{uuid}")
    @ResponseStatus(HttpStatus.OK)
    public BlueprintVersionRes getBlueprintVersion(
            @Parameter(description = "Blueprint version UUID", required = true)
            @PathVariable("uuid") String uuid
    ) {
        return blueprintVersionCrudService.findOneResource(uuid);
    }

    @Operation(summary = "Search data blueprint versions", description = "Retrieves a paginated list of data blueprint versions based on search criteria. " +
            "The results can be sorted by any of the following properties: uuid, blueprintUuid, name, description, tag, createdAt, updatedAt. " +
            "Sort direction can be specified using 'asc' or 'desc' (e.g., 'sort=name,desc'). Returns short resources for better performance.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Blueprint versions found",
                    content = @Content(schema = @Schema(implementation = Page.class))),
            @ApiResponse(responseCode = "400", description = "Invalid search parameters or invalid sort property"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public Page<BlueprintVersionShortRes> searchBlueprintVersions(
            @Parameter(description = "Search options for filtering data product versions")
            BlueprintVersionSearchOptions searchOptions,
            @Parameter(description = "Pagination and sorting parameters. Default sort is by createdAt in descending order. " +
                    "Valid sort properties are: uuid, blueprintUuid, name, description, tag, createdAt, updatedAt")
            @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return blueprintVersionQueryService.findAllResourcesShort(pageable, searchOptions);
    }

    @Hidden
    @Operation(summary = "Update blueprint version by UUID", description = "Updates an existing blueprint version by its UUID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Blueprint version updated successfully",
                    content = @Content(schema = @Schema(implementation = BlueprintVersionRes.class))),
            @ApiResponse(responseCode = "404", description = "Blueprint version not found"),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PutMapping("/{uuid}")
    @ResponseStatus(HttpStatus.OK)
    public BlueprintVersionRes updateBlueprintVersion(
            @Parameter(description = "Blueprint version UUID", required = true)
            @PathVariable("uuid") String uuid,
            @Parameter(description = "Updated blueprint version details", required = true)
            @RequestBody BlueprintVersionRes blueprintVersion
    ) {
        return blueprintVersionCrudService.overwriteResource(uuid, blueprintVersion);
    }

    @Hidden
    @Operation(summary = "Delete blueprint version by UUID", description = "Deletes a blueprint version by its UUID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Blueprint version deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Blueprint version not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @DeleteMapping("/{uuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBlueprintVersion(
            @Parameter(description = "Blueprint version UUID", required = true)
            @PathVariable("uuid") String uuid
    ) {
        blueprintVersionCrudService.delete(uuid);
    }

}
