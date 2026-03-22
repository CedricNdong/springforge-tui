package dev.springforge.tui;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import dev.springforge.engine.model.GenerationReport;
import dev.springforge.engine.model.Layer;
import dev.springforge.engine.model.ConflictStrategy;
import dev.springforge.engine.model.MapperLib;
import dev.springforge.engine.model.SpringVersion;
import dev.springforge.tui.screens.EntitySelectionScreen;
import dev.springforge.tui.screens.ErrorScreen;
import dev.springforge.tui.screens.LayerConfigScreen;
import dev.springforge.tui.screens.PreviewScreen;
import dev.springforge.tui.screens.ProgressScreen;
import dev.springforge.tui.screens.SplashScreen;
import dev.springforge.tui.screens.SummaryScreen;
import dev.springforge.tui.state.EntitySelectionState;
import dev.springforge.tui.state.ErrorState;
import dev.springforge.tui.state.GenerationProgressState;
import dev.springforge.tui.state.LayerConfigState;
import dev.springforge.tui.state.PreviewState;
import dev.springforge.tui.state.SplashState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.TuiRunner;
import dev.tamboui.tui.event.Event;
import dev.tamboui.tui.event.KeyEvent;

/**
 * Full interactive TUI implementation using TamboUI.
 *
 * <p>Runs a TuiRunner event loop on a background thread. Blocking
 * {@code show*()} methods set the active screen and wait until the
 * user completes it (via Enter, Escape, etc.). Non-blocking methods
 * (splash, progress) update the display and return immediately.
 */
