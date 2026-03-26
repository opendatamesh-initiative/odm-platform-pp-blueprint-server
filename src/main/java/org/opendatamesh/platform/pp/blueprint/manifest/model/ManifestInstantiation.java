package org.opendatamesh.platform.pp.blueprint.manifest.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.opendatamesh.platform.pp.blueprint.manifest.model.core.ManifestComponentBase;
import org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation.ManifestInstantiationCompositionLayout;
import org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation.ManifestInstantiationTarget;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestVisitor;

import java.util.ArrayList;
import java.util.List;

public class ManifestInstantiation extends ManifestComponentBase {

    private InstantiationStrategy strategy;
    private List<ManifestInstantiationCompositionLayout> compositionLayout = new ArrayList<>();
    private List<ManifestInstantiationTarget> targets = new ArrayList<>();

    public InstantiationStrategy getStrategy() {
        return strategy;
    }

    public void setStrategy(InstantiationStrategy strategy) {
        this.strategy = strategy;
    }

    public List<ManifestInstantiationCompositionLayout> getCompositionLayout() {
        return compositionLayout;
    }

    public void setCompositionLayout(List<ManifestInstantiationCompositionLayout> compositionLayout) {
        this.compositionLayout = compositionLayout;
    }

    public List<ManifestInstantiationTarget> getTargets() {
        return targets;
    }

    public void setTargets(List<ManifestInstantiationTarget> targets) {
        this.targets = targets;
    }

    public void accept(ManifestVisitor visitor) {
        visitor.visit(this);
    }

    public enum InstantiationStrategy {
        @JsonProperty("monorepo")
        MONOREPO,
        @JsonProperty("polyrepo")
        POLYREPO
    }
}
