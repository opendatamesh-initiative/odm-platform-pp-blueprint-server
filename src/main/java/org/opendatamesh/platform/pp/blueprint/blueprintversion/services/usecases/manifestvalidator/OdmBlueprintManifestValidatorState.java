package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.manifestvalidator;

import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestInstantiation;

import java.util.HashSet;
import java.util.Set;

/**
 * Mutable state shared across Manifest blueprint validation sub-visitors during a single traversal.
 */
class OdmBlueprintManifestValidatorState {
    boolean hasComposition;
    ManifestInstantiation.InstantiationStrategy currentInstantiationStrategy;
    final Set<String> compositionModules = new HashSet<>();

    String currentParameterFieldPath;
    String currentParameterKey;
    String currentParameterTypeFieldPath;
    String currentParameterRequiredFieldPath;
    String currentParameterDefaultFieldPath;

    String currentProtectedResourceFieldPath;
    String currentProtectedResourceIntegrityFieldPath;

    String currentCompositionFieldPath;

    String currentInstantiationFieldPath;
    String currentCompositionLayoutFieldPath;
    String currentTargetFieldPath;
}
