package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.manifestautofiller;

import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestVisitor;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestParameterVisitor;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestProtectedResourceVisitor;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestInstantiationVisitor;
import org.opendatamesh.platform.pp.blueprint.manifest.model.Manifest;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestComposition;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestParameter;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestProtectedResource;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestInstantiation;
import org.opendatamesh.platform.pp.blueprint.manifest.model.parameter.ManifestParameterValidation;
import org.opendatamesh.platform.pp.blueprint.manifest.model.parameter.ManifestParameterUi;
import org.opendatamesh.platform.pp.blueprint.manifest.model.protectedresource.ManifestProtectedResourceIntegrity;
import org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation.ManifestInstantiationCompositionLayout;
import org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation.ManifestInstantiationTarget;
import org.springframework.util.StringUtils;
import java.util.List;

class OdmBlueprintManifestAutoFillerVisitor implements ManifestVisitor, ManifestParameterVisitor,
ManifestProtectedResourceVisitor, ManifestInstantiationVisitor{

    private final String blueprintName;

    OdmBlueprintManifestAutoFillerVisitor(String blueprintName) {
        this.blueprintName = blueprintName;
    }

    @Override
    public void visit(Manifest manifest) {
        if (!StringUtils.hasText(manifest.getSpec())) {
            manifest.setSpec(Manifest.SPEC_NAME);
        }
        if (!StringUtils.hasText(manifest.getSpecVersion())) {
            manifest.setSpecVersion("1.0.0");
        }
        if (!StringUtils.hasText(manifest.getVersion())) {
            manifest.setVersion("1.0.0");
        }
        if (!StringUtils.hasText(manifest.getName())) {
            manifest.setName(blueprintName);
        }

        List<ManifestParameter> parameters = manifest.getParameters();
        if (parameters != null) {
            for (int i = 0; i < parameters.size(); i++) {
                ManifestParameter parameter = parameters.get(i);
                if (parameter != null) {
                    parameter.accept(this);
                }
            }
        }

        if (manifest.getInstantiation().getStrategy() == null) {
            manifest.getInstantiation().accept(this);
        }
    }

    @Override
    public void visit(ManifestParameter manifestParameter) {
        if (manifestParameter.getKey() != null && !manifestParameter.getKey().isEmpty()) {
            if (manifestParameter.getType() == null) {
                manifestParameter.setType(ManifestParameter.ManifestParameterType.STRING);
            }
        }
    }

    @Override
    public void visit(ManifestProtectedResource manifestProtectedResource) {
        // No auto fill for protected resources
    }

    @Override
    public void visit(ManifestInstantiation manifestInstantiation) {
        if (manifestInstantiation.getStrategy() == null) {
            manifestInstantiation.setStrategy(ManifestInstantiation.InstantiationStrategy.MONOREPO);
        }
    }
    
    @Override
    public void visit(ManifestParameterValidation validation) {
        // No auto fill for parameter validation
    }

    @Override
    public void visit(ManifestParameterUi ui) {
        // No auto fill for parameter ui
    }

    @Override
    public void visit(ManifestProtectedResourceIntegrity integrity) {
        // No auto fill for protected resource integrity
    }
    
    @Override
    public void visit(ManifestInstantiationCompositionLayout compositionLayout) {
        // No auto fill for instantiation composition layout
    }

    @Override
    public void visit(ManifestInstantiationTarget target) {
        // No auto fill for instantiation target
    }

    @Override
    public void visit(ManifestComposition manifestComposition) {
        // No auto fill for manifest composition
    }
}
