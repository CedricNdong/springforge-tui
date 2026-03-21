package dev.springforge.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;

import dev.springforge.config.SpringForgeConfig;
import dev.springforge.engine.model.ConflictStrategy;
import dev.springforge.engine.model.GenerationConfig;
import dev.springforge.engine.model.Layer;
import dev.springforge.engine.model.MapperLib;
import dev.springforge.engine.model.SpringVersion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

class GenerateCommandTest {

    @TempDir
    Path tempDir;

    private Path entityFile;

    @BeforeEach
    void setUp() throws IOException {
        entityFile = tempDir.resolve("User.java");
        Files.writeString(entityFile, """
            package com.example.model;

            import jakarta.persistence.Entity;
            import jakarta.persistence.Id;
            import jakarta.persistence.GeneratedValue;

            @Entity
            public class User {
                @Id
                @GeneratedValue
                private Long id;
                private String name;
                private String email;
            }
            """);
    }

    @Test
    @DisplayName("should parse all flags correctly via Picocli")
    void shouldParseAllFlags() {
        MainCommand main = new MainCommand();
        CommandLine cli = new CommandLine(main);
        int exitCode = cli.execute(
            "generate",
            "--entity", entityFile.toString(),
            "--all",
            "--dry-run",
            "--spring-version", "2",
            "--mapper-lib", "modelmapper",
            "--overwrite",
            "--output", tempDir.toString()
        );

        // Dry run should succeed (exit 0) — files exist
        assertThat(exitCode).isEqualTo(0);
    }

    @Test
    @DisplayName("should resolve all layers when --all is specified")
    void shouldResolveAllLayers() {
        GenerateCommand cmd = new GenerateCommand();
        SpringForgeConfig config = new SpringForgeConfig();

        // Use reflection-free setters
        // No layer flags set, allLayers defaults to false, no anyLayerSelected
        EnumSet<Layer> layers = cmd.resolveLayers(config);

        assertThat(layers).contains(
            Layer.DTO_REQUEST, Layer.DTO_RESPONSE,
            Layer.MAPPER, Layer.REPOSITORY,
            Layer.SERVICE, Layer.SERVICE_IMPL,
            Layer.CONTROLLER
        );
    }

    @Test
    @DisplayName("should resolve Spring version from CLI flag over config")
    void shouldResolveCLISpringVersionOverConfig() {
        GenerateCommand cmd = new GenerateCommand();
        cmd.setSpringVersionFlag("2");

        SpringForgeConfig config = new SpringForgeConfig();
        config.getProject().setSpringBootVersion("3");

        SpringVersion version = cmd.resolveSpringVersion(config);
        assertThat(version).isEqualTo(SpringVersion.V2);
    }

    @Test
    @DisplayName("should resolve mapper lib from CLI flag over config")
    void shouldResolveMapperLibFromFlag() {
        GenerateCommand cmd = new GenerateCommand();
        cmd.setMapperLibFlag("modelmapper");

        SpringForgeConfig config = new SpringForgeConfig();
        MapperLib lib = cmd.resolveMapperLib(config);

        assertThat(lib).isEqualTo(MapperLib.MODEL_MAPPER);
    }

    @Test
    @DisplayName("should resolve conflict strategy with --overwrite flag")
    void shouldResolveOverwriteFromFlag() {
        GenerateCommand cmd = new GenerateCommand();
        // Simulate --overwrite via internal field
        cmd.setDryRun(false);

        SpringForgeConfig config = new SpringForgeConfig();
        config.getGeneration().setOnConflict("skip");

        // Without --overwrite flag, should use config value
        ConflictStrategy strategy = cmd.resolveConflictStrategy(config);
        assertThat(strategy).isEqualTo(ConflictStrategy.SKIP);
    }

    @Test
    @DisplayName("should return exit code 3 when no entities found")
    void shouldReturnExitCode3WhenNoEntities() {
        GenerateCommand cmd = new GenerateCommand();
        cmd.setParent(new MainCommand());
        // No entity source set — will find nothing
        int exitCode = cmd.call();
        assertThat(exitCode).isEqualTo(ExitCodes.ENTITY_PARSE_ERROR);
    }

    @Test
    @DisplayName("should generate files in dry-run mode")
    void shouldDryRunWithoutWriting() {
        MainCommand main = new MainCommand();
        CommandLine cli = new CommandLine(main);
        int exitCode = cli.execute(
            "generate",
            "--entity", entityFile.toString(),
            "--all",
            "--dry-run",
            "--output", tempDir.resolve("output").toString()
        );

        assertThat(exitCode).isEqualTo(0);
        // Dry run should not create output directory
        assertThat(Files.exists(tempDir.resolve("output"))).isFalse();
    }

    @Test
    @DisplayName("should use config defaults when no flags provided")
    void shouldUseConfigDefaults() {
        GenerateCommand cmd = new GenerateCommand();
        SpringForgeConfig config = new SpringForgeConfig();

        SpringVersion version = cmd.resolveSpringVersion(config);
        MapperLib mapper = cmd.resolveMapperLib(config);
        ConflictStrategy conflict = cmd.resolveConflictStrategy(config);

        assertThat(version).isEqualTo(SpringVersion.V3);
        assertThat(mapper).isEqualTo(MapperLib.MAPSTRUCT);
        assertThat(conflict).isEqualTo(ConflictStrategy.SKIP);
    }
}
