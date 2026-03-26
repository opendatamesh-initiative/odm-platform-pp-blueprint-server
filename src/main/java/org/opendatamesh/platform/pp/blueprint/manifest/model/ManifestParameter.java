package org.opendatamesh.platform.pp.blueprint.manifest.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import org.opendatamesh.platform.pp.blueprint.manifest.model.core.ManifestComponentBase;
import org.opendatamesh.platform.pp.blueprint.manifest.model.parameter.ManifestParameterUi;
import org.opendatamesh.platform.pp.blueprint.manifest.model.parameter.ManifestParameterValidation;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestVisitor;


public class ManifestParameter extends ManifestComponentBase {

    private String key;
    private ManifestParameterType type;
    private Boolean required;
    @JsonProperty("default")
    private JsonNode defaultValue;
    private ManifestParameterValidation validation;
    private ManifestParameterUi ui;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public ManifestParameterType getType() {
        return type;
    }

    public void setType(ManifestParameterType type) {
        this.type = type;
    }

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    public JsonNode getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(JsonNode defaultValue) {
        this.defaultValue = defaultValue;
    }

    public ManifestParameterValidation getValidation() {
        return validation;
    }

    public void setValidation(ManifestParameterValidation validation) {
        this.validation = validation;
    }

    public ManifestParameterUi getUi() {
        return ui;
    }

    public void setUi(ManifestParameterUi ui) {
        this.ui = ui;
    }

    public void accept(ManifestVisitor visitor) {
        visitor.visit(this);
    }

    public enum ManifestParameterType {
        @JsonProperty("string")
        STRING,
        @JsonProperty("integer")
        INTEGER,
        @JsonProperty("boolean")
        BOOLEAN,
        @JsonProperty("array")
        ARRAY,
        @JsonProperty("object")
        OBJECT
    }
}
