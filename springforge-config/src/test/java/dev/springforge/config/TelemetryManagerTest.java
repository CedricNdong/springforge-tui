package dev.springforge.config;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import dev.springforge.config.TelemetryManager.TelemetryEvent;
import dev.springforge.config.TelemetryManager.TelemetryPreference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class TelemetryManagerTest {

    @TempDir
    Path tempHome;

    private TelemetryManager manager;

    @BeforeEach
    void setUp() {
        manager = new TelemetryManager(tempHome);
    }

    @Test
    @DisplayName("should return UNSET when no config file exists")
    void shouldReturnUnsetWhenNoConfigFile() {
        TelemetryPreference pref = manager.loadPreference();

        assertThat(pref).isEqualTo(TelemetryPreference.UNSET);
        assertThat(manager.shouldPrompt()).isTrue();
    }

    @Test
    @DisplayName("should save and load ENABLED preference")
    void shouldSaveAndLoadEnabled() throws Exception {
        manager.savePreference(TelemetryPreference.ENABLED);

        TelemetryManager reloaded = new TelemetryManager(tempHome);
        TelemetryPreference pref = reloaded.loadPreference();

        assertThat(pref).isEqualTo(TelemetryPreference.ENABLED);
        assertThat(reloaded.isEnabled()).isTrue();
        assertThat(reloaded.shouldPrompt()).isFalse();
    }

    @Test
    @DisplayName("should save and load DISABLED preference")
    void shouldSaveAndLoadDisabled() throws Exception {
        manager.savePreference(TelemetryPreference.DISABLED);

        TelemetryManager reloaded = new TelemetryManager(tempHome);
        TelemetryPreference pref = reloaded.loadPreference();

        assertThat(pref).isEqualTo(TelemetryPreference.DISABLED);
        assertThat(reloaded.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("should save and load ALWAYS_DISABLED preference")
    void shouldSaveAndLoadAlwaysDisabled() throws Exception {
        manager.savePreference(TelemetryPreference.ALWAYS_DISABLED);

        TelemetryManager reloaded = new TelemetryManager(tempHome);
        TelemetryPreference pref = reloaded.loadPreference();

        assertThat(pref).isEqualTo(TelemetryPreference.ALWAYS_DISABLED);
        assertThat(reloaded.isEnabled()).isFalse();
        assertThat(reloaded.shouldPrompt()).isFalse();
    }

    @Test
    @DisplayName("should create config directory if it does not exist")
    void shouldCreateConfigDirectory() throws Exception {
        Path configDir = tempHome.resolve(".springforge");
        assertThat(Files.exists(configDir)).isFalse();

        manager.savePreference(TelemetryPreference.ENABLED);

        assertThat(Files.exists(configDir)).isTrue();
        assertThat(Files.exists(configDir.resolve("config.yml"))).isTrue();
    }

    @Test
    @DisplayName("should preserve existing config when saving telemetry preference")
    void shouldPreserveExistingConfig() throws Exception {
        Path configDir = tempHome.resolve(".springforge");
        Files.createDirectories(configDir);
        Files.writeString(configDir.resolve("config.yml"),
            "theme: dark\n", StandardCharsets.UTF_8);

        manager.savePreference(TelemetryPreference.ENABLED);

        String content = Files.readString(
            configDir.resolve("config.yml"), StandardCharsets.UTF_8);
        assertThat(content).contains("theme: dark");
        assertThat(content).contains("telemetry: enabled");
    }

    @Test
    @DisplayName("should update existing telemetry preference in config")
    void shouldUpdateExistingPreference() throws Exception {
        manager.savePreference(TelemetryPreference.ENABLED);
        manager.savePreference(TelemetryPreference.DISABLED);

        TelemetryManager reloaded = new TelemetryManager(tempHome);
        TelemetryPreference pref = reloaded.loadPreference();

        assertThat(pref).isEqualTo(TelemetryPreference.DISABLED);
    }

    @Test
    @DisplayName("should not send events when telemetry is disabled")
    void shouldNotRecordWhenDisabled() throws Exception {
        manager.savePreference(TelemetryPreference.DISABLED);
        manager.loadPreference();

        // Should not throw or do anything
        manager.recordEvent(new TelemetryEvent(
            5, Set.of("DTO", "SERVICE"), "3", "Linux", 10, 0
        ));

        assertThat(manager.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("should record event as no-op in v1 when enabled")
    void shouldRecordEventAsNoOpInV1() throws Exception {
        manager.savePreference(TelemetryPreference.ENABLED);
        manager.loadPreference();

        // Should not throw — no-op in v1
        manager.recordEvent(new TelemetryEvent(
            3, Set.of("DTO", "CONTROLLER", "SERVICE"), "3", "Linux", 8, 1
        ));

        assertThat(manager.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("should handle malformed config file gracefully")
    void shouldHandleMalformedConfig() throws Exception {
        Path configDir = tempHome.resolve(".springforge");
        Files.createDirectories(configDir);
        Files.writeString(configDir.resolve("config.yml"),
            "invalid: [yaml: broken", StandardCharsets.UTF_8);

        TelemetryPreference pref = manager.loadPreference();

        assertThat(pref).isEqualTo(TelemetryPreference.UNSET);
    }
}
