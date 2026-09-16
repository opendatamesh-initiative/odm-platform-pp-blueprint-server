package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources;

interface EvaluateProtectedResourcesIntegrityDigestOutboundPort {

    DigestResult computeDigest(CloseableWorkingTree tree, String declaredPath);
}
