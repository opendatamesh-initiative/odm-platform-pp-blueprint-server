package org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.repository;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

/**
 * Request body to seed a blueprint's linked Git repository with files and push an initial commit.
 */
@Schema(name = "init_repository_command", description = "Command to write files into the remote repository and push them")
public class InitRepositoryCommandRes {

    @Schema(description = "Branch to check out before writing files; if omitted, the blueprint repository default branch is used")
    private String branch;

    @Schema(description = "Author name for the initial commit", requiredMode = Schema.RequiredMode.REQUIRED)
    private String authorName;

    @Schema(description = "Author email for the initial commit", requiredMode = Schema.RequiredMode.REQUIRED)
    private String authorEmail;

    @Schema(description = "Files to create or overwrite relative to the repository root", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<RepositoryResource> resources = new ArrayList<>();

    public InitRepositoryCommandRes() {
    }

    public List<RepositoryResource> getResources() {
        return resources;
    }

    public void setResources(List<RepositoryResource> resources) {
        this.resources = resources;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getAuthorEmail() {
        return authorEmail;
    }

    public void setAuthorEmail(String authorEmail) {
        this.authorEmail = authorEmail;
    }

    @Schema(name = "init_repository_resource", description = "A single file to write under the repository root")
    public static class RepositoryResource {

        @Schema(description = "Path relative to the repository root (e.g. manifest/blueprint.yaml)", requiredMode = Schema.RequiredMode.REQUIRED)
        private String filePath;

        @Schema(description = "Raw file body (any text: JSON, YAML, Markdown, etc.)", requiredMode = Schema.RequiredMode.REQUIRED)
        private String fileContent;

        public RepositoryResource() {
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public String getFileContent() {
            return fileContent;
        }

        public void setFileContent(String fileContent) {
            this.fileContent = fileContent;
        }
    }

}
