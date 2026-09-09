package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.publish;

record PublishCompositionIdentity(
        String moduleAlias,
        String blueprintName,
        String blueprintVersion,
        String fieldPath
) {
}
