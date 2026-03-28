package dev.springforge.tui.state;

import dev.springforge.engine.model.FileGenerationResult;
import dev.springforge.engine.model.GenerationStatus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenerationProgressStateTest {

    @Test
    @DisplayName("should start in progress with zero completed")
    void shouldStartInProgress() {
        GenerationProgressState state = GenerationProgressState.initial(5);
        assertThat(state.overallStatus()).isEqualTo(
            GenerationProgressState.OverallStatus.IN_PROGRESS);
        assertThat(state.completedFiles()).isEqualTo(0);
        assertThat(state.progressPercent()).isEqualTo(0);
    }

    @Test
    @DisplayName("should track created files")
    void shouldTrackCreatedFiles() {
        GenerationProgressState state = GenerationProgressState.initial(3)
            .withFileResult(new FileGenerationResult(
                "dto/UserRequestDto.java", GenerationStatus.CREATED, ""));

        assertThat(state.completedFiles()).isEqualTo(1);
        assertThat(state.progressPercent()).isEqualTo(33);
    }

    @Test
    @DisplayName("should track skipped files")
    void shouldTrackSkippedFiles() {
        GenerationProgressState state = GenerationProgressState.initial(2)
            .withFileResult(new FileGenerationResult(
                "mapper/UserMapper.java", GenerationStatus.SKIPPED, "exists"));

        assertThat(state.skippedFiles()).isEqualTo(1);
        assertThat(state.progressPercent()).isEqualTo(50);
    }

    @Test
    @DisplayName("should track error files")
    void shouldTrackErrorFiles() {
        GenerationProgressState state = GenerationProgressState.initial(2)
            .withFileResult(new FileGenerationResult(
                "dto/UserRequestDto.java", GenerationStatus.ERROR, "permission denied"));

        assertThat(state.errorFiles()).isEqualTo(1);
    }

    @Test
    @DisplayName("should transition to DONE when all files complete")
    void shouldTransitionToDone() {
        GenerationProgressState state = GenerationProgressState.initial(2)
            .withFileResult(new FileGenerationResult(
                "dto/UserRequestDto.java", GenerationStatus.CREATED, ""))
            .withFileResult(new FileGenerationResult(
                "dto/UserResponseDto.java", GenerationStatus.CREATED, ""));

        assertThat(state.overallStatus()).isEqualTo(
            GenerationProgressState.OverallStatus.DONE);
        assertThat(state.progressPercent()).isEqualTo(100);
    }

    @Test
    @DisplayName("should transition to ERROR when errors exist at completion")
    void shouldTransitionToError() {
        GenerationProgressState state = GenerationProgressState.initial(2)
            .withFileResult(new FileGenerationResult(
                "dto/UserRequestDto.java", GenerationStatus.CREATED, ""))
            .withFileResult(new FileGenerationResult(
                "dto/UserResponseDto.java", GenerationStatus.ERROR, "failed"));

        assertThat(state.overallStatus()).isEqualTo(
            GenerationProgressState.OverallStatus.ERROR);
    }

    @Test
    @DisplayName("should maintain log of all file results")
    void shouldMaintainLog() {
        GenerationProgressState state = GenerationProgressState.initial(3)
            .withFileResult(new FileGenerationResult(
                "dto/UserRequestDto.java", GenerationStatus.CREATED, ""))
            .withFileResult(new FileGenerationResult(
                "mapper/UserMapper.java", GenerationStatus.SKIPPED, "exists"));

        assertThat(state.log()).hasSize(2);
        assertThat(state.log().get(0).filePath()).isEqualTo("dto/UserRequestDto.java");
        assertThat(state.log().get(1).status()).isEqualTo(GenerationStatus.SKIPPED);
    }

    // ── Log scrolling ───────────────────────────────────────────────

    @Test
    @DisplayName("should start with zero scroll offset")
    void shouldStartWithZeroScrollOffset() {
        GenerationProgressState state = GenerationProgressState.initial(5);
        assertThat(state.logScrollOffset()).isEqualTo(0);
    }

    @Test
    @DisplayName("should auto-scroll to bottom on new file result")
    void shouldAutoScrollOnNewResult() {
        GenerationProgressState state = GenerationProgressState.initial(5)
            .withFileResult(new FileGenerationResult(
                "dto/UserRequestDto.java", GenerationStatus.CREATED, ""))
            .withFileResult(new FileGenerationResult(
                "dto/UserResponseDto.java", GenerationStatus.CREATED, ""))
            .withFileResult(new FileGenerationResult(
                "mapper/UserMapper.java", GenerationStatus.CREATED, ""));

        assertThat(state.logScrollOffset()).isEqualTo(2); // last entry index
    }

    @Test
    @DisplayName("should scroll log up and down")
    void shouldScrollLogUpAndDown() {
        GenerationProgressState state = GenerationProgressState.initial(5)
            .withFileResult(new FileGenerationResult(
                "dto/UserRequestDto.java", GenerationStatus.CREATED, ""))
            .withFileResult(new FileGenerationResult(
                "dto/UserResponseDto.java", GenerationStatus.CREATED, ""))
            .withFileResult(new FileGenerationResult(
                "mapper/UserMapper.java", GenerationStatus.CREATED, ""));

        // Auto-scrolled to 2, scroll up
        state = state.scrollLogUp();
        assertThat(state.logScrollOffset()).isEqualTo(1);

        state = state.scrollLogUp();
        assertThat(state.logScrollOffset()).isEqualTo(0);
    }

    @Test
    @DisplayName("should clamp scroll offset at bounds")
    void shouldClampScrollOffset() {
        GenerationProgressState state = GenerationProgressState.initial(2)
            .withFileResult(new FileGenerationResult(
                "dto/UserRequestDto.java", GenerationStatus.CREATED, ""));

        // Clamp at top
        state = state.scrollLogUp().scrollLogUp().scrollLogUp();
        assertThat(state.logScrollOffset()).isEqualTo(0);

        // Clamp at bottom
        state = state.scrollLogDown().scrollLogDown().scrollLogDown();
        assertThat(state.logScrollOffset()).isEqualTo(0); // only 1 entry, max offset = 0
    }

    @Test
    @DisplayName("should track current file being generated")
    void shouldTrackCurrentFile() {
        GenerationProgressState state = GenerationProgressState.initial(3)
            .withCurrentFile("dto/UserRequestDto.java");

        assertThat(state.currentFile()).isEqualTo("dto/UserRequestDto.java");
    }
}
