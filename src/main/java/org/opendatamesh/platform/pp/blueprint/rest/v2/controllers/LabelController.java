package org.opendatamesh.platform.pp.blueprint.rest.v2.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.opendatamesh.platform.pp.blueprint.label.services.core.LabelService;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.label.LabelRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.label.LabelSearchOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v2/pp/blueprint/labels", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Labels", description = "Endpoints for managing blueprint categorization labels")
public class LabelController {

    @Autowired
    private LabelService labelService;

    @Operation(summary = "Create a new label", description = "Creates a new blueprint categorization label")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Label created successfully",
                    content = @Content(schema = @Schema(implementation = LabelRes.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
            @ApiResponse(responseCode = "409", description = "A label with the same name already exists"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LabelRes createLabel(
            @Parameter(description = "Label creation request")
            @RequestBody LabelRes label
    ) {
        return labelService.createResource(label);
    }

    @Operation(summary = "Get a label by ID", description = "Retrieves a label by its ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Label retrieved successfully",
                    content = @Content(schema = @Schema(implementation = LabelRes.class))),
            @ApiResponse(responseCode = "404", description = "Label not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/{uuid}")
    public LabelRes getLabel(
            @Parameter(description = "Label UUID")
            @PathVariable("uuid") String uuid
    ) {
        return labelService.findOneResource(uuid);
    }

    @Operation(summary = "Search labels", description = "Retrieves a paginated list of labels based on search criteria. " +
            "The results can be sorted by any of the following properties: uuid, name, description, color, group, " +
            "createdAt, updatedAt. Sort direction can be specified using 'asc' or 'desc' (e.g., 'sort=name,desc').")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Labels found",
                    content = @Content(schema = @Schema(implementation = Page.class))),
            @ApiResponse(responseCode = "400", description = "Invalid search parameters or invalid sort property"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public Page<LabelRes> searchLabels(
            @Parameter(description = "Search options for filtering labels")
            LabelSearchOptions searchOptions,
            @Parameter(description = "Pagination and sorting parameters. Default sort is by createdAt in descending order. " +
                    "Valid sort properties are: uuid, name, description, color, group, createdAt, updatedAt")
            @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return labelService.findAllResourcesFiltered(pageable, searchOptions);
    }

    @Operation(summary = "Update label", description = "Updates an existing label by its UUID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Label updated successfully",
                    content = @Content(schema = @Schema(implementation = LabelRes.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
            @ApiResponse(responseCode = "404", description = "Label not found"),
            @ApiResponse(responseCode = "409", description = "A label with the same name already exists"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PutMapping("/{uuid}")
    @ResponseStatus(HttpStatus.OK)
    public LabelRes updateLabel(
            @Parameter(description = "Label UUID", required = true)
            @PathVariable("uuid") String uuid,
            @Parameter(description = "Updated label details", required = true)
            @RequestBody LabelRes label
    ) {
        return labelService.overwriteResource(uuid, label);
    }

    @Operation(summary = "Delete label", description = "Deletes a label by its UUID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Label deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Label not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @DeleteMapping("/{uuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLabel(
            @Parameter(description = "Label UUID", required = true)
            @PathVariable("uuid") String uuid
    ) {
        labelService.delete(uuid);
    }
}
