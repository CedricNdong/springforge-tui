package dev.springforge.tui.state;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SplashStateTest {

    @Test
    @DisplayName("should start with initial state")
    void shouldStartWithInitialState() {
        SplashState state = SplashState.initial();
        assertThat(state.scanComplete()).isFalse();
        assertThat(state.totalFiles()).isEqualTo(0);
        assertThat(state.errorMessage()).isNull();
    }

    @Test
    @DisplayName("should transition to complete state")
    void shouldTransitionToComplete() {
        SplashState state = SplashState.initial().withComplete(10);
        assertThat(state.scanComplete()).isTrue();
        assertThat(state.totalFiles()).isEqualTo(10);
    }

    @Test
    @DisplayName("should compute progress percentage")
    void shouldComputeProgress() {
        SplashState state = new SplashState(10, 5, "User.java", false, null);
        assertThat(state.progressPercent()).isEqualTo(50);
    }

    @Test
    @DisplayName("should handle error state")
    void shouldHandleError() {
        SplashState state = SplashState.initial().withError("Directory not found");
        assertThat(state.errorMessage()).isEqualTo("Directory not found");
        assertThat(state.scanComplete()).isFalse();
    }
}
