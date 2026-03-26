package org.opendatamesh.platform.pp.blueprint.blueprint.entities;

import jakarta.persistence.*;

@Entity
@Table(name = "blueprint_repositories")
public class BlueprintRepo {

    @Id
    @Column(name = "uuid")
    @GeneratedValue(strategy = GenerationType.UUID)
    private String uuid;

    @Column(name = "external_identifier")
    private String externalIdentifier;

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "manifest_root_path")
    private String manifestRootPath;

    @Column(name = "descriptor_template_path")
    private String descriptorTemplatePath;

    @Column(name = "remote_url_http")
    private String remoteUrlHttp;

    @Column(name = "remote_url_ssh")
    private String remoteUrlSsh;

    @Column(name = "default_branch")
    private String defaultBranch;

    @Column(name = "provider_type")
    @Enumerated(EnumType.STRING)
    private BlueprintRepoProviderType blueprintRepoProviderType;

    @Column(name = "provider_base_url")
    private String providerBaseUrl;

    @Column(name = "owner_id")
    private String ownerId;

    @Column(name = "owner_type")
    @Enumerated(EnumType.STRING)
    private BlueprintRepoOwnerType ownerType;

    @Column(name = "blueprint_uuid", insertable = false, updatable = false)
    private String blueprintUuid;

    @OneToOne
    @JoinColumn(name = "blueprint_uuid", nullable = false, unique = true)
    private Blueprint blueprint;

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

    public BlueprintRepoProviderType getProviderType() {
        return blueprintRepoProviderType;
    }

    public void setProviderType(BlueprintRepoProviderType blueprintRepoProviderType) {
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

    public BlueprintRepoOwnerType getOwnerType() {
        return ownerType;
    }

    public void setOwnerType(BlueprintRepoOwnerType ownerType) {
        this.ownerType = ownerType;
    }

    public String getBlueprintUuid() {
        return blueprintUuid;
    }

    public void setBlueprintUuid(String blueprintUuid) {
        this.blueprintUuid = blueprintUuid;
    }

    public Blueprint getBlueprint() {
        return blueprint;
    }

    public void setBlueprint(Blueprint blueprint) {
        this.blueprint = blueprint;
    }
}
