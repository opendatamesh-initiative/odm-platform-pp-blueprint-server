package org.opendatamesh.platform.pp.blueprint.blueprint.entities;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.opendatamesh.platform.pp.blueprint.label.entities.Label;
import org.opendatamesh.platform.pp.blueprint.utils.entities.VersionedEntity;

import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;

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

    @Column(name = "blueprint_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private BlueprintType blueprintType;

    @OneToOne(mappedBy = "blueprint", orphanRemoval = true, cascade = CascadeType.ALL)
    @Fetch(FetchMode.SELECT)
    private BlueprintRepo blueprintRepo;

    @ManyToMany
    @JoinTable(
            name = "blueprints_labels",
            joinColumns = @JoinColumn(name = "blueprint_uuid"),
            inverseJoinColumns = @JoinColumn(name = "label_uuid")
    )
    @Fetch(FetchMode.SELECT)
    private Set<Label> labels = new HashSet<>();

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

    public BlueprintType getBlueprintType() {
        return blueprintType;
    }

    public void setBlueprintType(BlueprintType blueprintType) {
        this.blueprintType = blueprintType;
    }

    public BlueprintRepo getBlueprintRepo() {
        return blueprintRepo;
    }

    public void setBlueprintRepo(BlueprintRepo blueprintRepo) {
        this.blueprintRepo = blueprintRepo;
    }

    public Set<Label> getLabels() {
        return labels;
    }

    public void setLabels(Set<Label> labels) {
        this.labels = labels;
    }
}
