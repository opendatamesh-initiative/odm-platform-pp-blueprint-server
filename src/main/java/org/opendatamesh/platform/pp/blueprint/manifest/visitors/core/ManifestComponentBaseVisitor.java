package org.opendatamesh.platform.pp.blueprint.manifest.visitors.core;

import org.opendatamesh.platform.pp.blueprint.manifest.model.core.ManifestComponentBase;

public interface ManifestComponentBaseVisitor<T extends ManifestComponentBase> {
    void visit(T componentBase);
}
