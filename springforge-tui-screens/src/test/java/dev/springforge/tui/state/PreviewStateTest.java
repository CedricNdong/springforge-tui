package dev.springforge.tui.state;

import java.nio.file.Path;
import java.util.List;

import dev.springforge.engine.model.GeneratedFile;
import dev.springforge.engine.model.Layer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PreviewStateTest {

    private List<GeneratedFile> testFiles() {
        return List.of(
            new GeneratedFile(Path.of("dto/UserRequestDto.java"),
                "public class UserRequestDto {}", Layer.DTO_REQUEST, "User"),
            new GeneratedFile(Path.of("dto/UserResponseDto.java"),
                "public class UserResponseDto {}", Layer.DTO_RESPONSE, "User"),
            new GeneratedFile(Path.of("repository/UserRepository.java"),
                "public interface UserRepository {}", Layer.REPOSITORY, "User")
        );
    }

    @Test
    @DisplayName("should start with first file selected")
    void shouldStartWithFirstFile() {
        PreviewState state = PreviewState.initial(testFiles());
        assertThat(state.selectedFileIndex()).isEqualTo(0);
        assertThat(state.selectedFile().entityName()).isEqualTo("User");
    }

    @Test
    @DisplayName("should navigate to next and previous files")
    void shouldNavigateFiles() {
        PreviewState state = PreviewState.initial(testFiles());
        PreviewState next = state.selectNext();
        assertThat(next.selectedFileIndex()).isEqualTo(1);

        PreviewState prev = next.selectPrevious();
        assertThat(prev.selectedFileIndex()).isEqualTo(0);
    }

    @Test
    @DisplayName("should not go below zero or above max")
    void shouldClampNavigation() {
        PreviewState state = PreviewState.initial(testFiles());
        PreviewState prev = state.selectPrevious();
        assertThat(prev.selectedFileIndex()).isEqualTo(0);

        PreviewState last = state.selectNext().selectNext().selectNext().selectNext();
        assertThat(last.selectedFileIndex()).isEqualTo(2);
    }

    @Test
    @DisplayName("should scroll code preview")
    void shouldScrollPreview() {
        PreviewState state = PreviewState.initial(testFiles());
        PreviewState scrolled = state.scrollDown().scrollDown();
        assertThat(scrolled.scrollOffset()).isEqualTo(2);

        PreviewState scrolledUp = scrolled.scrollUp();
        assertThat(scrolledUp.scrollOffset()).isEqualTo(1);
    }

    @Test
    @DisplayName("should reset scroll when selecting different file")
    void shouldResetScrollOnFileChange() {
        PreviewState state = PreviewState.initial(testFiles());
        PreviewState scrolled = state.scrollDown().scrollDown();
        PreviewState next = scrolled.selectNext();
        assertThat(next.scrollOffset()).isEqualTo(0);
    }

    @Test
    @DisplayName("should render preview in-memory without writing files")
    void shouldRenderInMemory() {
        PreviewState state = PreviewState.initial(testFiles());
        GeneratedFile selected = state.selectedFile();
        assertThat(selected.content()).isEqualTo("public class UserRequestDto {}");
    }
}
