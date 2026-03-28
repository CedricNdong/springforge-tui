package dev.springforge.tui;

import dev.springforge.engine.model.GenerationReport;
import dev.springforge.tui.state.EntitySelectionState;
import dev.springforge.tui.state.ErrorState;
import dev.springforge.tui.state.GenerationProgressState;
import dev.springforge.tui.state.LayerConfigState;
import dev.springforge.tui.state.PreviewState;
import dev.springforge.tui.state.SplashState;

/**
 * Abstraction layer between SpringForge business logic and the TUI framework.
 * All TUI calls MUST go through this interface — never call TamboUI directly
 * from engine or CLI code.
 *
 * <p>Two implementations:
 * <ul>
 *   <li>{@code TamboUiRenderer} — full interactive TUI via TamboUI (default)</li>
 *   <li>{@code PlainCliRenderer} — stdout-only fallback (dumb terminal / --no-tui)</li>
 * </ul>
 */
public interface TuiRenderer {

    void showSplash(SplashState state);

    void showEntitySelection(EntitySelectionState state,
        EntitySelectionCallbacks callbacks);

    void showLayerConfig(LayerConfigState state,
        LayerConfigCallbacks callbacks);

    void showPreview(PreviewState state,
        PreviewCallbacks callbacks);

    void showProgress(GenerationProgressState state);

    void showSummary(GenerationReport report, SummaryCallbacks callbacks);

    void showError(ErrorState state, ErrorCallbacks callbacks);

    interface EntitySelectionCallbacks {
        void onConfirm(java.util.List<dev.springforge.engine.model.EntityDescriptor> selected);
        void onCancel();
    }

    interface LayerConfigCallbacks {
        void onConfirm(LayerConfigState config);
        void onBack();
    }

    interface PreviewCallbacks {
        void onConfirm();
        void onBack();
    }

    interface SummaryCallbacks {
        void onGenerateMore();
        void onQuit();
    }

    interface ErrorCallbacks {
        void onRetry();
        void onSkip();
        void onQuit();
    }
}