public class TamboUiRenderer implements TuiRenderer, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(TamboUiRenderer.class);

    private final TuiRunner runner;
    private final Thread tuiThread;
    private volatile boolean closed;

    // Current screen
    private volatile ScreenType currentScreen = ScreenType.NONE;

    // Screen states — volatile for cross-thread visibility
    private volatile SplashState splashState;
    private volatile EntitySelectionState entitySelectionState;
    private volatile LayerConfigState layerConfigState;
    private volatile PreviewState previewState;
    private volatile GenerationProgressState progressState;
    private volatile GenerationReport summaryReport;
    private volatile ErrorState errorState;

    // Callbacks for interactive screens
    private volatile EntitySelectionCallbacks entityCallbacks;
    private volatile LayerConfigCallbacks layerCallbacks;
    private volatile PreviewCallbacks previewCallbacks;
    private volatile ErrorCallbacks errorCallbacks;

    // Filter mode for entity selection: only accept typing when active
    private volatile boolean filterMode;

    // Latch to block show*() callers until the screen is completed
    private final AtomicReference<CountDownLatch> screenLatch = new AtomicReference<>();

    enum ScreenType {
        NONE, SPLASH, ENTITY_SELECTION, LAYER_CONFIG, PREVIEW,
        PROGRESS, SUMMARY, ERROR
    }

    /**
     * Creates a TamboUiRenderer with default TUI configuration.
     */
    public TamboUiRenderer() {
        this(createDefaultRunner());
    }

    private static TuiRunner createDefaultRunner() {
        try {
            return TuiRunner.create();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create TuiRunner", e);
        }
    }

    /**
     * Creates a new TamboUiRenderer with the given runner and starts the event loop.
     */
    TamboUiRenderer(TuiRunner runner) {
        this.runner = runner;

        this.tuiThread = new Thread(() -> {
            try {
                runner.run(this::handleEvent, this::render);
            } catch (Exception e) {
                if (!closed) {
                    LOG.error("TUI event loop error: {}", e.getMessage(), e);
                }
            }
        }, "springforge-tui");
        tuiThread.setDaemon(true);
        tuiThread.start();
    }

    // ── Non-blocking screens ─────────────────────────────────────────

    @Override
    public void showSplash(SplashState state) {
        this.splashState = state;
        this.currentScreen = ScreenType.SPLASH;
    }

    @Override
    public void showProgress(GenerationProgressState state) {
        this.progressState = state;
        this.currentScreen = ScreenType.PROGRESS;
    }

    // ── Blocking screens ─────────────────────────────────────────────

    @Override
    public void showEntitySelection(EntitySelectionState state,
            EntitySelectionCallbacks callbacks) {
        this.entitySelectionState = state;
        this.entityCallbacks = callbacks;
        this.filterMode = false;
        blockOnScreen(ScreenType.ENTITY_SELECTION);
    }

    @Override
    public void showLayerConfig(LayerConfigState state,
            LayerConfigCallbacks callbacks) {
        this.layerConfigState = state;
        this.layerCallbacks = callbacks;
        blockOnScreen(ScreenType.LAYER_CONFIG);
    }

    @Override
    public void showPreview(PreviewState state, PreviewCallbacks callbacks) {
        this.previewState = state;
        this.previewCallbacks = callbacks;
        blockOnScreen(ScreenType.PREVIEW);
    }

    @Override
    public void showSummary(GenerationReport report) {
        this.summaryReport = report;
        blockOnScreen(ScreenType.SUMMARY);
    }

    @Override
    public void showError(ErrorState state, ErrorCallbacks callbacks) {
        this.errorState = state;
        this.errorCallbacks = callbacks;
        blockOnScreen(ScreenType.ERROR);
    }

    // ── Event handling ───────────────────────────────────────────────

    private boolean handleEvent(Event event, TuiRunner runner) {
        if (!(event instanceof KeyEvent ke)) {
            return false;
        }

        // Ctrl+C always quits the entire TUI
        if (ke.isCtrlC()) {
            forceQuit();
            return true;
        }

        return switch (currentScreen) {
            case ENTITY_SELECTION -> handleEntitySelectionEvent(ke);
            case LAYER_CONFIG -> handleLayerConfigEvent(ke);
            case PREVIEW -> handlePreviewEvent(ke);
            case SUMMARY -> handleSummaryEvent(ke);
            case ERROR -> handleErrorEvent(ke);
            default -> false;
        };
    }

    // ── Entity Selection (S2) ────────────────────────────────────────

    private boolean handleEntitySelectionEvent(KeyEvent ke) {
        // In filter mode, typing goes to filter text
        if (filterMode) {
            return handleFilterInput(ke);
        }

        // Escape → cancel
        if (ke.isCancel()) {
            entityCallbacks.onCancel();
            completeScreen();
            return true;
        }
        // Enter → confirm selection
        if (ke.isConfirm()) {
            if (entitySelectionState.hasSelection()) {
                entityCallbacks.onConfirm(entitySelectionState.selectedEntities());
                completeScreen();
            }
            return true;
        }
        // Navigation
        if (ke.isUp()) {
            entitySelectionState = entitySelectionState.moveFocusUp();
            return true;
        }
        if (ke.isDown()) {
            entitySelectionState = entitySelectionState.moveFocusDown();
            return true;
        }
        // Space → toggle selection
        if (ke.isSelect()) {
            entitySelectionState = entitySelectionState.toggleSelected();
            return true;
        }
        // Shortcuts
        if (ke.isCharIgnoreCase('a')) {
            entitySelectionState = entitySelectionState.selectAll();
            return true;
        }
        if (ke.isCharIgnoreCase('n')) {
            entitySelectionState = entitySelectionState.selectNone();
            return true;
        }
        if (ke.isChar('?')) {
            entitySelectionState = entitySelectionState.toggleHelp();
            return true;
        }
        // / → enter filter mode
        if (ke.isChar('/')) {
            filterMode = true;
            entitySelectionState = entitySelectionState.withFilter("");
            return true;
        }
        return false;
    }

    private boolean handleFilterInput(KeyEvent ke) {
        // Escape or Enter → exit filter mode
        if (ke.isCancel() || ke.isConfirm()) {
            filterMode = false;
            return true;
        }
        // Backspace
        if (ke.isDeleteBackward()) {
            String filter = entitySelectionState.filterText();
            if (filter.isEmpty()) {
                filterMode = false;
            } else {
                entitySelectionState = entitySelectionState
                    .withFilter(filter.substring(0, filter.length() - 1));
            }
            return true;
        }
        // Type character into filter
        char ch = ke.character();
        if (ch != 0 && (Character.isLetterOrDigit(ch) || ch == '.' || ch == '_')) {
            entitySelectionState = entitySelectionState
                .withFilter(entitySelectionState.filterText() + ch);
            return true;
        }
        return false;
    }

    // ── Layer Config (S3) ────────────────────────────────────────────

    private boolean handleLayerConfigEvent(KeyEvent ke) {
        // Escape → back to entity selection
        if (ke.isCancel()) {
            layerCallbacks.onBack();
            completeScreen();
            return true;
        }
        // Enter → confirm config
        if (ke.isConfirm()) {
            layerCallbacks.onConfirm(layerConfigState);
            completeScreen();
            return true;
        }
        // Navigation
        if (ke.isUp()) {
            layerConfigState = layerConfigState.moveFocusUp();
            return true;
        }
        if (ke.isDown()) {
            layerConfigState = layerConfigState.moveFocusDown();
            return true;
        }
        // Space → toggle focused layer
        if (ke.isSelect()) {
            int idx = layerConfigState.focusedIndex();
            Layer[] layers = Layer.values();
            if (idx < layers.length) {
                layerConfigState = layerConfigState.toggleLayer(layers[idx]);
            }
            return true;
        }
        // Option shortcuts
        if (ke.isChar('1')) {
            layerConfigState = layerConfigState.withSpringVersion(SpringVersion.V3);
            return true;
        }
        if (ke.isChar('2')) {
            layerConfigState = layerConfigState.withSpringVersion(SpringVersion.V2);
            return true;
        }
        if (ke.isCharIgnoreCase('m')) {
            MapperLib current = layerConfigState.mapperLib();
            MapperLib next = current == MapperLib.MAPSTRUCT
                ? MapperLib.MODEL_MAPPER : MapperLib.MAPSTRUCT;
            layerConfigState = layerConfigState.withMapperLib(next);
            return true;
        }
        if (ke.isCharIgnoreCase('o')) {
            ConflictStrategy current = layerConfigState.conflictStrategy();
            ConflictStrategy next = current == ConflictStrategy.SKIP
                ? ConflictStrategy.OVERWRITE : ConflictStrategy.SKIP;
            layerConfigState = layerConfigState.withConflictStrategy(next);
            return true;
        }
        return false;
    }

    // ── Preview (S4) ─────────────────────────────────────────────────

    private boolean handlePreviewEvent(KeyEvent ke) {
        // Escape → back to layer config
        if (ke.isCancel()) {
            previewCallbacks.onBack();
            completeScreen();
            return true;
        }
        // Enter → confirm and generate
        if (ke.isConfirm()) {
            previewCallbacks.onConfirm();
            completeScreen();
            return true;
        }
        // Arrow Up/Down → select file
        if (ke.isUp()) {
            previewState = previewState.selectPrevious();
            return true;
        }
        if (ke.isDown()) {
            previewState = previewState.selectNext();
            return true;
        }
        // j/k → scroll code preview (vim-style)
        if (ke.isCharIgnoreCase('j')) {
            previewState = previewState.scrollDown();
            return true;
        }
        if (ke.isCharIgnoreCase('k')) {
            previewState = previewState.scrollUp();
            return true;
        }
        // Page Up/Down → scroll fast
        if (ke.isPageDown()) {
            for (int i = 0; i < 10; i++) {
                previewState = previewState.scrollDown();
            }
            return true;
        }
        if (ke.isPageUp()) {
            for (int i = 0; i < 10; i++) {
                previewState = previewState.scrollUp();
            }
            return true;
        }
        return false;
    }

    // ── Summary (S6) ─────────────────────────────────────────────────

    private boolean handleSummaryEvent(KeyEvent ke) {
        // Any confirm/cancel/q exits the summary
        if (ke.isConfirm() || ke.isCancel() || ke.isCharIgnoreCase('q')) {
            completeScreen();
            return true;
        }
        return false;
    }

    // ── Error (S8) ───────────────────────────────────────────────────

    private boolean handleErrorEvent(KeyEvent ke) {
        if (ke.isCancel() || ke.isCharIgnoreCase('q')) {
            errorCallbacks.onQuit();
            completeScreen();
            return true;
        }
        if (errorState.canRetry() && ke.isCharIgnoreCase('r')) {
            errorCallbacks.onRetry();
            completeScreen();
            return true;
        }
        if (errorState.canRetry() && ke.isCharIgnoreCase('s')) {
            errorCallbacks.onSkip();
            completeScreen();
            return true;
        }
        return false;
    }

    // ── Rendering ────────────────────────────────────────────────────

    private void render(Frame frame) {
        Rect area = frame.area();

        switch (currentScreen) {
            case SPLASH -> {
                if (splashState != null) {
                    SplashScreen.render(frame, area, splashState);
                }
            }
            case ENTITY_SELECTION -> {
                if (entitySelectionState != null) {
                    EntitySelectionScreen.render(frame, area, entitySelectionState);
                }
            }
            case LAYER_CONFIG -> {
                if (layerConfigState != null) {
                    LayerConfigScreen.render(frame, area, layerConfigState);
                }
            }
            case PREVIEW -> {
                if (previewState != null) {
                    PreviewScreen.render(frame, area, previewState);
                }
            }
            case PROGRESS -> {
                if (progressState != null) {
                    ProgressScreen.render(frame, area, progressState);
                }
            }
            case SUMMARY -> {
                if (summaryReport != null) {
                    SummaryScreen.render(frame, area, summaryReport);
                }
            }
            case ERROR -> {
                if (errorState != null) {
                    ErrorScreen.render(frame, area, errorState);
                }
            }
            default -> { /* NONE — blank screen */ }
        }
    }

    // ── Internal helpers ─────────────────────────────────────────────

    private void blockOnScreen(ScreenType screen) {
        CountDownLatch latch = new CountDownLatch(1);
        screenLatch.set(latch);
        // Set screen type AFTER latch so render thread sees both atomically
        currentScreen = screen;

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void completeScreen() {
        CountDownLatch latch = screenLatch.get();
        if (latch != null) {
            latch.countDown();
        }
    }

    /**
     * Force-quit the entire TUI (Ctrl+C). Releases any blocking screen
     * and signals the runner to exit.
     */
    private void forceQuit() {
        closed = true;
        // Fire cancel callback for the current interactive screen
        switch (currentScreen) {
            case ENTITY_SELECTION -> {
                if (entityCallbacks != null) entityCallbacks.onCancel();
            }
            case LAYER_CONFIG -> {
                if (layerCallbacks != null) layerCallbacks.onBack();
            }
            case PREVIEW -> {
                if (previewCallbacks != null) previewCallbacks.onBack();
            }
            case ERROR -> {
                if (errorCallbacks != null) errorCallbacks.onQuit();
            }
            default -> { /* no callback needed */ }
        }
        completeScreen();
        runner.quit();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        currentScreen = ScreenType.NONE;
        completeScreen();

        if (runner != null) {
            runner.quit();
        }
        if (tuiThread != null) {
            try {
                tuiThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
