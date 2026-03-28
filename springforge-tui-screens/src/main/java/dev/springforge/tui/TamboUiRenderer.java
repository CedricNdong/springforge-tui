package dev.springforge.tui;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import dev.springforge.engine.model.GenerationReport;
import dev.springforge.engine.model.Layer;
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
import dev.tamboui.tui.event.TickEvent;
import dev.tamboui.widgets.tree.TreeState;

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

    // Splash screen: wait for any key before proceeding
    private volatile boolean waitingForSplashKey;

    // Tree state for preview file tree (mutable, managed by TreeWidget)
    private TreeState previewTreeState;

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

    /**
     * Blocks on the splash screen until the user presses any key.
     * Used after scan completes to let the user see the logo and results.
     */
    public void waitForKeyOnSplash() {
        this.waitingForSplashKey = true;
        blockOnScreen(ScreenType.SPLASH);
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
        this.previewTreeState = new TreeState();
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
        // Tick events → redraw during splash/progress so progress bar updates
        if (event instanceof TickEvent) {
            return currentScreen == ScreenType.SPLASH
                || currentScreen == ScreenType.PROGRESS;
        }

        if (!(event instanceof KeyEvent ke)) {
            return false;
        }

        // Ctrl+C always quits the entire TUI
        if (ke.isCtrlC()) {
            forceQuit();
            return true;
        }

        return switch (currentScreen) {
            case SPLASH -> handleSplashEvent(ke);
            case ENTITY_SELECTION -> handleEntitySelectionEvent(ke);
            case LAYER_CONFIG -> handleLayerConfigEvent(ke);
            case PREVIEW -> handlePreviewEvent(ke);
            case SUMMARY -> handleSummaryEvent(ke);
            case ERROR -> handleErrorEvent(ke);
            default -> false;
        };
    }

    // ── Splash (S1) ────────────────────────────────────────────────────
    // "Press any key to continue..." after scan completes

    private boolean handleSplashEvent(KeyEvent ke) {
        if (waitingForSplashKey) {
            waitingForSplashKey = false;
            completeScreen();
            return true;
        }
        return false;
    }

    // ── Entity Selection (S2) ────────────────────────────────────────

    private boolean handleEntitySelectionEvent(KeyEvent ke) {
        // In filter mode, typing goes to filter text
        if (filterMode) {
            return handleFilterInput(ke);
        }

        // [q] Quit
        if (ke.isCharIgnoreCase('q')) {
            entityCallbacks.onCancel();
            completeScreen();
            return true;
        }
        // [⇧Tab] or Esc → cancel / quit (S2 is first interactive screen)
        if (ke.isFocusPrevious() || ke.isCancel()) {
            entityCallbacks.onCancel();
            completeScreen();
            return true;
        }
        // [Tab] → next screen (Layer Config)
        if (ke.isFocusNext()) {
            if (entitySelectionState.hasSelection()) {
                entityCallbacks.onConfirm(entitySelectionState.selectedEntities());
                completeScreen();
            }
            return true;
        }
        // [Ctrl+G] → skip to generate (confirm directly)
        if (ke.hasCtrl() && ke.isCharIgnoreCase('g')) {
            if (entitySelectionState.hasSelection()) {
                entityCallbacks.onConfirm(entitySelectionState.selectedEntities());
                completeScreen();
            }
            return true;
        }
        // Enter is intentionally NOT mapped here — only [Tab] and [Ctrl+G]
        // advance to the next screen, as shown in the footer. This prevents
        // a double-Enter from leaking into the next screen (S3).
        // ↑/↓ Navigation
        if (ke.isUp()) {
            entitySelectionState = entitySelectionState.moveFocusUp();
            return true;
        }
        if (ke.isDown()) {
            entitySelectionState = entitySelectionState.moveFocusDown();
            return true;
        }
        // Space or Enter → toggle selection
        if (ke.isSelect() || ke.isConfirm()) {
            entitySelectionState = entitySelectionState.toggleSelected();
            return true;
        }
        // [A] Select all
        if (ke.isCharIgnoreCase('a')) {
            entitySelectionState = entitySelectionState.selectAll();
            return true;
        }
        // [N] Deselect all
        if (ke.isCharIgnoreCase('n')) {
            entitySelectionState = entitySelectionState.selectNone();
            return true;
        }
        // [?] Toggle help
        if (ke.isChar('?')) {
            entitySelectionState = entitySelectionState.toggleHelp();
            return true;
        }
        // [/] Enter filter mode
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
        // [⇧Tab] or Esc → back to entity selection
        if (ke.isFocusPrevious() || ke.isCancel()) {
            layerCallbacks.onBack();
            completeScreen();
            return true;
        }
        // [Tab] → next screen (Preview)
        if (ke.isFocusNext()) {
            layerCallbacks.onConfirm(layerConfigState);
            completeScreen();
            return true;
        }
        // [Ctrl+G] → confirm (skip preview, go to generate)
        if (ke.hasCtrl() && ke.isCharIgnoreCase('g')) {
            layerCallbacks.onConfirm(layerConfigState);
            completeScreen();
            return true;
        }
        // ↑/↓ Navigation (within active panel)
        if (ke.isUp()) {
            layerConfigState = layerConfigState.moveFocusUp();
            return true;
        }
        if (ke.isDown()) {
            layerConfigState = layerConfigState.moveFocusDown();
            return true;
        }
        // [1] Switch to Layers panel
        if (ke.isChar('1')) {
            layerConfigState = layerConfigState.withActivePanel(
                LayerConfigState.ActivePanel.LAYERS);
            return true;
        }
        // [2] Switch to Options panel
        if (ke.isChar('2')) {
            layerConfigState = layerConfigState.withActivePanel(
                LayerConfigState.ActivePanel.OPTIONS);
            return true;
        }
        // Space or Enter → toggle in active panel
        if (ke.isSelect() || ke.isConfirm()) {
            if (layerConfigState.activePanel() == LayerConfigState.ActivePanel.LAYERS) {
                int idx = layerConfigState.focusedIndex();
                Layer[] layers = Layer.values();
                if (idx < layers.length) {
                    layerConfigState = layerConfigState.toggleLayer(layers[idx]);
                }
            } else {
                layerConfigState = layerConfigState.toggleFocusedOption();
            }
            return true;
        }
        return false;
    }

    // ── Preview (S4) ─────────────────────────────────────────────────

    private boolean handlePreviewEvent(KeyEvent ke) {
        // [⇧Tab] or Esc → back to layer config
        if (ke.isFocusPrevious() || ke.isCancel()) {
            previewCallbacks.onBack();
            completeScreen();
            return true;
        }
        // [Ctrl+G] → confirm and generate
        if (ke.hasCtrl() && ke.isCharIgnoreCase('g')) {
            previewCallbacks.onConfirm();
            completeScreen();
            return true;
        }
        // Enter → confirm and generate
        if (ke.isConfirm()) {
            previewCallbacks.onConfirm();
            completeScreen();
            return true;
        }
        // ↑/↓ → navigate tree
        if (ke.isUp()) {
            previewTreeState.selectPrevious();
            syncSelectedFileFromTree();
            return true;
        }
        if (ke.isDown()) {
            previewTreeState.selectNext(previewState.files().size() + countEntities() - 1);
            syncSelectedFileFromTree();
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
        // PgUp/PgDn → scroll fast
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

    /**
     * Count unique entity names for tree node calculation.
     */
    private int countEntities() {
        return (int) previewState.files().stream()
            .map(f -> f.entityName())
            .distinct()
            .count();
    }

    /**
     * Sync selectedFileIndex from the tree state selection.
     * Tree flat entries include entity group nodes (non-leaf) and file nodes (leaf).
     * We map the tree selection index to the corresponding file index.
     */
    private void syncSelectedFileFromTree() {
        int treeIndex = previewTreeState.selected();
        // The tree is flattened as: [entity1, file1, file2, ..., entity2, file3, ...]
        // Entity nodes don't correspond to files, so we need to map.
        int fileIndex = -1;
        int flatIndex = 0;
        int entityCount = 0;
        String currentEntity = "";
        for (int i = 0; i < previewState.files().size(); i++) {
            String entity = previewState.files().get(i).entityName();
            if (!entity.equals(currentEntity)) {
                if (flatIndex == treeIndex) {
                    // Selected an entity group node — show first file of this group
                    previewState = previewState.withSelectedFileIndex(i);
                    return;
                }
                currentEntity = entity;
                flatIndex++;
                entityCount++;
            }
            if (flatIndex == treeIndex) {
                fileIndex = i;
                break;
            }
            flatIndex++;
        }
        if (fileIndex >= 0) {
            previewState = previewState.withSelectedFileIndex(fileIndex);
        }
    }

    // ── Summary (S6) ─────────────────────────────────────────────────
    // Footer: [q] Quit  [g] Generate more

    private boolean handleSummaryEvent(KeyEvent ke) {
        // [q] or Esc or Enter → quit
        if (ke.isCharIgnoreCase('q') || ke.isCancel() || ke.isConfirm()) {
            completeScreen();
            return true;
        }
        return false;
    }

    // ── Error (S8) ───────────────────────────────────────────────────
    // Footer: [R] Retry  [S] Skip  [Q] Quit

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
                    PreviewScreen.render(frame, area, previewState, previewTreeState);
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
        // Set screen to NONE immediately to prevent queued key events
        // from leaking into the next screen during transition.
        currentScreen = ScreenType.NONE;
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
