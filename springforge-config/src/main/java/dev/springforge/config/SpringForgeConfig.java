package dev.springforge.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Top-level configuration model for springforge.yml.
 * Uses Jackson YAML for type-safe binding.
 * Unknown fields are ignored for forward compatibility.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SpringForgeConfig {

    @JsonProperty("version")
    private String version = "1.0";

    @JsonProperty("project")
    private ProjectConfig project = new ProjectConfig();

    @JsonProperty("generation")
    private GenerationConfigYaml generation = new GenerationConfigYaml();

    @JsonProperty("naming")
    private NamingConfig naming = new NamingConfig();

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public ProjectConfig getProject() { return project; }
    public void setProject(ProjectConfig project) { this.project = project; }
    public GenerationConfigYaml getGeneration() { return generation; }
    public void setGeneration(GenerationConfigYaml generation) { this.generation = generation; }
    public NamingConfig getNaming() { return naming; }
    public void setNaming(NamingConfig naming) { this.naming = naming; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProjectConfig {
        @JsonProperty("basePackage")
        private String basePackage = "com.example";

        @JsonProperty("srcDir")
        private String srcDir = "src/main/java";

        @JsonProperty("resourceDir")
        private String resourceDir = "src/main/resources";

        @JsonProperty("springBootVersion")
        private String springBootVersion = "3";

        public String getBasePackage() { return basePackage; }
        public void setBasePackage(String basePackage) { this.basePackage = basePackage; }
        public String getSrcDir() { return srcDir; }
        public void setSrcDir(String srcDir) { this.srcDir = srcDir; }
        public String getResourceDir() { return resourceDir; }
        public void setResourceDir(String resourceDir) { this.resourceDir = resourceDir; }
        public String getSpringBootVersion() { return springBootVersion; }
        public void setSpringBootVersion(String springBootVersion) { this.springBootVersion = springBootVersion; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GenerationConfigYaml {
        @JsonProperty("mapperLib")
        private String mapperLib = "mapstruct";

        @JsonProperty("migrationTool")
        private String migrationTool = "none";

        @JsonProperty("openApiFormat")
        private String openApiFormat = "none";

        @JsonProperty("onConflict")
        private String onConflict = "skip";

        @JsonProperty("lombok")
        private boolean lombok = true;

        public String getMapperLib() { return mapperLib; }
        public void setMapperLib(String mapperLib) { this.mapperLib = mapperLib; }
        public String getMigrationTool() { return migrationTool; }
        public void setMigrationTool(String migrationTool) { this.migrationTool = migrationTool; }
        public String getOpenApiFormat() { return openApiFormat; }
        public void setOpenApiFormat(String openApiFormat) { this.openApiFormat = openApiFormat; }
        public String getOnConflict() { return onConflict; }
        public void setOnConflict(String onConflict) { this.onConflict = onConflict; }
        public boolean isLombok() { return lombok; }
        public void setLombok(boolean lombok) { this.lombok = lombok; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NamingConfig {
        @JsonProperty("apiPrefix")
        private String apiPrefix = "/api";

        @JsonProperty("apiVersion")
        private String apiVersion = "v1";

        public String getApiPrefix() { return apiPrefix; }
        public void setApiPrefix(String apiPrefix) { this.apiPrefix = apiPrefix; }
        public String getApiVersion() { return apiVersion; }
        public void setApiVersion(String apiVersion) { this.apiVersion = apiVersion; }
    }
}
