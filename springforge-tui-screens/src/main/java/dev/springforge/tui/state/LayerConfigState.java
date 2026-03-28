package dev.springforge.tui.state;

import java.util.EnumSet;

import dev.springforge.engine.model.ConflictStrategy;
import dev.springforge.engine.model.Layer;
import dev.springforge.engine.model.MapperLib;
import dev.springforge.engine.model.SpringVersion;

/**
 * State for the layer configuration screen (S3).
 *
 * <p>Two navigable panels: LAYERS (left) and OPTIONS (right).
 * The user switches between them with [1]/[2] and navigates
 * within each panel using ↑/↓.
 */
public record LayerConfigState(
    EnumSet<Layer> selectedLayers,
    SpringVersion springVersion,
    MapperLib mapperLib,
    ConflictStrategy conflictStrategy,
    int focusedIndex,
    int optionFocusedIndex,
    ActivePanel activePanel,
    int entityCount
) {

    /** Which panel is currently active for navigation. */
    public enum ActivePanel { LAYERS, OPTIONS }

    /** Number of options in the OPTIONS panel (Spring Boot, Mapper, Conflict). */
    public static final int OPTION_COUNT = 3;

    public static LayerConfigState initial(int entityCount) {
        return new LayerConfigState(
            EnumSet.allOf(Layer.class),
            SpringVersion.V3,
            MapperLib.MAPSTRUCT,
            ConflictStrategy.SKIP,
            0,
            0,
            ActivePanel.LAYERS,
            entityCount
        );
    }

    public LayerConfigState toggleLayer(Layer layer) {
        EnumSet<Layer> newLayers = EnumSet.copyOf(selectedLayers);
        if (newLayers.contains(layer)) {
            newLayers.remove(layer);
        } else {
            newLayers.add(layer);
        }
        return new LayerConfigState(newLayers, springVersion, mapperLib,
            conflictStrategy, focusedIndex, optionFocusedIndex, activePanel, entityCount);
    }

    public LayerConfigState withSpringVersion(SpringVersion version) {
        return new LayerConfigState(selectedLayers, version, mapperLib,
            conflictStrategy, focusedIndex, optionFocusedIndex, activePanel, entityCount);
    }

    public LayerConfigState withMapperLib(MapperLib lib) {
        return new LayerConfigState(selectedLayers, springVersion, lib,
            conflictStrategy, focusedIndex, optionFocusedIndex, activePanel, entityCount);
    }

    public LayerConfigState withConflictStrategy(ConflictStrategy strategy) {
        return new LayerConfigState(selectedLayers, springVersion, mapperLib,
            strategy, focusedIndex, optionFocusedIndex, activePanel, entityCount);
    }

    public LayerConfigState withActivePanel(ActivePanel panel) {
        return new LayerConfigState(selectedLayers, springVersion, mapperLib,
            conflictStrategy, focusedIndex, optionFocusedIndex, panel, entityCount);
    }

    public LayerConfigState moveFocusUp() {
        if (activePanel == ActivePanel.LAYERS) {
            return new LayerConfigState(selectedLayers, springVersion, mapperLib,
                conflictStrategy, Math.max(0, focusedIndex - 1),
                optionFocusedIndex, activePanel, entityCount);
        } else {
            return new LayerConfigState(selectedLayers, springVersion, mapperLib,
                conflictStrategy, focusedIndex,
                Math.max(0, optionFocusedIndex - 1), activePanel, entityCount);
        }
    }

    public LayerConfigState moveFocusDown() {
        if (activePanel == ActivePanel.LAYERS) {
            int maxIndex = Layer.values().length - 1;
            return new LayerConfigState(selectedLayers, springVersion, mapperLib,
                conflictStrategy, Math.min(maxIndex, focusedIndex + 1),
                optionFocusedIndex, activePanel, entityCount);
        } else {
            return new LayerConfigState(selectedLayers, springVersion, mapperLib,
                conflictStrategy, focusedIndex,
                Math.min(OPTION_COUNT - 1, optionFocusedIndex + 1), activePanel, entityCount);
        }
    }

    /** Toggle the focused option's value in the OPTIONS panel. */
    public LayerConfigState toggleFocusedOption() {
        return switch (optionFocusedIndex) {
            case 0 -> withSpringVersion(
                springVersion == SpringVersion.V3 ? SpringVersion.V2 : SpringVersion.V3);
            case 1 -> withMapperLib(
                mapperLib == MapperLib.MAPSTRUCT ? MapperLib.MODEL_MAPPER : MapperLib.MAPSTRUCT);
            case 2 -> withConflictStrategy(
                conflictStrategy == ConflictStrategy.SKIP
                    ? ConflictStrategy.OVERWRITE : ConflictStrategy.SKIP);
            default -> this;
        };
    }

    public int estimatedFileCount() {
        return entityCount * selectedLayers.size();
    }
}
