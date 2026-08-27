package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources;

interface EvaluateProtectedResourcesIntegrityGitOutboundPort {

    WorkingTree clonePublishedDataProductVersion(ProductRepoLocator repo, String tag);
}
