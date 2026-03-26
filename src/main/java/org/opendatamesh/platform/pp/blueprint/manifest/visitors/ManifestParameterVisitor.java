package org.opendatamesh.platform.pp.blueprint.manifest.visitors;

import org.opendatamesh.platform.pp.blueprint.manifest.model.parameter.ManifestParameterUi;
import org.opendatamesh.platform.pp.blueprint.manifest.model.parameter.ManifestParameterValidation;

public interface ManifestParameterVisitor {

    void visit(ManifestParameterValidation validation);

    void visit(ManifestParameterUi ui);
}
