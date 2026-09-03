package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.updatedataproduct;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.manifest.ManifestYamlTestSupport;
import org.opendatamesh.platform.pp.blueprint.manifest.model.Manifest;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateDataProductOdmBlueprintManifestOutboundPortTest {

    private final UpdateDataProductOdmBlueprintManifestOutboundPortImpl port =
            new UpdateDataProductOdmBlueprintManifestOutboundPortImpl();

    @Test
    void whenRootTargetPathNormalizationDiffersOnlyByDotSlashThenNoStructureFreezeIssue() throws IOException {
        JsonNode currentContent = ManifestYamlTestSupport.readYamlTreeFromClasspath(
                "/manifest/example-2.1-monorepo-no-composition.yaml");
        ObjectNode nextContent = currentContent.deepCopy();
        ArrayNode targets = (ArrayNode) nextContent.at("/instantiation/root/targets");
        ObjectNode target = (ObjectNode) targets.get(0);
        target.put("sourcePath", "");
        target.put("path", "");

        List<UpdateValidationIssue> issues = collect(currentContent, nextContent);

        assertThat(issues.stream().map(UpdateValidationIssue::fieldPath))
                .noneMatch(path -> path.contains("root.targets"));
    }

    @Test
    void whenParameterMappingDiffersThenNoStructureFreezeIssue() throws IOException {
        JsonNode currentContent = ManifestYamlTestSupport.readYamlTreeFromClasspath(
                "/manifest/example-2.2-monorepo-composition.yaml");
        ObjectNode nextContent = currentContent.deepCopy();
        ObjectNode mapping = (ObjectNode) nextContent.at("/composition/0/parameterMapping");
        mapping.putObject("region").put("$param", "environment");

        List<UpdateValidationIssue> issues = collect(currentContent, nextContent);

        assertThat(issues.stream().map(UpdateValidationIssue::problem))
                .noneMatch(problem -> problem.toLowerCase().contains("structure")
                        || problem.toLowerCase().contains("composition slot")
                        || problem.contains("Repository keys differ"));
    }

    @Test
    void whenRepositoryKeysDifferThenStructureFreezeIssueWithHint() throws IOException {
        JsonNode currentContent = ManifestYamlTestSupport.readYamlTreeFromClasspath(
                "/manifest/example-2.1-monorepo-no-composition.yaml");
        ObjectNode nextContent = currentContent.deepCopy();
        ArrayNode repositories = (ArrayNode) nextContent.at("/instantiation/repositories");
        ObjectNode extra = repositories.addObject();
        extra.put("key", "extra");
        extra.put("description", "extra repo");

        List<UpdateValidationIssue> issues = collect(currentContent, nextContent);

        assertThat(issues).anySatisfy(issue -> {
            assertThat(issue.problem()).contains("Repository keys differ");
            assertThat(issue.hint()).contains("content-only");
        });
    }

    private List<UpdateValidationIssue> collect(JsonNode currentContent, JsonNode nextContent) {
        BlueprintVersion current = versionWithContent(currentContent);
        BlueprintVersion next = versionWithContent(nextContent);
        return port.collectValidationIssues(current, next, Map.of(), List.of());
    }

    private static BlueprintVersion versionWithContent(JsonNode content) {
        BlueprintVersion version = new BlueprintVersion();
        version.setSpec(Manifest.SPEC_NAME);
        version.setSpecVersion("1.0.0");
        version.setContent(content);
        return version;
    }
}
