package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources;

interface EvaluateProtectedResourcesIntegrityDigestOutboundPort {

    DigestResult computeDigest(WorkingTree tree, String declaredPath);
}
