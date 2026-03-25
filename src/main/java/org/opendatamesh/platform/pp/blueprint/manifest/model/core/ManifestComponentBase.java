package org.opendatamesh.platform.pp.blueprint.manifest.model.core;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.core.ManifestComponentBaseVisitor;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Base type for manifest schema objects, following the extensibility pattern used in
 * {@code org.opendatamesh.dpds.model.core.ComponentBase}: unknown properties are captured for forward compatibility.
 */
public class ManifestComponentBase implements Serializable {

    private Map<String, JsonNode> additionalProperties = new HashMap<>();

    @JsonIgnore
    private Map<String, ManifestComponentBase> parsedProperties = new HashMap<>();

    public <T extends ManifestComponentBase> void accept(ManifestComponentBaseVisitor<T> visitor) {
        // Type check should be done in visitor implementations
        @SuppressWarnings("unchecked")
        T self = (T) this;
        visitor.visit(self);
    }

    @JsonAnySetter
    public void addAdditionalProperty(String key, JsonNode value) {
        additionalProperties.put(key, value);
    }

    @JsonAnyGetter
    public Map<String, JsonNode> getAdditionalProperties() {
        return additionalProperties;
    }

    @JsonIgnore
    public void setAdditionalProperties(Map<String, JsonNode> additionalProperties) {
        this.additionalProperties = additionalProperties;
    }

    @JsonIgnore
    public <T extends ManifestComponentBase> void addParsedProperty(String key, T value) {
        parsedProperties.put(key, value);
    }

    @JsonIgnore
    public Map<String, ManifestComponentBase> getParsedProperties() {
        return parsedProperties;
    }

    @JsonIgnore
    public void setParsedProperties(Map<String, ManifestComponentBase> parsedProperties) {
        this.parsedProperties = parsedProperties;
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        out.writeObject(new ObjectMapper().writeValueAsString(additionalProperties));
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        String json = (String) in.readObject();
        additionalProperties = new ObjectMapper().readValue(json, new TypeReference<Map<String, JsonNode>>() {
        });
    }
}
