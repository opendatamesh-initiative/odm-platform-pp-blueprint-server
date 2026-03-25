package org.opendatamesh.platform.pp.blueprint.manifest.model.instantiation;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.opendatamesh.platform.pp.blueprint.manifest.model.core.ManifestComponentBase;
import org.opendatamesh.platform.pp.blueprint.manifest.visitors.ManifestInstantiationVisitor;


public class ManifestInstantiationTarget extends ManifestComponentBase {

    private String repositoryNamePostfix;
    private InstantiationTargetCreatePolicy createPolicy;
    private String module;
    private String sourcePath;
    private String targetPath;

    public String getRepositoryNamePostfix() {
        return repositoryNamePostfix;
    }

    public void setRepositoryNamePostfix(String repositoryNamePostfix) {
        this.repositoryNamePostfix = repositoryNamePostfix;
    }

    public InstantiationTargetCreatePolicy getCreatePolicy() {
        return createPolicy;
    }

    public void setCreatePolicy(InstantiationTargetCreatePolicy createPolicy) {
        this.createPolicy = createPolicy;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public void setTargetPath(String targetPath) {
        this.targetPath = targetPath;
    }

    public enum InstantiationTargetCreatePolicy {
        @JsonProperty("create_if_missing")
        CREATE_IF_MISSING,
        @JsonProperty("must_exist")
        MUST_EXIST
    }

    public void accept(ManifestInstantiationVisitor visitor) {
        visitor.visit(this);
    }
}
