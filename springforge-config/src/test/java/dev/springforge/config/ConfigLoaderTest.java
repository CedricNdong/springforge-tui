package dev.springforge.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigLoaderTest {

    private ConfigLoader loader;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        loader = new ConfigLoader();
    }

    @Test
    @DisplayName("should return defaults when no config file exists")
    void shouldReturnDefaultsWhenNoConfig() throws Exception {
        SpringForgeConfig config = loader.load(null, tempDir);

        assertThat(config.getProject().getBasePackage()).isEqualTo("com.example");
        assertThat(config.getProject().getSpringBootVersion()).isEqualTo("3");
        assertThat(config.getGeneration().getMapperLib()).isEqualTo("mapstruct");
        assertThat(config.getGeneration().getOnConflict()).isEqualTo("skip");
    }

    @Test
    @DisplayName("should load config from local springforge.yml")
    void shouldLoadLocalConfig() throws Exception {
        String yaml = """
            version: "1.0"
            project:
              basePackage: com.myapp
              springBootVersion: "2"
            generation:
              mapperLib: modelmapper
              onConflict: overwrite
            """;
        Files.writeString(tempDir.resolve("springforge.yml"), yaml);

        SpringForgeConfig config = loader.load(null, tempDir);

        assertThat(config.getProject().getBasePackage()).isEqualTo("com.myapp");
        assertThat(config.getProject().getSpringBootVersion()).isEqualTo("2");
        assertThat(config.getGeneration().getMapperLib()).isEqualTo("modelmapper");
        assertThat(config.getGeneration().getOnConflict()).isEqualTo("overwrite");
    }

    @Test
    @DisplayName("should load config with springforge: top-level key")
    void shouldLoadConfigWithTopLevelKey() throws Exception {
        String yaml = """
            springforge:
              version: "1.0"
              project:
                basePackage: com.wrapped
              generation:
                mapperLib: mapstruct
            """;
        Files.writeString(tempDir.resolve("springforge.yml"), yaml);

        SpringForgeConfig config = loader.load(null, tempDir);

        assertThat(config.getProject().getBasePackage()).isEqualTo("com.wrapped");
    }

    @Test
    @DisplayName("should prefer explicit config path over local file")
    void shouldPreferExplicitConfigPath() throws Exception {
        String localYaml = """
            project:
              basePackage: com.local
            """;
        Files.writeString(tempDir.resolve("springforge.yml"), localYaml);

        Path explicitConfig = tempDir.resolve("custom-config.yml");
        String explicitYaml = """
            project:
              basePackage: com.explicit
            """;
        Files.writeString(explicitConfig, explicitYaml);

        SpringForgeConfig config = loader.load(explicitConfig, tempDir);

        assertThat(config.getProject().getBasePackage()).isEqualTo("com.explicit");
    }

    @Test
    @DisplayName("should ignore unknown fields for forward compatibility")
    void shouldIgnoreUnknownFields() throws Exception {
        String yaml = """
            project:
              basePackage: com.myapp
              unknownField: "ignored"
            generation:
              futureOption: true
            """;
        Files.writeString(tempDir.resolve("springforge.yml"), yaml);

        SpringForgeConfig config = loader.load(null, tempDir);

        assertThat(config.getProject().getBasePackage()).isEqualTo("com.myapp");
    }

    @Test
    @DisplayName("should throw ConfigLoadException for invalid YAML")
    void shouldThrowOnInvalidYaml() throws Exception {
        String yaml = "invalid: [yaml: broken";
        Files.writeString(tempDir.resolve("springforge.yml"), yaml);

        assertThatThrownBy(() -> loader.load(null, tempDir))
            .isInstanceOf(ConfigLoader.ConfigLoadException.class)
            .hasMessageContaining("Failed to parse");
    }

    @Test
    @DisplayName("should throw ConfigLoadException when explicit file not found")
    void shouldThrowWhenExplicitFileNotFound() {
        Path nonExistent = tempDir.resolve("missing.yml");

        assertThatThrownBy(() -> loader.load(nonExistent, tempDir))
            .isInstanceOf(ConfigLoader.ConfigLoadException.class)
            .hasMessageContaining("Config file not found");
    }

    @Test
    @DisplayName("should save config to YAML file")
    void shouldSaveConfig() throws Exception {
        SpringForgeConfig config = new SpringForgeConfig();
        config.getProject().setBasePackage("com.saved");
        config.getGeneration().setMapperLib("modelmapper");

        Path outputPath = tempDir.resolve("output/springforge.yml");
        loader.save(config, outputPath);

        assertThat(Files.exists(outputPath)).isTrue();
        String content = Files.readString(outputPath);
        assertThat(content).contains("com.saved");
    }
}
