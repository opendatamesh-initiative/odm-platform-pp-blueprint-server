package org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.updatedataproduct;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@Schema(name = "update_data_product_result")
public class UpdateDataProductResultRes {

    @Schema(description = "Per-target update outcomes")
    private List<UpdateDataProductTargetResultRes> results = new ArrayList<>();

    @Schema(description = "User-visible side-operation warnings (e.g. PR open failure after a successful update). Empty when none.")
    private List<String> warnings = new ArrayList<>();

    public List<UpdateDataProductTargetResultRes> getResults() {
        return results;
    }

    public void setResults(List<UpdateDataProductTargetResultRes> results) {
        this.results = results;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }
}
