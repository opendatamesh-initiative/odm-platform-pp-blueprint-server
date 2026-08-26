package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources;

import java.nio.file.Path;

interface EvaluateProtectedResourcesIntegrityDigestOutboundPort {

    DigestResult digest(Path repoRoot, String declaredPath);
}
