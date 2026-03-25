package org.opendatamesh.platform.pp.blueprint.manifest.model.parameter;

import com.fasterxml.jackson.databind.JsonNode;
import org.opendatamesh.platform.pp.blueprint.manifest.model.core.ManifestComponentBase;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestParameterVisitor;

import java.util.ArrayList;
import java.util.List;


public class ManifestParameterValidation extends ManifestComponentBase {

    private List<JsonNode> allowedValues = new ArrayList<>();
    private String format;
    private String pattern;
    private Number min;
    private Number max;

    public List<JsonNode> getAllowedValues() {
        return allowedValues;
    }

    public void setAllowedValues(List<JsonNode> allowedValues) {
        this.allowedValues = allowedValues;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public Number getMin() {
        return min;
    }

    public void setMin(Number min) {
        this.min = min;
    }

    public Number getMax() {
        return max;
    }

    public void setMax(Number max) {
        this.max = max;
    }

    public void accept(ManifestParameterVisitor visitor) {
        visitor.visit(this);
    }
}
