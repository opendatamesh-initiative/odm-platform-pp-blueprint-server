package org.opendatamesh.platform.pp.blueprint.manifest.model;

import org.opendatamesh.platform.pp.blueprint.manifest.model.core.ManifestComponentBase;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestVisitor;

import java.util.ArrayList;
import java.util.List;

/**
 * Root document of the Blueprint Manifest ({@code spec: odm-blueprint-manifest}).
 */
public class Manifest extends ManifestComponentBase {

    public static final String SPEC_NAME = "odm-blueprint-manifest";

    private String spec;
    private String specVersion;
    private String name;
    private String displayName;
    private String version;
    private String description;

    private List<ManifestParameter> parameters = new ArrayList<>();
    private List<ManifestProtectedResource> protectedResources = new ArrayList<>();
    private List<ManifestComposition> composition = new ArrayList<>();
    private ManifestInstantiation instantiation;

    public String getSpec() {
        return spec;
    }

    public void setSpec(String spec) {
        this.spec = spec;
    }

    public String getSpecVersion() {
        return specVersion;
    }

    public void setSpecVersion(String specVersion) {
        this.specVersion = specVersion;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<ManifestParameter> getParameters() {
        return parameters;
    }

    public void setParameters(List<ManifestParameter> parameters) {
        this.parameters = parameters;
    }

    public List<ManifestProtectedResource> getProtectedResources() {
        return protectedResources;
    }

    public void setProtectedResources(List<ManifestProtectedResource> protectedResources) {
        this.protectedResources = protectedResources;
    }

    public List<ManifestComposition> getComposition() {
        return composition;
    }

    public void setComposition(List<ManifestComposition> composition) {
        this.composition = composition;
    }

    public ManifestInstantiation getInstantiation() {
        return instantiation;
    }

    public void setInstantiation(ManifestInstantiation instantiation) {
        this.instantiation = instantiation;
    }

    public void accept(ManifestVisitor visitor) {
        visitor.visit(this);
    }
}
