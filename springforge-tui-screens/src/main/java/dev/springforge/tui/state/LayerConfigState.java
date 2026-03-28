package dev.springforge.tui.state;

import java.util.EnumSet;
import java.util.List;

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
 *
 * <p>Migration (Liquibase/Flyway/None) is an option in the right panel,
 * not a toggleable layer in the left panel.
 */
public record LayerConfigState(
    EnumSet<Layer> selectedLayers,
    SpringVersion springVersion,
    MapperLib mapperLib,
    ConflictStrategy conflictStrategy,
    MigrationChoice migrationChoice,
    int focusedIndex,
    int optionFocusedIndex,
    ActivePanel activePanel,
    int entityCount
) {

    /** Which panel is currently active for navigation. */
    public enum ActivePanel { LAYERS, OPTIONS }

    /** Migration tool choice — maps to Layer.LIQUIBASE / Layer.FLYWAY or neither. */
    public enum MigrationChoice { LIQUIBASE, FLYWAY, NONE }

    /** Layers shown in the left panel (excludes migration). */
    public static final List<Layer> DISPLAY_LAYERS = List.of(
        Layer.DTO_REQUEST, Layer.DTO_RESPONSE, Layer.MAPPER,
        Layer.REPOSITORY, Layer.SERVICE, Layer.SERVICE_IMPL,
        Layer.CONTROLLER, Layer.FILE_UPLOAD
    );

    /** Number of options in the OPTIONS panel. */
    public static final int OPTION_COUNT = 4;

    public static LayerConfigState initial(int entityCount) {
        return new LayerConfigState(
            EnumSet.allOf(Layer.class),
            SpringVersion.V3,
            MapperLib.MAPSTRUCT,
            ConflictStrategy.SKIP,
            MigrationChoice.LIQUIBASE,
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
            conflictStrategy, migrationChoice, focusedIndex, optionFocusedIndex,
            activePanel, entityCount);
    }

    public LayerConfigState withSpringVersion(SpringVersion version) {
        return new LayerConfigState(selectedLayers, version, mapperLib,
            conflictStrategy, migrationChoice, focusedIndex, optionFocusedIndex,
            activePanel, entityCount);
    }

    public LayerConfigState withMapperLib(MapperLib lib) {
        return new LayerConfigState(selectedLayers, springVersion, lib,
            conflictStrategy, migrationChoice, focusedIndex, optionFocusedIndex,
            activePanel, entityCount);
    }

    public LayerConfigState withConflictStrategy(ConflictStrategy strategy) {
        return new LayerConfigState(selectedLayers, springVersion, mapperLib,
            strategy, migrationChoice, focusedIndex, optionFocusedIndex,
            activePanel, entityCount);
    }

    public LayerConfigState withMigrationChoice(MigrationChoice choice) {
        // Sync the selectedLayers with the migration choice
        EnumSet<Layer> newLayers = EnumSet.copyOf(selectedLayers);
        newLayers.remove(Layer.LIQUIBASE);
        newLayers.remove(Layer.FLYWAY);
        switch (choice) {
            case LIQUIBASE -> newLayers.add(Layer.LIQUIBASE);
            case FLYWAY -> newLayers.add(Layer.FLYWAY);
            case NONE -> { /* neither */ }
            default -> { /* no action */ }
        }
        return new LayerConfigState(newLayers, springVersion, mapperLib,
            conflictStrategy, choice, focusedIndex, optionFocusedIndex,
            activePanel, entityCount);
    }

    public LayerConfigState withActivePanel(ActivePanel panel) {
        return new LayerConfigState(selectedLayers, springVersion, mapperLib,
            conflictStrategy, migrationChoice, focusedIndex, optionFocusedIndex,
            panel, entityCount);
    }

    public LayerConfigState moveFocusUp() {
        if (activePanel == ActivePanel.LAYERS) {
            return new LayerConfigState(selectedLayers, springVersion, mapperLib,
                conflictStrategy, migrationChoice,
                Math.max(0, focusedIndex - 1),
                optionFocusedIndex, activePanel, entityCount);
        } else {
            return new LayerConfigState(selectedLayers, springVersion, mapperLib,
                conflictStrategy, migrationChoice, focusedIndex,
                Math.max(0, optionFocusedIndex - 1), activePanel, entityCount);
        }
    }

    public LayerConfigState moveFocusDown() {
        if (activePanel == ActivePanel.LAYERS) {
            int maxIndex = DISPLAY_LAYERS.size() - 1;
            return new LayerConfigState(selectedLayers, springVersion, mapperLib,
                conflictStrategy, migrationChoice,
                Math.min(maxIndex, focusedIndex + 1),
                optionFocusedIndex, activePanel, entityCount);
        } else {
            return new LayerConfigState(selectedLayers, springVersion, mapperLib,
                conflictStrategy, migrationChoice, focusedIndex,
                Math.min(OPTION_COUNT - 1, optionFocusedIndex + 1), activePanel, entityCount);
        }
    }

    /** Toggle/cycle the focused option's value in the OPTIONS panel. */
    public LayerConfigState toggleFocusedOption() {
        return switch (optionFocusedIndex) {
            case 0 -> withSpringVersion(
                springVersion == SpringVersion.V3 ? SpringVersion.V2 : SpringVersion.V3);
            case 1 -> withMapperLib(
                mapperLib == MapperLib.MAPSTRUCT ? MapperLib.MODEL_MAPPER : MapperLib.MAPSTRUCT);
            case 2 -> withConflictStrategy(
                conflictStrategy == ConflictStrategy.SKIP
                    ? ConflictStrategy.OVERWRITE : ConflictStrategy.SKIP);
            case 3 -> cycleMigration();
            default -> this;
        };
    }

    private LayerConfigState cycleMigration() {
        MigrationChoice next = switch (migrationChoice) {
            case LIQUIBASE -> MigrationChoice.FLYWAY;
            case FLYWAY -> MigrationChoice.NONE;
            case NONE -> MigrationChoice.LIQUIBASE;
        };
        return withMigrationChoice(next);
    }

    public int estimatedFileCount() {
        return entityCount * selectedLayers.size();
    }
}
