package dev.springforge.tui.state;

import dev.springforge.engine.model.ConflictStrategy;
import dev.springforge.engine.model.Layer;
import dev.springforge.engine.model.MapperLib;
import dev.springforge.engine.model.SpringVersion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LayerConfigStateTest {

    @Test
    @DisplayName("should start with all layers selected")
    void shouldStartWithAllLayers() {
        LayerConfigState state = LayerConfigState.initial(3);
        assertThat(state.selectedLayers()).hasSize(Layer.values().length);
        assertThat(state.entityCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("should toggle layer on and off")
    void shouldToggleLayer() {
        LayerConfigState state = LayerConfigState.initial(2);
        LayerConfigState toggled = state.toggleLayer(Layer.MAPPER);
        assertThat(toggled.selectedLayers()).doesNotContain(Layer.MAPPER);

        LayerConfigState reToggled = toggled.toggleLayer(Layer.MAPPER);
        assertThat(reToggled.selectedLayers()).contains(Layer.MAPPER);
    }

    @Test
    @DisplayName("should compute estimated file count")
    void shouldComputeFileCount() {
        LayerConfigState state = LayerConfigState.initial(5);
        assertThat(state.estimatedFileCount()).isEqualTo(5 * Layer.values().length);
    }

    @Test
    @DisplayName("should switch Spring version")
    void shouldSwitchSpringVersion() {
        LayerConfigState state = LayerConfigState.initial(1);
        assertThat(state.springVersion()).isEqualTo(SpringVersion.V3);

        LayerConfigState switched = state.withSpringVersion(SpringVersion.V2);
        assertThat(switched.springVersion()).isEqualTo(SpringVersion.V2);
    }

    @Test
    @DisplayName("should switch mapper library")
    void shouldSwitchMapperLib() {
        LayerConfigState state = LayerConfigState.initial(1);
        assertThat(state.mapperLib()).isEqualTo(MapperLib.MAPSTRUCT);

        LayerConfigState switched = state.withMapperLib(MapperLib.MODEL_MAPPER);
        assertThat(switched.mapperLib()).isEqualTo(MapperLib.MODEL_MAPPER);
    }

    @Test
    @DisplayName("should switch conflict strategy")
    void shouldSwitchConflictStrategy() {
        LayerConfigState state = LayerConfigState.initial(1);
        assertThat(state.conflictStrategy()).isEqualTo(ConflictStrategy.SKIP);

        LayerConfigState switched = state.withConflictStrategy(ConflictStrategy.OVERWRITE);
        assertThat(switched.conflictStrategy()).isEqualTo(ConflictStrategy.OVERWRITE);
    }

    // ── Panel navigation ────────────────────────────────────────────

    @Test
    @DisplayName("should start with LAYERS panel active")
    void shouldStartWithLayersPanel() {
        LayerConfigState state = LayerConfigState.initial(2);
        assertThat(state.activePanel()).isEqualTo(LayerConfigState.ActivePanel.LAYERS);
    }

    @Test
    @DisplayName("should switch active panel")
    void shouldSwitchActivePanel() {
        LayerConfigState state = LayerConfigState.initial(2)
            .withActivePanel(LayerConfigState.ActivePanel.OPTIONS);
        assertThat(state.activePanel()).isEqualTo(LayerConfigState.ActivePanel.OPTIONS);
    }

    @Test
    @DisplayName("should navigate within LAYERS panel")
    void shouldNavigateLayersPanel() {
        LayerConfigState state = LayerConfigState.initial(2);
        assertThat(state.focusedIndex()).isEqualTo(0);

        state = state.moveFocusDown();
        assertThat(state.focusedIndex()).isEqualTo(1);

        state = state.moveFocusUp();
        assertThat(state.focusedIndex()).isEqualTo(0);
    }

    @Test
    @DisplayName("should clamp LAYERS navigation at bounds")
    void shouldClampLayersNavigation() {
        LayerConfigState state = LayerConfigState.initial(2);
        state = state.moveFocusUp();
        assertThat(state.focusedIndex()).isEqualTo(0);

        for (int i = 0; i < 20; i++) {
            state = state.moveFocusDown();
        }
        assertThat(state.focusedIndex()).isEqualTo(LayerConfigState.DISPLAY_LAYERS.size() - 1);
    }

    @Test
    @DisplayName("should navigate within OPTIONS panel")
    void shouldNavigateOptionsPanel() {
        LayerConfigState state = LayerConfigState.initial(2)
            .withActivePanel(LayerConfigState.ActivePanel.OPTIONS);
        assertThat(state.optionFocusedIndex()).isEqualTo(0);

        state = state.moveFocusDown();
        assertThat(state.optionFocusedIndex()).isEqualTo(1);

        state = state.moveFocusUp();
        assertThat(state.optionFocusedIndex()).isEqualTo(0);
    }

    @Test
    @DisplayName("should clamp OPTIONS navigation at bounds")
    void shouldClampOptionsNavigation() {
        LayerConfigState state = LayerConfigState.initial(2)
            .withActivePanel(LayerConfigState.ActivePanel.OPTIONS);

        for (int i = 0; i < 10; i++) {
            state = state.moveFocusDown();
        }
        assertThat(state.optionFocusedIndex()).isEqualTo(LayerConfigState.OPTION_COUNT - 1);
    }

    // ── Toggle focused option ────���──────────────────────────────────

    @Test
    @DisplayName("should toggle Spring version via toggleFocusedOption")
    void shouldToggleSpringVersionViaOption() {
        LayerConfigState state = LayerConfigState.initial(1)
            .withActivePanel(LayerConfigState.ActivePanel.OPTIONS);
        assertThat(state.springVersion()).isEqualTo(SpringVersion.V3);

        state = state.toggleFocusedOption(); // index 0 = Spring version
        assertThat(state.springVersion()).isEqualTo(SpringVersion.V2);

        state = state.toggleFocusedOption();
        assertThat(state.springVersion()).isEqualTo(SpringVersion.V3);
    }

    @Test
    @DisplayName("should toggle mapper via toggleFocusedOption")
    void shouldToggleMapperViaOption() {
        LayerConfigState state = LayerConfigState.initial(1)
            .withActivePanel(LayerConfigState.ActivePanel.OPTIONS)
            .moveFocusDown(); // index 1 = Mapper
        assertThat(state.mapperLib()).isEqualTo(MapperLib.MAPSTRUCT);

        state = state.toggleFocusedOption();
        assertThat(state.mapperLib()).isEqualTo(MapperLib.MODEL_MAPPER);
    }

    // ── Migration ───────────────────────────────────────────────────

    @Test
    @DisplayName("should start with Liquibase migration")
    void shouldStartWithLiquibase() {
        LayerConfigState state = LayerConfigState.initial(1);
        assertThat(state.migrationChoice()).isEqualTo(LayerConfigState.MigrationChoice.LIQUIBASE);
        assertThat(state.selectedLayers()).contains(Layer.LIQUIBASE);
    }

    @Test
    @DisplayName("should cycle migration Liquibase → Flyway → None → Liquibase")
    void shouldCycleMigration() {
        LayerConfigState state = LayerConfigState.initial(1)
            .withActivePanel(LayerConfigState.ActivePanel.OPTIONS);
        // Navigate to migration option (index 3)
        state = state.moveFocusDown().moveFocusDown().moveFocusDown();

        // Liquibase → Flyway
        state = state.toggleFocusedOption();
        assertThat(state.migrationChoice()).isEqualTo(LayerConfigState.MigrationChoice.FLYWAY);
        assertThat(state.selectedLayers()).contains(Layer.FLYWAY);
        assertThat(state.selectedLayers()).doesNotContain(Layer.LIQUIBASE);

        // Flyway → None
        state = state.toggleFocusedOption();
        assertThat(state.migrationChoice()).isEqualTo(LayerConfigState.MigrationChoice.NONE);
        assertThat(state.selectedLayers()).doesNotContain(Layer.FLYWAY);
        assertThat(state.selectedLayers()).doesNotContain(Layer.LIQUIBASE);

        // None → Liquibase
        state = state.toggleFocusedOption();
        assertThat(state.migrationChoice()).isEqualTo(LayerConfigState.MigrationChoice.LIQUIBASE);
        assertThat(state.selectedLayers()).contains(Layer.LIQUIBASE);
    }

    @Test
    @DisplayName("should sync selected layers when changing migration choice")
    void shouldSyncLayersWithMigrationChoice() {
        LayerConfigState state = LayerConfigState.initial(1)
            .withMigrationChoice(LayerConfigState.MigrationChoice.FLYWAY);

        assertThat(state.selectedLayers()).contains(Layer.FLYWAY);
        assertThat(state.selectedLayers()).doesNotContain(Layer.LIQUIBASE);
    }
}
