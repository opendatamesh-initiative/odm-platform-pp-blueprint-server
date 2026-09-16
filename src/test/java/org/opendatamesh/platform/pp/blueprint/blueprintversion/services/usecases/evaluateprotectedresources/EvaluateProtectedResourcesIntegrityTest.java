package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.Blueprint;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.BlueprintRepo;
import org.opendatamesh.platform.pp.blueprint.blueprint.entities.BlueprintRepoProviderType;
import org.opendatamesh.platform.pp.blueprint.blueprintversion.entities.BlueprintVersion;
import org.opendatamesh.platform.pp.blueprint.manifest.model.Manifest;
import org.opendatamesh.platform.pp.blueprint.manifest.model.ManifestProtectedResource;
import org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation.ManifestTargetRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Target-keyed integrity coverage and recorded-version policy.
 * Scenarios trace to {@code spdd/prompt/BDMD-5124-202608210930-[Feat]-service-protected-resources-integrity-policy-adapter.md}
 * and {@code spdd/prompt/BDMD-5124-202609031327-[Feat]-service-protected-resources-n1-integrity.md} (Gherkin).
 */
class EvaluateProtectedResourcesIntegrityTest {

    private static final ProductRepoLocator ROOT_LOCATOR = locator("https://github.com/org/root.git");
    private static final ProductRepoLocator INFRA_LOCATOR = locator("https://github.com/org/infra.git");

    /**
     * Feature: Lasting protected-resources integrity
     *
     * Scenario: Omitted repository protects the explicit root target
     *   Given a manifest whose root target is not the first target
     *   And a protected resource omits repository
     *   When integrity resolves protection coverage
     *   Then the protected target is the target marked isRoot true
     */
    @Test
    void whenRepositoryOmittedThenResolveExplicitRootEvenIfNotFirst(@TempDir Path published, @TempDir Path expected)
            throws Exception {
        Files.writeString(published.resolve("root.txt"), "same");
        Files.writeString(expected.resolve("root.txt"), "same");
        RecordingGitPort gitPort = new RecordingGitPort(published);
        CapturingPresenter presenter = new CapturingPresenter();
        new EvaluateProtectedResourcesIntegrity(
                command(ROOT_LOCATOR, "root-tag", List.of(), List.of()),
                presenter,
                persistency(polyrepoManifest(protectedResource("root.txt", null))),
                gitPort,
                instantiatePort(Map.of("infra-repo", tree(expected), "app-repo", tree(expected))),
                new EvaluateProtectedResourcesIntegrityDigestOutboundPortImpl()
        ).execute();

        assertThat(presenter.passed).isTrue();
        assertThat(gitPort.refs).containsExactly("root-tag");
        assertThat(gitPort.urls).containsExactly(ROOT_LOCATOR.remoteUrlHttp());
    }

    /**
     * Feature: Lasting protected-resources integrity
     *
     * Scenario: Omitted repository resolves to the explicit root
     *   Given a valid manifest whose root target is not first
     *   And a protected resource omits repository
     *   When the manifest is published and integrity is evaluated
     *   Then publication accepts the declaration
     *   And integrity compares it on the target marked isRoot true
     */
    @Test
    void whenRepositoryOmittedThenUseIsRootTarget(@TempDir Path published, @TempDir Path expected) throws Exception {
        whenRepositoryOmittedThenResolveExplicitRootEvenIfNotFirst(published, expected);
    }

