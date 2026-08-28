package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.instantiate;

import com.fasterxml.jackson.databind.JsonNode;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Applies instantiation routes into a target workspace and records parent lineage on the root target.
 */
interface InstantiateBlueprintVersionTemplatingOutboundPort {

    /**
     * Velocity-renders the source subtree at {@code sourcePath} and copies it into
     * {@code destinationPath} under the target workspace. Skips {@code .git}. Does not write lineage sidecars.
     */
    void applyRoute(
            Path sourceRoot,
            String sourcePath,
            Path targetRoot,
            String destinationPath,
            Map<String, JsonNode> parameters);

    /**
     * When {@code descriptorTemplatePath} is non-blank, Velocity-renders that template from the parent source
     * into {@code rootTarget} at the path derived from the template (same relative path, {@code .vm} stripped).
     */
    void renderDescriptorToRoot(
            Path parentSourceRoot,
            String descriptorTemplatePath,
            Path rootTarget,
            Map<String, JsonNode> parameters);

    /**
     * Records parent-only provenance on the designated root target: descriptor enrichment plus
     * {@code .odm/blueprint/} README/manifest relocate for files present on this tree.
     */
    void recordParentLineage(
            Path rootTarget,
            BlueprintVersion parentVersion,
            Map<String, JsonNode> parentResolvedParameters);

    /**
     * Moves the module's {@code BlueprintRepo} file pointers ({@code readmePath}, {@code manifestRootPath})
     * under {@code .odm/<moduleAlias>} on the target. Prefers files already rendered at
     * {@code destinationPaths}; otherwise copies from {@code moduleSourceRoot}.
     */
    void relocateModuleReferencedFiles(
            Path targetRoot,
            String moduleAlias,
            BlueprintVersion moduleVersion,
            Path moduleSourceRoot,
            List<String> destinationPaths);
}
