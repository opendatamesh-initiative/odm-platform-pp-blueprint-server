package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources;

interface EvaluateProtectedResourcesIntegrityGitOutboundPort {

    CloseableWorkingTree clonePublishedDataProductVersion(ProductRepoLocator repo, String tag);
}