    /**
     * Feature: Lasting protected-resources integrity
     *
     * Scenario: Referenced additional target uses its own locator and ref
     *   Given a protected resource names "infra-repo"
     *   And exactly one additional locator and ref are recorded for "infra-repo"
     *   When integrity evaluates the resource
     *   Then "infra-repo" is cloned at its own recorded ref
     *   And the root ref is not used
     */
    @Test
    void whenAdditionalTargetProtectedThenUseItsOwnLocatorAndRef(@TempDir Path published, @TempDir Path expected)
            throws Exception {
        Files.writeString(published.resolve("tf.txt"), "same");
        Files.writeString(expected.resolve("tf.txt"), "same");
        RecordingGitPort gitPort = new RecordingGitPort(published);
        CapturingPresenter presenter = new CapturingPresenter();
        new EvaluateProtectedResourcesIntegrity(
                command(
                        ROOT_LOCATOR,
                        "root-tag",
                        List.of(new KeyedProductRepoLocator("infra-repo", INFRA_LOCATOR)),
                        List.of(new KeyedProductRepoRef("infra-repo", "infra-tag"))),
                presenter,
                persistency(polyrepoManifest(protectedResource("tf.txt", "infra-repo"))),
                gitPort,
                instantiatePort(Map.of("infra-repo", tree(expected), "app-repo", tree(expected))),
                new EvaluateProtectedResourcesIntegrityDigestOutboundPortImpl()
        ).execute();

        assertThat(presenter.passed).isTrue();
        assertThat(gitPort.refs).containsExactly("infra-tag");
        assertThat(gitPort.urls).containsExactly(INFRA_LOCATOR.remoteUrlHttp());
    }

    /**
     * Feature: Lasting protected-resources integrity
     *
     * Scenario: Missing referenced repository metadata fails closed
     *   Given a protected resource names "infra-repo"
     *   And its locator or ref is missing or duplicated
     *   When integrity is evaluated
     *   Then the outcome is failed before Git access
     *   And the message names "infra-repo"
     */
    @Test
    void whenReferencedTargetMetadataMissingOrDuplicateThenFailBeforeClone() {
        RecordingGitPort gitPort = new RecordingGitPort(null);
        CapturingPresenter presenter = new CapturingPresenter();
        new EvaluateProtectedResourcesIntegrity(
                command(ROOT_LOCATOR, "root-tag", List.of(), List.of()),
                presenter,
                persistency(polyrepoManifest(protectedResource("tf.txt", "infra-repo"))),
                gitPort,
                instantiatePort(Map.of()),
                new EvaluateProtectedResourcesIntegrityDigestOutboundPortImpl()
        ).execute();

        assertThat(presenter.failed).isTrue();
        assertThat(presenter.message).contains("infra-repo");
        assertThat(gitPort.refs).isEmpty();
    }

    /**
     * Feature: Multi-destination integrity
     *
     * Scenario: Missing referenced additional ref fails closed
     *   Given "infra-repo" is protected
     *   And its locator exists but its additional ref is missing
     *   When integrity is evaluated
     *   Then evaluationResult is false before cloning "infra-repo"
     *   And the message names "infra-repo"
     */
    @Test
    void whenProtectedAdditionalRefMissingThenFailBeforeGit() {
        RecordingGitPort gitPort = new RecordingGitPort(null);
        CapturingPresenter presenter = new CapturingPresenter();
        new EvaluateProtectedResourcesIntegrity(
                command(
                        ROOT_LOCATOR,
                        "root-tag",
                        List.of(new KeyedProductRepoLocator("infra-repo", INFRA_LOCATOR)),
                        List.of()),
                presenter,
                persistency(polyrepoManifest(protectedResource("tf.txt", "infra-repo"))),
                gitPort,
                instantiatePort(Map.of()),
                new EvaluateProtectedResourcesIntegrityDigestOutboundPortImpl()
        ).execute();

        assertThat(presenter.failed).isTrue();
        assertThat(presenter.message).contains("infra-repo");
        assertThat(gitPort.refs).isEmpty();
    }

    /**
     * Feature: Multi-destination integrity
     *
     * Scenario: Duplicate referenced locator fails closed
     *   Given "infra-repo" is protected
     *   And two additional locator entries use repositoryKey "infra-repo"
     *   When integrity is evaluated
     *   Then evaluationResult is false
     *   And no ambiguous locator is selected
     */
    @Test
    void whenProtectedAdditionalLocatorDuplicatedThenFailBeforeGit() {
        RecordingGitPort gitPort = new RecordingGitPort(null);
        CapturingPresenter presenter = new CapturingPresenter();
        new EvaluateProtectedResourcesIntegrity(
                command(
                        ROOT_LOCATOR,
                        "root-tag",
                        List.of(
                                new KeyedProductRepoLocator("infra-repo", INFRA_LOCATOR),
                                new KeyedProductRepoLocator("infra-repo", locator("https://github.com/org/other.git"))),
                        List.of(new KeyedProductRepoRef("infra-repo", "infra-tag"))),
                presenter,
                persistency(polyrepoManifest(protectedResource("tf.txt", "infra-repo"))),
                gitPort,
                instantiatePort(Map.of()),
                new EvaluateProtectedResourcesIntegrityDigestOutboundPortImpl()
        ).execute();

        assertThat(presenter.failed).isTrue();
        assertThat(presenter.message).contains("infra-repo");
        assertThat(gitPort.refs).isEmpty();
    }

