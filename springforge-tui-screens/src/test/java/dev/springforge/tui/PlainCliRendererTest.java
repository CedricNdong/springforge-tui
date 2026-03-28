package dev.springforge.tui;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import dev.springforge.engine.model.EntityDescriptor;
import dev.springforge.engine.model.FieldDescriptor;
import dev.springforge.engine.model.FileGenerationResult;
import dev.springforge.engine.model.GeneratedFile;
import dev.springforge.engine.model.GenerationReport;
import dev.springforge.engine.model.GenerationStatus;
import dev.springforge.engine.model.Layer;
import dev.springforge.engine.model.RelationType;
import dev.springforge.engine.model.SpringNamespace;

import java.time.Duration;
import dev.springforge.tui.state.EntitySelectionState;
import dev.springforge.tui.state.ErrorState;
import dev.springforge.tui.state.GenerationProgressState;
import dev.springforge.tui.state.PreviewState;
import dev.springforge.tui.state.SplashState;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlainCliRendererTest {

    private ByteArrayOutputStream outputStream;
    private PlainCliRenderer renderer;

    @BeforeEach
    void setUp() {
        outputStream = new ByteArrayOutputStream();
        renderer = new PlainCliRenderer(new PrintStream(outputStream));
    }

    private String output() {
        return outputStream.toString(StandardCharsets.UTF_8);
    }

    private EntityDescriptor testEntity() {
        return new EntityDescriptor(
            "User", "com.example.model",
            List.of(
                new FieldDescriptor("id", "Long", null, true, false, false,
                    RelationType.NONE, null, false),
                new FieldDescriptor("username", "String", null, false, false, false,
                    RelationType.NONE, null, false)
            ),
            Set.of("Entity", "Data"),
            SpringNamespace.JAKARTA,
            true, "id", "Long"
        );
    }

    @Test
    @DisplayName("should render splash screen with scan progress")
    void shouldRenderSplash() {
        renderer.showSplash(SplashState.initial().withComplete(5));

        assertThat(output()).contains("SpringForge TUI");
        assertThat(output()).contains("Scan complete: 5 entity files found");
    }

    @Test
    @DisplayName("should render splash with error message")
    void shouldRenderSplashError() {
        renderer.showSplash(SplashState.initial().withError("Directory not found"));

        assertThat(output()).contains("Scan error: Directory not found");
    }

    @Test
    @DisplayName("should render entity selection with checkboxes")
    void shouldRenderEntitySelection() {
        EntityDescriptor entity = testEntity();
        EntitySelectionState state = EntitySelectionState.initial(List.of(entity));
        state = state.toggleSelected();

        renderer.showEntitySelection(state, noOpEntityCallbacks());

        assertThat(output()).contains("Entity Selection");
        assertThat(output()).contains("[x] User");
        assertThat(output()).contains("Selected: 1 entities");
    }

    @Test
    @DisplayName("should render entity details in selection screen")
    void shouldRenderEntityDetails() {
        EntityDescriptor entity = testEntity();
        EntitySelectionState state = EntitySelectionState.initial(List.of(entity));
        state = state.toggleSelected();

        renderer.showEntitySelection(state, noOpEntityCallbacks());

        assertThat(output()).contains("User");
        assertThat(output()).contains("com.example.model");
        assertThat(output()).contains("Long id");
        assertThat(output()).contains("@Id");
    }

    @Test
    @DisplayName("should render generation summary with counts")
    void shouldRenderSummary() {
        GenerationReport report = new GenerationReport(
            3, 2, 1, 0,
            List.of(
                new FileGenerationResult("dto/UserRequestDto.java",
                    GenerationStatus.CREATED, ""),
                new FileGenerationResult("dto/UserResponseDto.java",
                    GenerationStatus.CREATED, ""),
                new FileGenerationResult("mapper/UserMapper.java",
                    GenerationStatus.SKIPPED, "already exists")
            ),
            Duration.ofMillis(150)
        );

        renderer.showSummary(report, noOpSummaryCallbacks());

        assertThat(output()).contains("Generation Summary");
        assertThat(output()).contains("Total files:   3");
        assertThat(output()).contains("Created:       2");
        assertThat(output()).contains("Skipped:       1");
        assertThat(output()).contains("[OK]");
        assertThat(output()).contains("[SKIP]");
    }

    @Test
    @DisplayName("should render error screen with message")
    void shouldRenderError() {
        ErrorState state = ErrorState.ofFile("Permission denied", "/output/dto/User.java");

        renderer.showError(state, noOpErrorCallbacks());

        assertThat(output()).contains("ERROR");
        assertThat(output()).contains("Permission denied");
        assertThat(output()).contains("/output/dto/User.java");
        assertThat(output()).contains("[R] Retry");
    }

    @Test
    @DisplayName("should render progress with per-file status")
    void shouldRenderProgress() {
        GenerationProgressState state = GenerationProgressState.initial(3)
            .withFileResult(new FileGenerationResult(
                "dto/UserRequestDto.java", GenerationStatus.CREATED, ""))
            .withFileResult(new FileGenerationResult(
                "mapper/UserMapper.java", GenerationStatus.SKIPPED, "exists"));

        renderer.showProgress(state);

        assertThat(output()).contains("[OK] dto/UserRequestDto.java");
        assertThat(output()).contains("[SKIP] mapper/UserMapper.java");
    }

    @Test
    @DisplayName("should render code preview with file content")
    void shouldRenderPreview() {
        GeneratedFile file = new GeneratedFile(
            java.nio.file.Path.of("dto/UserRequestDto.java"),
            "public class UserRequestDto { }",
            Layer.DTO_REQUEST, "User");
        PreviewState state = PreviewState.initial(List.of(file));

        renderer.showPreview(state, noOpPreviewCallbacks());

        assertThat(output()).contains("Code Preview");
        assertThat(output()).contains("UserRequestDto.java");
        assertThat(output()).contains("public class UserRequestDto");
    }

    private TuiRenderer.EntitySelectionCallbacks noOpEntityCallbacks() {
        return new TuiRenderer.EntitySelectionCallbacks() {
            @Override public void onConfirm(List<EntityDescriptor> selected) {}
            @Override public void onCancel() {}
        };
    }

    private TuiRenderer.ErrorCallbacks noOpErrorCallbacks() {
        return new TuiRenderer.ErrorCallbacks() {
            @Override public void onRetry() {}
            @Override public void onSkip() {}
            @Override public void onQuit() {}
        };
    }

    private TuiRenderer.PreviewCallbacks noOpPreviewCallbacks() {
        return new TuiRenderer.PreviewCallbacks() {
            @Override public void onConfirm() {}
            @Override public void onBack() {}
        };
    }

    private TuiRenderer.SummaryCallbacks noOpSummaryCallbacks() {
        return new TuiRenderer.SummaryCallbacks() {
            @Override public void onGenerateMore() {}
            @Override public void onQuit() {}
        };
    }
}
