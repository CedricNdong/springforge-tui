package dev.springforge.tui.state;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import dev.springforge.engine.model.EntityDescriptor;

/**
 * State for the entity selection screen (S2).
 */
public record EntitySelectionState(
    List<EntityDescriptor> entities,
    Set<String> selectedEntityNames,
    int focusedIndex,
    String filterText,
    boolean showHelp
) {

    public static EntitySelectionState initial(List<EntityDescriptor> entities) {
        return new EntitySelectionState(entities, new HashSet<>(), 0, "", false);
    }

    public EntitySelectionState moveFocusUp() {
        List<EntityDescriptor> filtered = filteredEntities();
        if (filtered.isEmpty()) return this;
        int newIndex = Math.max(0, focusedIndex - 1);
        return new EntitySelectionState(entities, selectedEntityNames, newIndex, filterText, showHelp);
    }

    public EntitySelectionState moveFocusDown() {
        List<EntityDescriptor> filtered = filteredEntities();
        if (filtered.isEmpty()) return this;
        int newIndex = Math.min(filtered.size() - 1, focusedIndex + 1);
        return new EntitySelectionState(entities, selectedEntityNames, newIndex, filterText, showHelp);
    }

    public EntitySelectionState toggleSelected() {
        List<EntityDescriptor> filtered = filteredEntities();
        if (filtered.isEmpty() || focusedIndex >= filtered.size()) return this;
        String name = filtered.get(focusedIndex).className();
        Set<String> newSelected = new HashSet<>(selectedEntityNames);
        if (newSelected.contains(name)) {
            newSelected.remove(name);
        } else {
            newSelected.add(name);
        }
        return new EntitySelectionState(entities, newSelected, focusedIndex, filterText, showHelp);
    }

    public EntitySelectionState selectAll() {
        Set<String> all = new HashSet<>(selectedEntityNames);
        filteredEntities().stream()
            .map(EntityDescriptor::className)
            .forEach(all::add);
        return new EntitySelectionState(entities, all, focusedIndex, filterText, showHelp);
    }

    public EntitySelectionState selectNone() {
        if (filterText.isEmpty()) {
            return new EntitySelectionState(entities, Set.of(), focusedIndex, filterText, showHelp);
        }
        // Only deselect filtered entities, keep others selected
        Set<String> filtered = filteredEntities().stream()
            .map(EntityDescriptor::className)
            .collect(Collectors.toSet());
        Set<String> remaining = new HashSet<>(selectedEntityNames);
        remaining.removeAll(filtered);
        return new EntitySelectionState(entities, remaining, focusedIndex, filterText, showHelp);
    }

    public EntitySelectionState withFilter(String filterText) {
        return new EntitySelectionState(entities, selectedEntityNames, 0, filterText, showHelp);
    }

    public EntitySelectionState toggleHelp() {
        return new EntitySelectionState(entities, selectedEntityNames, focusedIndex, filterText, !showHelp);
    }

    public boolean hasSelection() {
        return !selectedEntityNames.isEmpty();
    }

    public List<EntityDescriptor> selectedEntities() {
        return entities.stream()
            .filter(e -> selectedEntityNames.contains(e.className()))
            .toList();
    }

    public List<EntityDescriptor> filteredEntities() {
        if (filterText.isEmpty()) return entities;
        String lower = filterText.toLowerCase();
        return entities.stream()
            .filter(e -> e.className().toLowerCase().contains(lower))
            .toList();
    }

    public EntityDescriptor focusedEntity() {
        List<EntityDescriptor> filtered = filteredEntities();
        if (filtered.isEmpty() || focusedIndex >= filtered.size()) return null;
        return filtered.get(focusedIndex);
    }
}
