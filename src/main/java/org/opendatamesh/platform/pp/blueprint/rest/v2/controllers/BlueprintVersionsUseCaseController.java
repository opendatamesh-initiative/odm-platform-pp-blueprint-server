package org.opendatamesh.platform.pp.blueprint.rest.v2.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.opendatamesh.platform.pp.blueprint.blueprintversion.services.BlueprintVersionUseCasesService;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.publish.PublishBlueprintVersionCommandRes;
import org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.publish.PublishBlueprintVersionResponseRes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v2/pp/blueprint/blueprints-versions", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Blueprint versions use cases", description = "Public blueprint version publication and related flows")
public class BlueprintVersionsUseCaseController {

    @Autowired
    private BlueprintVersionUseCasesService blueprintVersionUseCasesService;

    @Operation(summary = "Publish a blueprint version", description = "Creates a new blueprint version using semantic validation.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Blueprint published successfully",
                    content = @Content(schema = @Schema(implementation = PublishBlueprintVersionResponseRes.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request (including semantic validation failure)"),
            @ApiResponse(responseCode = "409", description = "Conflict (e.g. duplicate blueprint version name)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/publish")
    @ResponseStatus(HttpStatus.CREATED)
    public PublishBlueprintVersionResponseRes publishBlueprintVersion(
            @Parameter(description = "Publication command", required = true)
            @RequestBody PublishBlueprintVersionCommandRes command
    ) {
        return blueprintVersionUseCasesService.publishBlueprintVersion(command);
    }
}
