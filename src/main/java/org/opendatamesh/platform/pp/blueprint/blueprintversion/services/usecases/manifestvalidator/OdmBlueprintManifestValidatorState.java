package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.manifestvalidator;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Mutable state shared across Manifest blueprint validation sub-visitors during a single traversal.
 */
class OdmBlueprintManifestValidatorState {
    boolean hasComposition;
    final Set<String> compositionModules = new HashSet<>();
    final Set<String> repositoryKeys = new LinkedHashSet<>();

    String currentParameterFieldPath;
    String currentParameterKey;
    String currentParameterTypeFieldPath;
    String currentParameterRequiredFieldPath;
    String currentParameterDefaultFieldPath;

    String currentProtectedResourceFieldPath;
    String currentProtectedResourceIntegrityFieldPath;

    String currentCompositionFieldPath;

    String currentInstantiationFieldPath;
    String currentTargetFieldPath;
}
