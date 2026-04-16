package org.opendatamesh.platform.pp.blueprint.rest.v2.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.opendatamesh.platform.pp.blueprint.blueprint.services.BlueprintUseCasesService;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.usecases.register.RegisterBlueprintCommandRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.usecases.register.RegisterBlueprintResponseRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.usecases.updatedocumentationfields.BlueprintUpdateDocumentationFieldsCommandRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.usecases.updatedocumentationfields.UpdateBlueprintDocumentationFieldsResponseRes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v2/pp/blueprint/blueprints", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Blueprint use cases", description = "Public blueprint registration and related flows")
public class BlueprintUseCaseController {

    @Autowired
    private BlueprintUseCasesService blueprintUseCasesService;

    @Operation(summary = "Register a blueprint", description = "Creates a new blueprint with repository configuration using semantic validation (URLs, paths).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Blueprint registered successfully",
                    content = @Content(schema = @Schema(implementation = RegisterBlueprintResponseRes.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request (including semantic validation failure)"),
            @ApiResponse(responseCode = "409", description = "Conflict (e.g. duplicate blueprint name)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterBlueprintResponseRes registerBlueprint(
            @Parameter(description = "Registration command", required = true)
            @RequestBody RegisterBlueprintCommandRes command
    ) {
        return blueprintUseCasesService.registerBlueprint(command);
    }

    @Operation(summary = "Update blueprint documentation fields", description = "Updates display name, description, and optionally nested repository configuration")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Blueprint updated successfully",
                    content = @Content(schema = @Schema(implementation = UpdateBlueprintDocumentationFieldsResponseRes.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request (including semantic validation failure)"),
            @ApiResponse(responseCode = "404", description = "Blueprint not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/update-documentation-fields")
    public UpdateBlueprintDocumentationFieldsResponseRes updateBlueprintDocumentationFields(
            @Parameter(description = "Update command", required = true) 
            @RequestBody BlueprintUpdateDocumentationFieldsCommandRes command
    ) {
        return blueprintUseCasesService.updateBlueprintDocumentationFields(command);
    }
}
