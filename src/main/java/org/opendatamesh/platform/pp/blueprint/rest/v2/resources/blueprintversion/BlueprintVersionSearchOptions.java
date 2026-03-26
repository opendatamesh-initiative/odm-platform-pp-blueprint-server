package org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;

public class BlueprintVersionSearchOptions {

    @Parameter(
            description = "Filter blueprint versions by parent blueprint UUID. Exact match.",
            schema = @Schema(type = "string")
    )
    private String blueprintUuid;

    @Parameter(
            description = "Filter blueprint versions by name. Exact match (case-insensitive).",
            schema = @Schema(type = "string")
    )
    private String name;

    @Parameter(
            description = "Filter blueprint versions by tag. Exact match (case-insensitive).",
            schema = @Schema(type = "string")
    )
    private String tag;

    @Parameter(
            description = "Filter blueprint versions by version number. Exact match (case-insensitive).",
            schema = @Schema(type = "string")
    )
    private String versionNumber;

    @Parameter(
            description = "Filter blueprint versions with partial match on name (case-insensitive).",
            schema = @Schema(type = "string")
    )
    private String search;

    public String getBlueprintUuid() {
        return blueprintUuid;
    }

    public void setBlueprintUuid(String blueprintUuid) {
        this.blueprintUuid = blueprintUuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(String versionNumber) {
        this.versionNumber = versionNumber;
    }

    public String getSearch() {
        return search;
    }

    public void setSearch(String search) {
        this.search = search;
    }
}
