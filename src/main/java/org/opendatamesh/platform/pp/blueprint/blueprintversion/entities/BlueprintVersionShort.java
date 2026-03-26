package org.opendatamesh.platform.pp.blueprint.blueprintversion.entities;

import org.opendatamesh.platform.pp.blueprint.utils.entities.VersionedEntity;
import jakarta.persistence.*;
import org.springframework.util.StringUtils;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint;

@Entity
@Table(name = "blueprint_versions")
public class BlueprintVersionShort extends VersionedEntity {
    @Id
    @Column(name = "uuid")
    @GeneratedValue(strategy = GenerationType.UUID)
    private String uuid;

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "tag")
    private String tag;

    @Column(name = "version_number")
    private String versionNumber;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "blueprint_uuid", insertable = false, updatable = false)
    private String blueprintUuid;

    @ManyToOne
    @JoinColumn(name = "blueprint_uuid", nullable = false)
    private Blueprint blueprint;

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
    
    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
    
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
        if (blueprint != null) {
            this.blueprintUuid = blueprint.getUuid();
        }
    }



    /**
     * Initialize created_by and updated_by on creation.
     * When a new DataProductVersion is created, both created_by and updated_by
     * should be set to the same value (created_by).
     */
    @PrePersist
    public void onPrePersist() {
        if (StringUtils.hasText(this.createdBy) && !StringUtils.hasText(this.updatedBy)) {
            this.updatedBy = this.createdBy;
        }
    }
    
}
