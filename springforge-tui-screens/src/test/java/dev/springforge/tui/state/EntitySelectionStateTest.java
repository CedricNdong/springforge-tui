package dev.springforge.tui.state;

import java.util.List;
import java.util.Set;

import dev.springforge.engine.model.EntityDescriptor;
import dev.springforge.engine.model.FieldDescriptor;
import dev.springforge.engine.model.RelationType;
import dev.springforge.engine.model.SpringNamespace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EntitySelectionStateTest {

    private List<EntityDescriptor> entities;
    private EntitySelectionState state;

    @BeforeEach
    void setUp() {
        entities = List.of(
            createEntity("User"),
            createEntity("Product"),
            createEntity("Order")
        );
        state = EntitySelectionState.initial(entities);
    }

    private EntityDescriptor createEntity(String name) {
        return new EntityDescriptor(
            name, "com.example.model",
            List.of(
                new FieldDescriptor("id", "Long", null, true, false, false,
                    RelationType.NONE, null, false)
            ),
            Set.of("Entity"),
            SpringNamespace.JAKARTA,
            true, "id", "Long"
        );
    }

    @Test
    @DisplayName("should start with no selection and focus on first entity")
    void shouldStartWithNoSelection() {
        assertThat(state.hasSelection()).isFalse();
        assertThat(state.focusedIndex()).isEqualTo(0);
        assertThat(state.focusedEntity().className()).isEqualTo("User");
    }

    @Test
    @DisplayName("should toggle entity selection on and off")
    void shouldToggleSelection() {
        EntitySelectionState toggled = state.toggleSelected();
        assertThat(toggled.selectedEntityNames()).contains("User");
        assertThat(toggled.hasSelection()).isTrue();

        EntitySelectionState unToggled = toggled.toggleSelected();
        assertThat(unToggled.selectedEntityNames()).doesNotContain("User");
    }

    @Test
    @DisplayName("should move focus down and up")
    void shouldMoveFocus() {
        EntitySelectionState moved = state.moveFocusDown();
        assertThat(moved.focusedEntity().className()).isEqualTo("Product");

        EntitySelectionState movedUp = moved.moveFocusUp();
        assertThat(movedUp.focusedEntity().className()).isEqualTo("User");
    }

    @Test
    @DisplayName("should not move focus below last entity")
    void shouldNotMoveBelowLast() {
        EntitySelectionState moved = state.moveFocusDown().moveFocusDown().moveFocusDown();
        assertThat(moved.focusedEntity().className()).isEqualTo("Order");
    }

    @Test
    @DisplayName("should select all entities")
    void shouldSelectAll() {
        EntitySelectionState allSelected = state.selectAll();
        assertThat(allSelected.selectedEntityNames()).hasSize(3);
        assertThat(allSelected.selectedEntities()).hasSize(3);
    }

    @Test
    @DisplayName("should deselect all entities")
    void shouldSelectNone() {
        EntitySelectionState noneSelected = state.selectAll().selectNone();
        assertThat(noneSelected.hasSelection()).isFalse();
    }

    @Test
    @DisplayName("should filter entities by name")
    void shouldFilterEntities() {
        EntitySelectionState filtered = state.withFilter("prod");
        assertThat(filtered.filteredEntities()).hasSize(1);
        assertThat(filtered.filteredEntities().get(0).className()).isEqualTo("Product");
    }

    @Test
    @DisplayName("should toggle help overlay")
    void shouldToggleHelp() {
        assertThat(state.showHelp()).isFalse();
        EntitySelectionState withHelp = state.toggleHelp();
        assertThat(withHelp.showHelp()).isTrue();
    }
}
