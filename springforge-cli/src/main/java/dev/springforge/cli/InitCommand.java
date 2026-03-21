package dev.springforge.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import dev.springforge.config.ConfigLoader;
import dev.springforge.config.SpringForgeConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

/**
 * Implements {@code springforge init} — interactive config wizard.
 * Asks for basePackage, mapper, migration, openapi, Spring version
 * and writes springforge.yml to project root.
 */
@Command(
    name = "init",
    description = "Initialize springforge.yml in current project",
    mixinStandardHelpOptions = true
)
public class InitCommand implements Callable<Integer> {

    private static final Logger LOG = LoggerFactory.getLogger(InitCommand.class);
    private static final String CONFIG_FILE = "springforge.yml";

    @ParentCommand
    private MainCommand parent;

    private final BufferedReader reader;
    private final PrintStream out;

    public InitCommand() {
        this(new BufferedReader(new InputStreamReader(System.in)), System.out);
    }

    InitCommand(BufferedReader reader, PrintStream out) {
        this.reader = reader;
        this.out = out;
    }

    @Override
    public Integer call() {
        Path projectRoot = Path.of(System.getProperty("user.dir"));
        Path configFile = projectRoot.resolve(CONFIG_FILE);

        if (Files.exists(configFile)) {
            out.println("springforge.yml already exists in this directory.");
            String answer = prompt("Overwrite? (y/N): ", "n");
            if (!"y".equalsIgnoreCase(answer.trim())) {
                out.println("Aborted.");
                return ExitCodes.SUCCESS;
            }
        }

        out.println();
        out.println("SpringForge Init — Configuration Wizard");
        out.println("========================================");
        out.println();

        SpringForgeConfig config = new SpringForgeConfig();

        String basePackage = prompt(
            "Base package [com.example]: ", "com.example");
        config.getProject().setBasePackage(basePackage);

        String springVersion = prompt(
            "Spring Boot version (2/3) [3]: ", "3");
        config.getProject().setSpringBootVersion(springVersion);

        String mapperLib = prompt(
            "Mapper library (mapstruct/modelmapper) [mapstruct]: ", "mapstruct");
        config.getGeneration().setMapperLib(mapperLib);

        String migrationTool = prompt(
            "Migration tool (liquibase/flyway/none) [none]: ", "none");
        config.getGeneration().setMigrationTool(migrationTool);

        String openApiFormat = prompt(
            "OpenAPI format (yaml/json/none) [none]: ", "none");
        config.getGeneration().setOpenApiFormat(openApiFormat);

        String onConflict = prompt(
            "On conflict (skip/overwrite) [skip]: ", "skip");
        config.getGeneration().setOnConflict(onConflict);

        try {
            ConfigLoader configLoader = new ConfigLoader();
            configLoader.save(config, configFile);
            out.println();
            out.println("Created " + CONFIG_FILE);
            LOG.info("Wrote springforge.yml to {}", configFile);
            return ExitCodes.SUCCESS;
        } catch (IOException e) {
            LOG.error("Failed to write config: {}", e.getMessage());
            System.err.println("Error: Failed to write " + CONFIG_FILE + " — " + e.getMessage());
            return ExitCodes.FILE_WRITE_ERROR;
        }
    }

    String prompt(String message, String defaultValue) {
        out.print(message);
        out.flush();
        try {
            String line = reader.readLine();
            if (line == null || line.isBlank()) {
                return defaultValue;
            }
            return line.trim();
        } catch (IOException e) {
            return defaultValue;
        }
    }
}
