package dev.springforge.tui.state;

import java.util.EnumSet;

import dev.springforge.engine.model.ConflictStrategy;
import dev.springforge.engine.model.Layer;
import dev.springforge.engine.model.MapperLib;
import dev.springforge.engine.model.SpringVersion;

/**
 * State for the layer configuration screen (S3).
 */
public record LayerConfigState(
    EnumSet<Layer> selectedLayers,
    SpringVersion springVersion,
    MapperLib mapperLib,
    ConflictStrategy conflictStrategy,
    int focusedIndex,
    int entityCount
) {

    public static LayerConfigState initial(int entityCount) {
        return new LayerConfigState(
            EnumSet.allOf(Layer.class),
            SpringVersion.V3,
            MapperLib.MAPSTRUCT,
            ConflictStrategy.SKIP,
            0,
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
            conflictStrategy, focusedIndex, entityCount);
    }

    public LayerConfigState withSpringVersion(SpringVersion version) {
        return new LayerConfigState(selectedLayers, version, mapperLib,
            conflictStrategy, focusedIndex, entityCount);
    }

    public LayerConfigState withMapperLib(MapperLib lib) {
        return new LayerConfigState(selectedLayers, springVersion, lib,
            conflictStrategy, focusedIndex, entityCount);
    }

    public LayerConfigState withConflictStrategy(ConflictStrategy strategy) {
        return new LayerConfigState(selectedLayers, springVersion, mapperLib,
            strategy, focusedIndex, entityCount);
    }

    public LayerConfigState moveFocusUp() {
        return new LayerConfigState(selectedLayers, springVersion, mapperLib,
            conflictStrategy, Math.max(0, focusedIndex - 1), entityCount);
    }

    public LayerConfigState moveFocusDown() {
        int maxIndex = Layer.values().length - 1;
        return new LayerConfigState(selectedLayers, springVersion, mapperLib,
            conflictStrategy, Math.min(maxIndex, focusedIndex + 1), entityCount);
    }

    public int estimatedFileCount() {
        return entityCount * selectedLayers.size();
    }
}
