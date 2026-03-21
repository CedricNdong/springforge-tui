package dev.springforge.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads and resolves SpringForge configuration with 4-level resolution order:
 * <ol>
 *   <li>CLI flags (highest priority, applied externally)</li>
 *   <li>{@code --config <path>} — explicitly passed config file</li>
 *   <li>{@code ./springforge.yml} — local file in current working directory</li>
 *   <li>Built-in defaults (lowest priority)</li>
 * </ol>
 *
 * <p>Unknown fields in YAML are silently ignored for forward compatibility.
 */
public class ConfigLoader {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigLoader.class);
    private static final String CONFIG_FILE_NAME = "springforge.yml";

    private final ObjectMapper yamlMapper;

    public ConfigLoader() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    /**
     * Loads config with the 4-level resolution order.
     * CLI flags are applied externally after this method returns.
     *
     * @param explicitConfigPath path from --config flag, or null
     * @param projectRoot the project root directory
     * @return resolved config (defaults merged with file config)
     * @throws ConfigLoadException if config file is invalid (exit code 5)
     */
    public SpringForgeConfig load(Path explicitConfigPath, Path projectRoot)
            throws ConfigLoadException {
        SpringForgeConfig defaults = new SpringForgeConfig();

        Optional<Path> configFile = resolveConfigFile(explicitConfigPath, projectRoot);
        if (configFile.isEmpty()) {
            LOG.info("No springforge.yml found, using all defaults");
            return defaults;
        }

        return loadFromFile(configFile.get());
    }

    /**
     * Loads config from a specific file path.
     *
     * @param configPath path to the YAML config file
     * @return parsed config
     * @throws ConfigLoadException on parse errors
     */
    public SpringForgeConfig loadFromFile(Path configPath) throws ConfigLoadException {
        if (!Files.exists(configPath)) {
            throw new ConfigLoadException(
                "Config file not found: " + configPath);
        }

        try {
            String content = Files.readString(configPath);

            // Handle "springforge:" top-level key wrapper
            SpringForgeConfig config;
            if (content.stripLeading().startsWith("springforge:")) {
                var wrapper = yamlMapper.readValue(content,
                    ConfigWrapper.class);
                config = wrapper.springforge != null
                    ? wrapper.springforge : new SpringForgeConfig();
            } else {
                config = yamlMapper.readValue(content, SpringForgeConfig.class);
            }

            LOG.info("Loaded config from {}", configPath);
            return config;
        } catch (IOException e) {
            throw new ConfigLoadException(
                "Failed to parse config file: " + configPath
                    + " — " + e.getMessage());
        }
    }

    private Optional<Path> resolveConfigFile(Path explicitPath, Path projectRoot) {
        if (explicitPath != null) {
            LOG.debug("Using explicit config path: {}", explicitPath);
            return Optional.of(explicitPath);
        }

        Path localConfig = projectRoot.resolve(CONFIG_FILE_NAME);
        if (Files.exists(localConfig)) {
            LOG.debug("Found local config: {}", localConfig);
            return Optional.of(localConfig);
        }

        return Optional.empty();
    }

    /**
     * Writes a config to a YAML file.
     */
    public void save(SpringForgeConfig config, Path outputPath) throws IOException {
        Files.createDirectories(outputPath.getParent());
        yamlMapper.writeValue(outputPath.toFile(), config);
    }

    /**
     * Wrapper to handle the top-level "springforge:" key in YAML.
     */
    static class ConfigWrapper {
        public SpringForgeConfig springforge;
    }

    /**
     * Exception thrown when config loading fails (maps to exit code 5).
     */
    public static class ConfigLoadException extends Exception {
        public ConfigLoadException(String message) {
            super(message);
        }
    }
}
