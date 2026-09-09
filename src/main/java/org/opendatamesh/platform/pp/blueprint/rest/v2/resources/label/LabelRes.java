package org.opendatamesh.platform.pp.blueprint.rest.v2.resources.label;

import io.swagger.v3.oas.annotations.media.Schema;
import org.opendatamesh.platform.pp.blueprint.utils.resources.VersionedRes;

@Schema(name = "labels")
public class LabelRes extends VersionedRes {

    @Schema(description = "The unique identifier of the label")
    private String uuid;

    @Schema(description = "The name of the label")
    private String name;

    @Schema(description = "The description of the label", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String description;

    @Schema(description = "The color of the label as #RRGGBB", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "#0E8A16")
    private String color;

    @Schema(
            description = "Optional catalog group (free text; presentation metadata; not a filter key). " +
                    "Values are trimmed on write; whitespace-only is stored as no group. Distinct groups are exact trimmed strings.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    private String group;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }
}
