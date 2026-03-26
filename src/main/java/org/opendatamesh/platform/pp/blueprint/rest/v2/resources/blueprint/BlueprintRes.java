package org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprint;

import io.swagger.v3.oas.annotations.media.Schema;
import org.opendatamesh.platform.pp.blueprint.utils.resources.VersionedRes;

@Schema(name = "blueprints")
public class BlueprintRes extends VersionedRes {

    @Schema(description = "The unique identifier of the blueprint")
    private String uuid;

    @Schema(description = "The name of the blueprint")
    private String name;

    @Schema(description = "The name used as display name of the blueprint")
    private String displayName;

    @Schema(description = "The description of the blueprint")
    private String description;

    private BlueprintRepoRes blueprintRepo;

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
    
    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BlueprintRepoRes getBlueprintRepo() {
        return blueprintRepo;
    }

    public void setBlueprintRepo(BlueprintRepoRes blueprintRepo) {
        this.blueprintRepo = blueprintRepo;
    }

    @Schema(name = "blueprint_repositories")
    public static class BlueprintRepoRes {

    @Schema(description = "The unique identifier of the blueprint repository")
    private String uuid;

    @Schema(description = "The external identifier of the repository in the Git provider (e.g., repository name or ID)")
    private String externalIdentifier;

    @Schema(description = "The name of the repository")
    private String name;

    @Schema(description = "Optional description of the repository")
    private String description;

    @Schema(description = "The root path where the manifest are located in the repository")
    private String manifestRootPath;

    @Schema(description = "The path where the descriptor template is located in the repository")
    private String descriptorTemplatePath;

    @Schema(description = "The HTTP URL for cloning the repository", example = "https://github.com/my-company/data-product-repo.git")
    private String remoteUrlHttp;

    @Schema(description = "The SSH URL for cloning the repository", example = "git@github.com:my-company/data-product-repo.git")
    private String remoteUrlSsh;

    @Schema(description = "The default branch of the repository", example = "main")
    private String defaultBranch;

    @Schema(description = "The Git provider type hosting the repository", example = "GITHUB", allowableValues = {"AZURE", "BITBUCKET", "GITHUB", "GITLAB"})
    private BlueprintRepoProviderTypeRes blueprintRepoProviderType;

    @Schema(description = "The base URL of the Git provider", example = "https://github.com")
    private String providerBaseUrl;

    @Schema(description = "The owner identifier of the repository in the Git provider", example = "my-company")
    private String ownerId;

    @Schema(description = "The owner type of the repository", example = "ORGANIZATION", allowableValues = {"ORGANIZATION", "ACCOUNT"})
    private BlueprintRepoOwnerTypeRes ownerType;

    @Schema(description = "The UUID of the associated blueprint")
    private String blueprintUuid;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getExternalIdentifier() {
        return externalIdentifier;
    }

    public void setExternalIdentifier(String externalIdentifier) {
        this.externalIdentifier = externalIdentifier;
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

    public String getManifestRootPath() {
        return manifestRootPath;
    }

    public void setManifestRootPath(String manifestRootPath) {
        this.manifestRootPath = manifestRootPath;
    }

    public String getDescriptorTemplatePath() {
        return descriptorTemplatePath;
    }

    public void setDescriptorTemplatePath(String descriptorTemplatePath) {
        this.descriptorTemplatePath = descriptorTemplatePath;
    }

    public String getRemoteUrlHttp() {
        return remoteUrlHttp;
    }

    public void setRemoteUrlHttp(String remoteUrlHttp) {
        this.remoteUrlHttp = remoteUrlHttp;
    }

    public String getRemoteUrlSsh() {
        return remoteUrlSsh;
    }

    public void setRemoteUrlSsh(String remoteUrlSsh) {
        this.remoteUrlSsh = remoteUrlSsh;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public void setDefaultBranch(String defaultBranch) {
        this.defaultBranch = defaultBranch;
    }

    public BlueprintRepoProviderTypeRes getProviderType() {
        return blueprintRepoProviderType;
    }

    public void setProviderType(BlueprintRepoProviderTypeRes blueprintRepoProviderType) {
        this.blueprintRepoProviderType = blueprintRepoProviderType;
    }

    public String getProviderBaseUrl() {
        return providerBaseUrl;
    }

    public void setProviderBaseUrl(String providerBaseUrl) {
        this.providerBaseUrl = providerBaseUrl;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public BlueprintRepoOwnerTypeRes getOwnerType() {
        return ownerType;
    }

    public void setOwnerType(BlueprintRepoOwnerTypeRes ownerType) {
        this.ownerType = ownerType;
    }

    public String getBlueprintUuid() {
        return blueprintUuid;
    }

    public void setBlueprintUuid(String blueprintUuid) {
        this.blueprintUuid = blueprintUuid;
    }

    }
}
