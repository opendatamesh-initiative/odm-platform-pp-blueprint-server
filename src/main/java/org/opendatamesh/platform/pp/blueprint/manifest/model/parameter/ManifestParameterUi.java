package org.opendatamesh.platform.pp.blueprint.manifest.model.parameter;

import org.opendatamesh.platform.pp.blueprint.manifest.model.core.ManifestComponentBase;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestParameterVisitor;

public class ManifestParameterUi extends ManifestComponentBase {

    private String group;
    private String label;
    private String description;
    private String formType;

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getFormType() {
        return formType;
    }

    public void setFormType(String formType) {
        this.formType = formType;
    }

    public void accept(ManifestParameterVisitor visitor) {
        visitor.visit(this);
    }
}