    /**
     * Feature: Lasting protected-resources integrity
     *
     * Scenario: Unreferenced repository metadata is ignored
     *   Given only the root target is protected
     *   And an unrelated additional target has missing or duplicate metadata
     *   When integrity is evaluated
     *   Then only the root repository is cloned
     *   And the unrelated metadata does not fail the policy
     */
    @Test
    void whenOnlyRootProtectedThenIgnoreUnreferencedAdditionalMetadata(
            @TempDir Path published, @TempDir Path expected) throws Exception {
        Files.writeString(published.resolve("root.txt"), "same");
        Files.writeString(expected.resolve("root.txt"), "same");
        RecordingGitPort gitPort = new RecordingGitPort(published);
        CapturingPresenter presenter = new CapturingPresenter();
        new EvaluateProtectedResourcesIntegrity(
                command(
                        ROOT_LOCATOR,
                        "root-tag",
                        List.of(
                                new KeyedProductRepoLocator("infra-repo", INFRA_LOCATOR),
                                new KeyedProductRepoLocator("infra-repo", locator("https://github.com/org/dup.git"))),
                        List.of()),
                presenter,
                persistency(polyrepoManifest(protectedResource("root.txt", null))),
                gitPort,
                instantiatePort(Map.of("infra-repo", tree(expected), "app-repo", tree(expected))),
                new EvaluateProtectedResourcesIntegrityDigestOutboundPortImpl()
        ).execute();

        assertThat(presenter.passed).isTrue();
        assertThat(gitPort.urls).containsExactly(ROOT_LOCATOR.remoteUrlHttp());
        assertThat(gitPort.refs).containsExactly("root-tag");
    }

    /**
     * Feature: Protected-resources integrity evaluation
     *
     * Scenario: Missing blueprint repository configuration fails
     *   Given a recorded blueprint version with protected resources whose blueprint has no Git repository
     *   And the data product version has blueprint lineage for that version
     *   When integrity is evaluated
     *   Then the outcome is an infrastructure failure
     *   And the message states the blueprint repository is not configured
     */
    @Test
    void whenSourceBlueprintRepositoryMissingThenInfrastructureFailure() {
        BlueprintVersion version = new BlueprintVersion();
        version.setBlueprint(new Blueprint());
        RecordingPersistencyPort persistency = new RecordingPersistencyPort(
                version, polyrepoManifest(protectedResource("root.txt", null)));
        CapturingPresenter presenter = new CapturingPresenter();
        RecordingGitPort gitPort = new RecordingGitPort(null);
        new EvaluateProtectedResourcesIntegrity(
                command(ROOT_LOCATOR, "root-tag", List.of(), List.of()),
                presenter,
                persistency,
                gitPort,
                instantiatePort(Map.of()),
                new EvaluateProtectedResourcesIntegrityDigestOutboundPortImpl()
        ).execute();

        assertThat(presenter.infrastructure).isTrue();
        assertThat(presenter.message).contains("the blueprint repository is not configured");
        assertThat(gitPort.refs).isEmpty();
    }

