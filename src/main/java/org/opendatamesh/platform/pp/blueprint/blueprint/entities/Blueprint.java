package org.opendatamesh.platform.pp.blueprint.blueprint.entities;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.opendatamesh.platform.pp.blueprint.utils.entities.VersionedEntity;

import jakarta.persistence.*;

@Entity
@Table(name = "blueprints")
public class Blueprint extends VersionedEntity {
    
    @Id
    @Column(name = "uuid")
    @GeneratedValue(strategy = GenerationType.UUID)
    private String uuid;

    @Column(name = "name")
    private String name;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "description")
    private String description;

    @OneToOne(mappedBy = "blueprint", orphanRemoval = true, cascade = CascadeType.ALL)
    @Fetch(FetchMode.SELECT)
    private BlueprintRepo blueprintRepo;

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

    public BlueprintRepo getBlueprintRepo() {
        return blueprintRepo;
    }

    public void setBlueprintRepo(BlueprintRepo blueprintRepo) {
        this.blueprintRepo = blueprintRepo;
    }
}
