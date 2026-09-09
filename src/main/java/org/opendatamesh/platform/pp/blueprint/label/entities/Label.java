package org.opendatamesh.platform.pp.blueprint.label.entities;

import org.opendatamesh.platform.pp.blueprint.utils.entities.VersionedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "labels")
public class Label extends VersionedEntity {

    @Id
    @Column(name = "uuid")
    @GeneratedValue(strategy = GenerationType.UUID)
    private String uuid;

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "color", length = 32)
    private String color;

    @Column(name = "label_group", length = 255)
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
