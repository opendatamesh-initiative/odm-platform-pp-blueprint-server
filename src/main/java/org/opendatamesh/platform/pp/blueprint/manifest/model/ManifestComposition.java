package org.opendatamesh.platform.pp.blueprint.manifest.model;

import com.fasterxml.jackson.databind.JsonNode;
import org.opendatamesh.platform.pp.blueprint.manifest.model.core.ManifestComponentBase;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestVisitor;

import java.util.LinkedHashMap;
import java.util.Map;

public class ManifestComposition extends ManifestComponentBase {

    private String module;
    private String blueprintName;
    private String blueprintVersion;
    private Map<String, JsonNode> parameterMapping = new LinkedHashMap<>();

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getBlueprintName() {
        return blueprintName;
    }

    public void setBlueprintName(String blueprintName) {
        this.blueprintName = blueprintName;
    }

    public String getBlueprintVersion() {
        return blueprintVersion;
    }

    public void setBlueprintVersion(String blueprintVersion) {
        this.blueprintVersion = blueprintVersion;
    }

    public Map<String, JsonNode> getParameterMapping() {
        return parameterMapping;
    }

    public void setParameterMapping(Map<String, JsonNode> parameterMapping) {
        this.parameterMapping = parameterMapping;
    }

    public void accept(ManifestVisitor visitor) {
        visitor.visit(this);
    }
}