    /**
     * Feature: Lasting protected-resources integrity
     *
     * Scenario: Publication uses only the recorded Blueprint version policy
     *   Given a published product records Blueprint version 1
     *   And Blueprint version 2 removes or retargets protected resources
     *   When the product publication is evaluated
     *   Then only Blueprint version 1 protectedResources are used
     */
    @Test
    void whenLaterBlueprintChangesProtectionThenRecordedVersionListIsUsed(
            @TempDir Path published, @TempDir Path expected) throws Exception {
        Files.writeString(published.resolve("v1-only.txt"), "same");
        Files.writeString(expected.resolve("v1-only.txt"), "same");
        Manifest recordedV1 = polyrepoManifest(protectedResource("v1-only.txt", null));
        Manifest laterV2 = polyrepoManifest(protectedResource("v2-only.txt", "infra-repo"));
        RecordingPersistencyPort persistency = persistencyWithRecordedAndLaterPolicy(recordedV1, laterV2);
        CapturingPresenter presenter = new CapturingPresenter();
        new EvaluateProtectedResourcesIntegrity(
                command(ROOT_LOCATOR, "root-tag", List.of(), List.of()),
                presenter,
                persistency,
                new RecordingGitPort(published),
                instantiatePort(Map.of("infra-repo", tree(expected), "app-repo", tree(expected))),
                new EvaluateProtectedResourcesIntegrityDigestOutboundPortImpl()
        ).execute();

        assertThat(persistency.requestedVersions).containsExactly("1.0.0");
        assertThat(persistency.readManifests).containsExactly("1.0.0");
        assertThat(presenter.passed).isTrue();
        assertThat(presenter.failed).isFalse();
        assertThat(presenter.infrastructure).isFalse();
    }

    /**
     * Feature: Multi-destination integrity
     *
     * Scenario: Same physical remote remains two logical comparisons
     *   Given two protected logical targets resolve to the same remote URL
     *   And each has its own recorded ref
     *   When integrity is evaluated
     *   Then both logical targets are cloned and compared independently
     */
    @Test
    void whenProtectedKeysShareRemoteThenEvaluateIndependently(@TempDir Path published, @TempDir Path expected)
            throws Exception {
        Files.writeString(published.resolve("shared.txt"), "same");
        Files.writeString(expected.resolve("shared.txt"), "same");
        RecordingGitPort gitPort = new RecordingGitPort(published);
        CapturingPresenter presenter = new CapturingPresenter();
        ProductRepoLocator shared = locator("https://github.com/org/shared.git");
        new EvaluateProtectedResourcesIntegrity(
                command(
                        shared,
                        "root-tag",
                        List.of(new KeyedProductRepoLocator("infra-repo", shared)),
                        List.of(new KeyedProductRepoRef("infra-repo", "infra-tag"))),
                presenter,
                persistency(polyrepoManifest(
                        protectedResource("shared.txt", null),
                        protectedResource("shared.txt", "infra-repo"))),
                gitPort,
                instantiatePort(Map.of("infra-repo", tree(expected), "app-repo", tree(expected))),
                new EvaluateProtectedResourcesIntegrityDigestOutboundPortImpl()
        ).execute();

        assertThat(presenter.passed).isTrue();
        assertThat(gitPort.refs).containsExactly("root-tag", "infra-tag");
        assertThat(gitPort.urls).containsExactly(shared.remoteUrlHttp(), shared.remoteUrlHttp());
    }

    /**
     * Feature: Multi-destination integrity
     *
     * Scenario: First infrastructure failure may stop evaluation
     *   Given several targets are protected
     *   And cloning one target fails
     *   When integrity is evaluated
     *   Then evaluationResult is false
     *   And the implementation is not required to clone remaining targets
     */
    @Test
    void whenProtectedTargetCloneFailsThenRemainingClonesAreOptional(@TempDir Path expected) {
        RecordingGitPort gitPort = new RecordingGitPort(null);
        gitPort.failure = new IllegalStateException("clone failed");
        CapturingPresenter presenter = new CapturingPresenter();
        new EvaluateProtectedResourcesIntegrity(
                command(
                        ROOT_LOCATOR,
                        "root-tag",
                        List.of(new KeyedProductRepoLocator("infra-repo", INFRA_LOCATOR)),
                        List.of(new KeyedProductRepoRef("infra-repo", "infra-tag"))),
                presenter,
                persistency(polyrepoManifest(
                        protectedResource("root.txt", null),
                        protectedResource("tf.txt", "infra-repo"))),
                gitPort,
                instantiatePort(Map.of("infra-repo", tree(expected), "app-repo", tree(expected))),
                new EvaluateProtectedResourcesIntegrityDigestOutboundPortImpl()
        ).execute();

        assertThat(presenter.infrastructure).isTrue();
        assertThat(gitPort.refs.size()).isLessThanOrEqualTo(2);
        assertThat(gitPort.refs).isNotEmpty();
    }

