package dev.springforge.tui.screens;

import java.util.ArrayList;
import java.util.List;

import dev.springforge.engine.model.FileGenerationResult;
import dev.springforge.tui.state.GenerationProgressState;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.gauge.Gauge;
import dev.tamboui.widgets.paragraph.Paragraph;

/**
 * S5 — Generation Progress screen.
 * Overall progress bar and per-file status log.
 */
public final class ProgressScreen {

    private ProgressScreen() {}

    public static void render(Frame frame, Rect area, GenerationProgressState state) {
        List<Rect> layout = Layout.vertical()
            .constraints(
                Constraint.length(3),
                Constraint.length(3),
                Constraint.length(3),
                Constraint.fill()
            )
            .split(area);

        renderHeader(frame, layout.get(0), state);
        renderProgressBar(frame, layout.get(1), state);
        renderCurrentFile(frame, layout.get(2), state);
        renderFileLog(frame, layout.get(3), state);
    }

    private static void renderHeader(Frame frame, Rect area,
            GenerationProgressState state) {
        String status = switch (state.overallStatus()) {
            case IN_PROGRESS -> "Generating...";
            case DONE -> "Complete!";
            case ERROR -> "Completed with errors";
        };

        Block header = Block.builder()
            .borders(Borders.ALL)
            .borderType(BorderType.ROUNDED)
            .borderStyle(Style.EMPTY.fg(Color.CYAN))
            .title(Title.from(Line.from(
                Span.raw(" SpringForge ").bold().cyan(),
                Span.raw("— " + status + " ").white()
            )))
            .build();

        frame.renderWidget(header, area);
    }

    private static void renderProgressBar(Frame frame, Rect area,
            GenerationProgressState state) {
        int done = state.completedFiles() + state.skippedFiles() + state.errorFiles();
        double ratio = state.totalFiles() > 0
            ? (double) done / state.totalFiles()
            : 0.0;

        String label = String.format("%d%%  (%d/%d files)",
            state.progressPercent(), done, state.totalFiles());

        Gauge gauge = Gauge.builder()
            .block(Block.builder()
                .borders(Borders.ALL)
                .borderType(BorderType.ROUNDED)
                .borderStyle(Style.EMPTY.fg(Color.GREEN))
                .build())
            .gaugeStyle(Style.EMPTY.fg(Color.GREEN))
            .ratio(ratio)
            .label(label)
            .build();

        frame.renderWidget(gauge, area);
    }

    private static void renderCurrentFile(Frame frame, Rect area,
            GenerationProgressState state) {
        Line line;
        if (state.overallStatus() == GenerationProgressState.OverallStatus.IN_PROGRESS
                && !state.currentFile().isEmpty()) {
            line = Line.from(
                Span.raw("  Current: ").bold(),
                Span.raw(state.currentFile()).cyan()
            );
        } else if (state.overallStatus() == GenerationProgressState.OverallStatus.DONE) {
            line = Line.from(
                Span.raw("  All files generated successfully").green()
            );
        } else {
            line = Line.from(Span.raw("  Waiting...").dim());
        }

        Paragraph current = Paragraph.builder()
            .text(Text.from(line))
            .block(Block.builder()
                .borders(Borders.ALL)
                .borderType(BorderType.ROUNDED)
                .borderStyle(Style.EMPTY.fg(Color.DARK_GRAY))
                .build())
            .build();

        frame.renderWidget(current, area);
    }

    private static void renderFileLog(Frame frame, Rect area,
            GenerationProgressState state) {
        List<Line> lines = new ArrayList<>();

        for (FileGenerationResult result : state.log()) {
            Span icon = switch (result.status()) {
                case CREATED -> Span.raw("  [OK]   ").green();
                case SKIPPED -> Span.raw("  [SKIP] ").yellow();
                case ERROR -> Span.raw("  [ERR]  ").red();
            };

            Span path = Span.raw(result.filePath()).white();
            List<Span> spans = new ArrayList<>();
            spans.add(icon);
            spans.add(path);

            if (result.message() != null && !result.message().isEmpty()) {
                spans.add(Span.raw(" — " + result.message()).dim());
            }
            lines.add(Line.from(spans.toArray(Span[]::new)));
        }

        if (lines.isEmpty()) {
            lines.add(Line.from(Span.raw("  No files processed yet").dim()));
        }

        Paragraph log = Paragraph.builder()
            .text(Text.from(lines.toArray(Line[]::new)))
            .block(Block.builder()
                .borders(Borders.ALL)
                .borderType(BorderType.ROUNDED)
                .borderStyle(Style.EMPTY.fg(Color.GREEN))
                .title("Generation Log")
                .build())
            .build();

        frame.renderWidget(log, area);
    }
}
