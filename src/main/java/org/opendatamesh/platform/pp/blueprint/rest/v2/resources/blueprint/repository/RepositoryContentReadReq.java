package org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.repository;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(
        name = "repository_content_read_request",
        description = "Repository content read request parameters (path variable + query parameters)"
)
public class RepositoryContentReadReq {

    @Parameter(description = "Blueprint UUID")
    @Schema(description = "Blueprint UUID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String uuid;

    @Parameter(description = "Branch name (mutually exclusive with tag and commit)")
    @Schema(description = "Branch name (mutually exclusive with tag and commit)")
    private String branch;

    @Parameter(description = "Tag name (mutually exclusive with branch and commit)")
    @Schema(description = "Tag name (mutually exclusive with branch and commit)")
    private String tag;

    @Parameter(description = "Commit hash (mutually exclusive with branch and tag)")
    @Schema(description = "Commit hash (mutually exclusive with branch and tag)")
    private String commit;

    @Parameter(description = "Repo-relative file path to read; repeat for multiple paths. Omit to use blueprint default paths.")
    @Schema(description = "Repo-relative file path to read; repeat for multiple paths. Omit to use blueprint default paths.")
    private List<String> path;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getCommit() {
        return commit;
    }

    public void setCommit(String commit) {
        this.commit = commit;
    }

    public List<String> getPath() {
        return path;
    }

    public void setPath(List<String> path) {
        this.path = path;
    }
}
