package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources;

import java.util.List;

public interface EvaluateProtectedResourcesIntegrityPresenter {

    void presentNotApplicable(String reason);

    void presentPassed(String message);

    void presentFailed(List<ProtectedResourceMismatch> mismatches, String message);

    void presentInfrastructureFailure(String message);
}
