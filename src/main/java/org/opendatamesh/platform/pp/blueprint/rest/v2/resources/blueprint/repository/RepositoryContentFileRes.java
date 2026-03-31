package org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint.repository;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A single file read from a blueprint's linked Git repository (UTF-8 text), aligned with
 * {@link InitRepositoryCommandRes.RepositoryResource} naming for symmetry with POST repository-content.
 */
@Schema(name = "repository_content_file", description = "Repository-relative path and file body read from the clone")
public class RepositoryContentFileRes {

    @Schema(description = "Path relative to the repository root", requiredMode = Schema.RequiredMode.REQUIRED)
    private String filePath;

    @Schema(description = "File body as UTF-8 text", requiredMode = Schema.RequiredMode.REQUIRED)
    private String fileContent;

    public RepositoryContentFileRes() {
    }

    public RepositoryContentFileRes(String filePath, String fileContent) {
        this.filePath = filePath;
        this.fileContent = fileContent;
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