    /**
     * Feature: Protected-resources integrity evaluation
     *
     * Scenario: All path mismatches are reported together
     *   Given two protected paths whose contents differ from the re-instantiation
     *   When integrity is evaluated
     *   Then evaluation fails once
     *   And the failure names both protected paths
     */
    @Test
    void whenSeveralProtectedPathsDifferThenReportAllMismatches(
            @TempDir Path published, @TempDir Path expected) throws Exception {
        Files.writeString(published.resolve("docs.md"), "tampered-docs");
        Files.writeString(published.resolve("core.tf"), "tampered-tf");
        Files.writeString(expected.resolve("docs.md"), "original-docs");
        Files.writeString(expected.resolve("core.tf"), "original-tf");
        CapturingPresenter presenter = new CapturingPresenter();
        new EvaluateProtectedResourcesIntegrity(
                command(ROOT_LOCATOR, "root-tag", List.of(), List.of()),
                presenter,
                persistency(polyrepoManifest(
                        protectedResource("docs.md", null),
                        protectedResource("core.tf", null))),
                new RecordingGitPort(published),
                instantiatePort(Map.of("infra-repo", tree(expected), "app-repo", tree(expected))),
                new EvaluateProtectedResourcesIntegrityDigestOutboundPortImpl()
        ).execute();

        assertThat(presenter.failed).isTrue();
        assertThat(presenter.infrastructure).isFalse();
        assertThat(presenter.mismatches).hasSize(2);
        assertThat(presenter.message).contains("docs.md").contains("core.tf");
    }

    private static EvaluateProtectedResourcesIntegrityCommand command(
            ProductRepoLocator rootLocator,
            String rootRef,
            List<KeyedProductRepoLocator> additionalLocators,
            List<KeyedProductRepoRef> additionalRefs
    ) {
        return new EvaluateProtectedResourcesIntegrityCommand(
                rootRef,
                rootLocator,
                additionalLocators,
                additionalRefs,
                "parent-blueprint",
                "1.0.0",
                Map.of());
    }

    private static RecordingPersistencyPort persistency(Manifest manifest) {
        return new RecordingPersistencyPort(
                Map.of("1.0.0", blueprintVersionWithRepo("1.0.0")),
                Map.of("1.0.0", manifest),
                Set.of());
    }

    private static RecordingPersistencyPort persistencyWithRecordedAndLaterPolicy(
            Manifest recordedV1,
            Manifest laterV2
    ) {
        return new RecordingPersistencyPort(
                Map.of(
                        "1.0.0", blueprintVersionWithRepo("1.0.0"),
                        "2.0.0", blueprintVersionWithRepo("2.0.0")),
                Map.of("1.0.0", recordedV1, "2.0.0", laterV2),
                Set.of("2.0.0"));
    }

    private static BlueprintVersion blueprintVersionWithRepo(String versionNumber) {
        BlueprintVersion version = new BlueprintVersion();
        version.setVersionNumber(versionNumber);
        Blueprint blueprint = new Blueprint();
        BlueprintRepo repo = new BlueprintRepo();
        repo.setRemoteUrlHttp("https://github.com/org/source.git");
        repo.setProviderType(BlueprintRepoProviderType.GITHUB);
        repo.setProviderBaseUrl("https://github.com");
        blueprint.setBlueprintRepo(repo);
        version.setBlueprint(blueprint);
        return version;
    }

    private static EvaluateProtectedResourcesIntegrityInstantiateOutboundPort instantiatePort(
            Map<String, CloseableWorkingTree> trees
    ) {
        return (version, command) -> TargetWorkingTrees.of(trees);
    }

    private static Manifest polyrepoManifest(ManifestProtectedResource... resources) {
        Manifest manifest = new Manifest();
        ManifestTargetRepository infra = new ManifestTargetRepository();
        infra.setKey("infra-repo");
        ManifestTargetRepository root = new ManifestTargetRepository();
        root.setKey("app-repo");
        root.setIsRoot(true);
        manifest.setTargetRepositories(List.of(infra, root));
        manifest.setProtectedResources(List.of(resources));
        return manifest;
    }

