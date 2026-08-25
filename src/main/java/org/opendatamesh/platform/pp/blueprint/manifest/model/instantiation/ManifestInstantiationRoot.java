package org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation;

import org.opendatamesh.platform.pp.blueprint.manifest.model.core.ManifestComponentBase;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestInstantiationVisitor;

import java.util.ArrayList;
import java.util.List;

public class ManifestInstantiationRoot extends ManifestComponentBase {

    private List<ManifestTarget> targets = new ArrayList<>();

    public List<ManifestTarget> getTargets() {
        return targets;
    }

    public void setTargets(List<ManifestTarget> targets) {
        this.targets = targets;
    }

    public void accept(ManifestInstantiationVisitor visitor) {
        visitor.visit(this);
    }
}
