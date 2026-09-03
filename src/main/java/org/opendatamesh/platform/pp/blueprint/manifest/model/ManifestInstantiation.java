package org.opendatamesh.platform.pp.blueprint.manifest.model;

import org.opendatamesh.platform.pp.blueprint.manifest.model.core.ManifestComponentBase;
import org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation.ManifestInstantiationRepository;
import org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation.ManifestInstantiationRoot;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestVisitor;

import java.util.ArrayList;
import java.util.List;

public class ManifestInstantiation extends ManifestComponentBase {

    private List<ManifestInstantiationRepository> repositories = new ArrayList<>();
    private ManifestInstantiationRoot root;

    public List<ManifestInstantiationRepository> getRepositories() {
        return repositories;
    }

    public void setRepositories(List<ManifestInstantiationRepository> repositories) {
        this.repositories = repositories;
    }

    public ManifestInstantiationRoot getRoot() {
        return root;
    }

    public void setRoot(ManifestInstantiationRoot root) {
        this.root = root;
    }

    public void accept(ManifestVisitor visitor) {
        visitor.visit(this);
    }
}
