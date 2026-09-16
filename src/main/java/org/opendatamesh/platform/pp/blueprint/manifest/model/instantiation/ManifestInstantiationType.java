package org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ManifestInstantiationType {

    ROOT("root"),
    MODULE("module");

    private final String value;

    ManifestInstantiationType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ManifestInstantiationType fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (ManifestInstantiationType type : values()) {
            if (type.value.equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown instantiation type: " + value);
    }
}
