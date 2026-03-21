package dev.springforge.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages anonymous opt-in telemetry preferences.
 *
 * <p>On first run, prompts the user to opt in or out. The preference is
 * stored in {@code ~/.springforge/config.yml}. Telemetry data never includes
 * entity names, code content, or file paths — only aggregate counts and
 * metadata (entity count, layer selection, Spring version, OS, success/error
 * count).
 *
 * <p>Note: In v1, no data is actually sent over the network. This class
 * manages preference storage and event recording only. Transmission will
 * be implemented in a future version.
 */
public class TelemetryManager {

    private static final Logger LOG = LoggerFactory.getLogger(TelemetryManager.class);
    private static final String CONFIG_DIR_NAME = ".springforge";
    private static final String CONFIG_FILE_NAME = "config.yml";

    private final Path configDir;
    private final Path configFile;
    private TelemetryPreference preference;

    /**
     * Telemetry preference options.
     */
    public enum TelemetryPreference {
        /** User has opted in to telemetry. */
        ENABLED,
        /** User has opted out for this session. */
        DISABLED,
        /** User has permanently opted out. */
        ALWAYS_DISABLED,
        /** User has not yet been asked. */
        UNSET
    }

    /**
     * Telemetry event data — contains only anonymous aggregate metadata.
     * Never includes entity names, code content, or file paths.
     */
    public record TelemetryEvent(
        int entityCount,
        java.util.Set<String> layerSelection,
        String springVersion,
        String os,
        int successCount,
        int errorCount
    ) {}

    /**
     * Creates a TelemetryManager using the default home directory.
     */
    public TelemetryManager() {
        this(Path.of(System.getProperty("user.home")));
    }

    /**
     * Creates a TelemetryManager with a custom home directory (for testing).
     */
    public TelemetryManager(Path homeDir) {
        this.configDir = homeDir.resolve(CONFIG_DIR_NAME);
        this.configFile = configDir.resolve(CONFIG_FILE_NAME);
        this.preference = TelemetryPreference.UNSET;
    }

    /**
     * Loads the telemetry preference from disk.
     *
     * @return the stored preference, or UNSET if not yet configured
     */
    public TelemetryPreference loadPreference() {
        if (!Files.exists(configFile)) {
            preference = TelemetryPreference.UNSET;
            return preference;
        }

        try {
            var yamlMapper = new ObjectMapper(new YAMLFactory());
            var tree = yamlMapper.readTree(configFile.toFile());
            var telemetryNode = tree.get("telemetry");
            if (telemetryNode != null) {
                String value = telemetryNode.asText();
                preference = parsePref(value);
            }
        } catch (IOException e) {
            LOG.warn("Failed to read telemetry config: {}", e.getMessage());
            preference = TelemetryPreference.UNSET;
        }

        return preference;
    }

    /**
     * Saves the telemetry preference to disk.
     */
    public void savePreference(TelemetryPreference pref) throws IOException {
        this.preference = pref;
        Files.createDirectories(configDir);

        String value = switch (pref) {
            case ENABLED -> "enabled";
            case DISABLED -> "disabled";
            case ALWAYS_DISABLED -> "always-disabled";
            case UNSET -> "unset";
        };

        // Read existing config if present, merge telemetry setting
        String content;
        if (Files.exists(configFile)) {
            String existing = Files.readString(configFile, StandardCharsets.UTF_8);
            if (existing.contains("telemetry:")) {
                content = existing.replaceFirst(
                    "telemetry:.*", "telemetry: " + value);
            } else {
                content = existing + "\ntelemetry: " + value + "\n";
            }
        } else {
            content = "telemetry: " + value + "\n";
        }

        Files.writeString(configFile, content, StandardCharsets.UTF_8);
        LOG.info("Telemetry preference saved: {}", value);
    }

    /**
     * Prompts the user for telemetry consent on first run.
     *
     * @return the chosen preference
     */
    public TelemetryPreference promptForConsent() {
        System.out.println();
        System.out.println("Help improve SpringForge TUI? "
            + "Send anonymous usage data "
            + "(no source code, no file contents).");
        System.out.print("[Y]es / [N]o / [A]lways no: ");
        System.out.flush();

        try {
            var reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8));
            String input = reader.readLine();
            if (input == null) {
                return TelemetryPreference.DISABLED;
            }

            String trimmed = input.trim().toLowerCase();
            return switch (trimmed) {
                case "y", "yes" -> TelemetryPreference.ENABLED;
                case "a", "always no", "always" ->
                    TelemetryPreference.ALWAYS_DISABLED;
                default -> TelemetryPreference.DISABLED;
            };
        } catch (IOException e) {
            LOG.warn("Could not read telemetry consent: {}", e.getMessage());
            return TelemetryPreference.DISABLED;
        }
    }

    /**
     * Checks if the user should be prompted for consent.
     */
    public boolean shouldPrompt() {
        return preference == TelemetryPreference.UNSET;
    }

    /**
     * Checks if telemetry is enabled.
     */
    public boolean isEnabled() {
        return preference == TelemetryPreference.ENABLED;
    }

    /**
     * Records a telemetry event. In v1, this is a no-op (no network calls).
     * The event is logged at debug level for diagnostic purposes only.
     */
    public void recordEvent(TelemetryEvent event) {
        if (!isEnabled()) {
            return;
        }
        // v1: no network calls — log only
        LOG.debug("Telemetry event (not sent in v1): "
            + "entities={}, layers={}, springVersion={}, os={}, "
            + "success={}, errors={}",
            event.entityCount(), event.layerSelection(),
            event.springVersion(), event.os(),
            event.successCount(), event.errorCount());
    }

    /**
     * Returns the current preference.
     */
    public TelemetryPreference getPreference() {
        return preference;
    }

    /**
     * Returns the config directory path.
     */
    public Path getConfigDir() {
        return configDir;
    }

    private static TelemetryPreference parsePref(String value) {
        return switch (value) {
            case "enabled" -> TelemetryPreference.ENABLED;
            case "always-disabled" -> TelemetryPreference.ALWAYS_DISABLED;
            case "disabled" -> TelemetryPreference.DISABLED;
            default -> TelemetryPreference.UNSET;
        };
    }
}
