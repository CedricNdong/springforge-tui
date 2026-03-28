package dev.springforge.tui;

import java.io.PrintStream;
import java.util.List;

import dev.springforge.engine.model.EntityDescriptor;
import dev.springforge.engine.model.FieldDescriptor;
import dev.springforge.engine.model.FileGenerationResult;
import dev.springforge.engine.model.GeneratedFile;
import dev.springforge.engine.model.GenerationReport;
import dev.springforge.tui.state.EntitySelectionState;
import dev.springforge.tui.state.ErrorState;
import dev.springforge.tui.state.GenerationProgressState;
import dev.springforge.tui.state.LayerConfigState;
import dev.springforge.tui.state.PreviewState;
import dev.springforge.tui.state.SplashState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plain stdout fallback implementation of TuiRenderer.
 * Used when --no-tui is specified or a dumb terminal is detected.
 * No interactive input — outputs formatted text to stdout.
 */
public class PlainCliRenderer implements TuiRenderer {

    private static final Logger LOG = LoggerFactory.getLogger(PlainCliRenderer.class);

    private final PrintStream out;

    public PlainCliRenderer() {
        this(System.out);
    }

    public PlainCliRenderer(PrintStream out) {
        this.out = out;
    }

    @Override
    public void showSplash(SplashState state) {
        out.println("SpringForge TUI");
        out.println("================");
        if (state.scanComplete()) {
            out.println("Scan complete: " + state.totalFiles() + " entity files found");
        } else if (state.errorMessage() != null) {
            out.println("Scan error: " + state.errorMessage());
        } else {
            out.println("Scanning... " + state.scannedFiles() + " files");
        }
        out.println();
    }

    @Override
    public void showEntitySelection(EntitySelectionState state,
            EntitySelectionCallbacks callbacks) {
        out.println("=== Entity Selection ===");
        out.println();
        List<EntityDescriptor> entities = state.entities();
        for (int i = 0; i < entities.size(); i++) {
            EntityDescriptor entity = entities.get(i);
            boolean selected = state.selectedEntityNames().contains(entity.className());
            out.printf("  [%s] %s (%s)%n",
                selected ? "x" : " ",
                entity.className(),
                entity.packageName());
        }
        out.println();
        out.println("Selected: " + state.selectedEntityNames().size() + " entities");
        out.println();

        if (state.hasSelection()) {
            EntityDescriptor focused = state.focusedEntity();
            if (focused != null) {
                printEntityDetails(focused);
            }
        }
    }

    @Override
    public void showLayerConfig(LayerConfigState state,
            LayerConfigCallbacks callbacks) {
        out.println("=== Layer Configuration ===");
        out.println();
        out.println("Selected layers: " + state.selectedLayers());
        out.println("Spring version: " + state.springVersion());
        out.println("Mapper: " + state.mapperLib());
        out.println("Conflict strategy: " + state.conflictStrategy());
        out.println("Estimated files: " + state.estimatedFileCount());
        out.println();
    }

    @Override
    public void showPreview(PreviewState state, PreviewCallbacks callbacks) {
        out.println("=== Code Preview ===");
        out.println();
        List<GeneratedFile> files = state.files();
        for (int i = 0; i < files.size(); i++) {
            GeneratedFile file = files.get(i);
            String marker = (i == state.selectedFileIndex()) ? " >> " : "    ";
            out.println(marker + file.outputPath());
        }
        out.println();

        GeneratedFile selected = state.selectedFile();
        if (selected != null) {
            out.println("--- " + selected.outputPath() + " ---");
            out.println(selected.content());
            out.println("--- end ---");
        }
    }

    @Override
    public void showProgress(GenerationProgressState state) {
        int percent = state.progressPercent();
        out.printf("Generation progress: %d%% (%d/%d files)%n",
            percent,
            state.completedFiles() + state.skippedFiles() + state.errorFiles(),
            state.totalFiles());

        for (FileGenerationResult result : state.log()) {
            String icon = switch (result.status()) {
                case CREATED -> "[OK]";
                case SKIPPED -> "[SKIP]";
                case ERROR -> "[ERR]";
            };
            out.printf("  %s %s%n", icon, result.filePath());
        }

        if (state.overallStatus() == GenerationProgressState.OverallStatus.DONE) {
            out.println("Generation complete.");
        }
    }

    @Override
    public void showSummary(GenerationReport report, SummaryCallbacks callbacks) {
        out.println("=== Generation Summary ===");
        out.println();
        out.println("Total files:   " + report.totalFiles());
        out.println("Created:       " + report.createdFiles());
        out.println("Skipped:       " + report.skippedFiles());
        out.println("Errors:        " + report.errorFiles());
        out.println("Duration:      " + report.duration().toMillis() + "ms");
        out.println();

        for (FileGenerationResult result : report.results()) {
            String icon = switch (result.status()) {
                case CREATED -> "[OK]";
                case SKIPPED -> "[SKIP]";
                case ERROR -> "[ERR]";
            };
            out.printf("  %s %s", icon, result.filePath());
            if (result.message() != null && !result.message().isEmpty()) {
                out.printf(" — %s", result.message());
            }
            out.println();
        }
    }

    @Override
    public void showError(ErrorState state, ErrorCallbacks callbacks) {
        out.println("=== ERROR ===");
        out.println();
        out.println("Error: " + state.errorMessage());
        if (state.filePath() != null) {
            out.println("File: " + state.filePath());
        }
        if (state.canRetry()) {
            out.println("Options: [R] Retry  [S] Skip  [Q] Quit");
        }
    }

    private void printEntityDetails(EntityDescriptor entity) {
        out.println("--- " + entity.className() + " ---");
        out.println("Package: " + entity.packageName());
        out.println("Namespace: " + entity.namespace());
        out.println("Lombok: " + entity.hasLombok());
        out.println("Fields:");
        for (FieldDescriptor field : entity.fields()) {
            String extra = "";
            if (field.isId()) extra += " @Id";
            if (field.isNullable()) extra += " nullable";
            if (field.isUnique()) extra += " unique";
            out.printf("  - %s %s%s%n", field.type(), field.name(), extra);
        }
        out.println();
    }

    /**
     * Detects if the current terminal is a dumb terminal that cannot
     * support interactive TUI rendering.
     */
    public static boolean isDumbTerminal() {
        String term = System.getenv("TERM");
        return term == null
            || term.equals("dumb")
            || System.console() == null;
    }
}
