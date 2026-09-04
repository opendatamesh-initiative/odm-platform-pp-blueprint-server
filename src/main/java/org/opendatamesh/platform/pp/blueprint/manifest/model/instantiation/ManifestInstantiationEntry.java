package org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation;

import org.opendatamesh.platform.pp.blueprint.manifest.model.core.ManifestComponentBase;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestVisitor;

import java.util.ArrayList;
import java.util.List;

public class ManifestInstantiationEntry extends ManifestComponentBase {

    private ManifestInstantiationType type;
    /**
     * Required when {@link #type} is {@link ManifestInstantiationType#MODULE}; must match
     * {@code composition[].module}.
     */
    private String moduleName;
    private List<ManifestTarget> targets = new ArrayList<>();

    public ManifestInstantiationType getType() {
        return type;
    }

    public void setType(ManifestInstantiationType type) {
        this.type = type;
    }

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    public List<ManifestTarget> getTargets() {
        return targets;
    }

    public void setTargets(List<ManifestTarget> targets) {
        this.targets = targets;
    }

    public void accept(ManifestVisitor visitor) {
        visitor.visit(this);
    }
}