    private static ManifestProtectedResource protectedResource(String path, String repository) {
        ManifestProtectedResource resource = new ManifestProtectedResource();
        resource.setPath(path);
        resource.setRepository(repository);
        return resource;
    }

    private static ProductRepoLocator locator(String url) {
        return new ProductRepoLocator(url, "GITHUB", "https://github.com", "repo", "main", "org", "id");
    }

    private static CloseableWorkingTree tree(Path path) {
        return new CloseableWorkingTree(path) {
            @Override
            public void close() {
                // JUnit @TempDir trees must survive try-with-resources in the use case.
            }
        };
    }

    private static final class CapturingPresenter implements EvaluateProtectedResourcesIntegrityPresenter {
        private boolean passed;
        private boolean failed;
        private boolean infrastructure;
        private String message;
        private List<ProtectedResourceMismatch> mismatches = List.of();

        @Override
        public void presentNotApplicable(String reason) {
            this.message = reason;
        }

        @Override
        public void presentPassed(String message) {
            this.passed = true;
            this.message = message;
        }

        @Override
        public void presentFailed(List<ProtectedResourceMismatch> mismatches, String message) {
            this.failed = true;
            this.mismatches = mismatches;
            this.message = message;
        }

        @Override
        public void presentInfrastructureFailure(String message) {
            this.infrastructure = true;
            this.message = message;
        }
    }

    private static final class RecordingGitPort implements EvaluateProtectedResourcesIntegrityGitOutboundPort {
        private final Path tree;
        private final List<String> refs = new ArrayList<>();
        private final List<String> urls = new ArrayList<>();
        private RuntimeException failure;

        private RecordingGitPort(Path tree) {
            this.tree = tree;
        }

        @Override
        public CloseableWorkingTree clonePublishedDataProductVersion(ProductRepoLocator repo, String tag) {
            refs.add(tag);
            urls.add(repo == null ? null : repo.remoteUrlHttp());
            if (failure != null) {
                throw failure;
            }
            return tree(tree);
        }
    }

    private static final class RecordingPersistencyPort implements EvaluateProtectedResourcesIntegrityPersistencyOutboundPort {
        private final Map<String, BlueprintVersion> versionsByNumber;
        private final Map<String, Manifest> manifestsByNumber;
        private final Set<String> forbiddenVersions;
        private final List<String> requestedVersions = new ArrayList<>();
        private final List<String> readManifests = new ArrayList<>();

        private RecordingPersistencyPort(BlueprintVersion version, Manifest manifest) {
            String number = version.getVersionNumber() == null ? "1.0.0" : version.getVersionNumber();
            version.setVersionNumber(number);
            this.versionsByNumber = Map.of(number, version);
            this.manifestsByNumber = Map.of(number, manifest);
            this.forbiddenVersions = Set.of();
        }

        private RecordingPersistencyPort(
                Map<String, BlueprintVersion> versionsByNumber,
                Map<String, Manifest> manifestsByNumber,
                Set<String> forbiddenVersions
        ) {
            this.versionsByNumber = Map.copyOf(versionsByNumber);
            this.manifestsByNumber = Map.copyOf(manifestsByNumber);
            this.forbiddenVersions = Set.copyOf(forbiddenVersions);
        }

        @Override
        public BlueprintVersion findByBlueprintNameAndVersion(String blueprintName, String blueprintVersion) {
            requestedVersions.add(blueprintVersion);
            refuseLaterVersion(blueprintVersion);
            BlueprintVersion found = versionsByNumber.get(blueprintVersion);
            if (found == null) {
                throw new AssertionError("unexpected Blueprint version lookup: " + blueprintVersion);
            }
            return found;
        }

        @Override
        public Manifest readManifest(BlueprintVersion blueprintVersion) {
            String number = blueprintVersion.getVersionNumber();
            readManifests.add(number);
            refuseLaterVersion(number);
            Manifest found = manifestsByNumber.get(number);
            if (found == null) {
                throw new AssertionError("no manifest for Blueprint version " + number);
            }
            return found;
        }

        private void refuseLaterVersion(String blueprintVersion) {
            if (forbiddenVersions.contains(blueprintVersion)) {
                throw new AssertionError("must not read later Blueprint version " + blueprintVersion);
            }
        }
    }
}
