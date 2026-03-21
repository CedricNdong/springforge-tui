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
}
