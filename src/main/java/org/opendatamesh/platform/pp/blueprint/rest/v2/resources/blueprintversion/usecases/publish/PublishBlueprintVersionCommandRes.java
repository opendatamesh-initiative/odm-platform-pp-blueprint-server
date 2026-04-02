package org.opendatamesh.platform.pp.blueprint.rest.v2.resources.blueprintversion.usecases.publish;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "publish_blueprint_version_command")
public class PublishBlueprintVersionCommandRes {

    private BlueprintVersion blueprintVersion;

    public static class BlueprintVersion {
        private String name;
        private String description;
        private String readme;
        private String tag;
        private String spec;
        private String specVersion;
        private JsonNode content;
        private Blueprint blueprint;

        public static class Blueprint {
            private String name;
            private String uuid;

            public String getName() {
                return name;
            }
    
            public void setName(String name) {
                this.name = name;
            }
            
            public String getUuid() {
                return uuid;
            }
    
            public void setUuid(String uuid) {
                this.uuid = uuid;
            }
        }
        
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
        
        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
        
        public String getReadme() {
            return readme;
        }

        public void setReadme(String readme) {
            this.readme = readme;
        }
        
        public String getTag() {
            return tag;
        }

        public void setTag(String tag) {
            this.tag = tag;
        }
        
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
        
        public JsonNode getContent() {
            return content;
        }

        public void setContent(JsonNode content) {
            this.content = content;
        }
        
        public Blueprint getBlueprint() {
            return blueprint;
        }

        public void setBlueprint(Blueprint blueprint) {
            this.blueprint = blueprint;
        }
    }

    public BlueprintVersion getBlueprintVersion() {
        return blueprintVersion;
    }

    public void setBlueprintVersion(BlueprintVersion blueprintVersion) {
        this.blueprintVersion = blueprintVersion;
    } 
}
