package org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.repository;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

/**
 * Response for GET repository-content: files read from the clone at the requested pointer.
 */
@Schema(name = "repository_content_read", description = "Files read from the blueprint's linked Git repository")
public class RepositoryContentReadRes {

    @ArraySchema(schema = @Schema(implementation = RepositoryContentFileRes.class))
    @Schema(description = "Repository-relative paths and UTF-8 file bodies")
    private List<RepositoryContentFileRes> resources = new ArrayList<>();

    public RepositoryContentReadRes() {
    }

    public RepositoryContentReadRes(List<RepositoryContentFileRes> resources) {
        this.resources = resources != null ? new ArrayList<>(resources) : new ArrayList<>();
    }

    public List<RepositoryContentFileRes> getResources() {
        return resources;
    }

    public void setResources(List<RepositoryContentFileRes> resources) {
        this.resources = resources != null ? resources : new ArrayList<>();
    }
}
