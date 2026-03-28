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
                Constraint.fill(),
                Constraint.length(3)
            )
            .split(area);

        renderHeader(frame, layout.get(0), state);
        renderProgressBar(frame, layout.get(1), state);
        renderCurrentFile(frame, layout.get(2), state);
        renderFileLog(frame, layout.get(3), state);
        renderFooter(frame, layout.get(4), state);
    }

    private static void renderHeader(Frame frame, Rect area,
            GenerationProgressState state) {
        String statusIcon;
        String statusText;
        switch (state.overallStatus()) {
            case IN_PROGRESS -> { statusIcon = "\u2699"; statusText = "Generating..."; }
            case DONE -> { statusIcon = "\u2705"; statusText = "Complete!"; }
            case ERROR -> { statusIcon = "\u26A0"; statusText = "Completed with errors"; }
            default -> { statusIcon = "\u2699"; statusText = "Generating..."; }
        }

        Block header = Block.builder()
            .borders(Borders.ALL)
            .borderType(BorderType.ROUNDED)
            .borderStyle(Style.EMPTY.fg(Color.CYAN))
            .title(Title.from(Line.from(
                Span.raw(" " + statusIcon + " SpringForge ").bold().cyan(),
                Span.raw("\u2014 " + statusText + " ").white()
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
                .title(Title.from(Line.from(
                    Span.raw(" \uD83D\uDCCA Progress ").bold()
                )))
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
                Span.raw("  \uD83D\uDD27 Current: ").bold(),
                Span.raw(state.currentFile()).cyan()
            );
        } else if (state.overallStatus() == GenerationProgressState.OverallStatus.DONE) {
            line = Line.from(
                Span.raw("  \u2705 All files generated successfully").green()
            );
        } else if (state.overallStatus() == GenerationProgressState.OverallStatus.ERROR) {
            line = Line.from(
                Span.raw("  \u26A0 Generation completed with errors").red()
            );
        } else {
            line = Line.from(Span.raw("  \u23F3 Waiting...").dim());
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

        // Calculate visible window based on area height (minus 2 for borders)
        int viewportHeight = area.height() - 2;
        List<FileGenerationResult> logEntries = state.log();
        int startIdx = Math.max(0,
            Math.min(state.logScrollOffset(), logEntries.size() - viewportHeight));
        int endIdx = Math.min(logEntries.size(), startIdx + viewportHeight);

        for (int i = startIdx; i < endIdx; i++) {
            FileGenerationResult result = logEntries.get(i);
            Span icon = switch (result.status()) {
                case CREATED -> Span.raw("  \u2705 ").green();
                case SKIPPED -> Span.raw("  \u23ED ").yellow();
                case ERROR -> Span.raw("  \u274C ").red();
            };

            Span path = Span.raw(result.filePath()).white();
            List<Span> spans = new ArrayList<>();
            spans.add(icon);
            spans.add(path);

            if (result.message() != null && !result.message().isEmpty()) {
                spans.add(Span.raw(" \u2014 " + result.message()).dim());
            }
            lines.add(Line.from(spans.toArray(Span[]::new)));
        }

        if (lines.isEmpty()) {
            lines.add(Line.from(Span.raw("  \u23F3 No files processed yet").dim()));
        }

        Paragraph log = Paragraph.builder()
            .text(Text.from(lines.toArray(Line[]::new)))
            .block(Block.builder()
                .borders(Borders.ALL)
                .borderType(BorderType.ROUNDED)
                .borderStyle(Style.EMPTY.fg(Color.GREEN))
                .title(Title.from(Line.from(
                    Span.raw(" \uD83D\uDCDD Generation Log ").bold()
                )))
                .titleBottom(Title.from(Line.from(
                    Span.raw(" \uD83D\uDCE6 ").dim(),
                    Span.raw(String.valueOf(state.completedFiles())).green(),
                    Span.raw(" created ").dim(),
                    Span.raw("| \u23ED ").dim(),
                    Span.raw(String.valueOf(state.skippedFiles())).yellow(),
                    Span.raw(" skipped ").dim(),
                    Span.raw("| \u274C ").dim(),
                    Span.raw(String.valueOf(state.errorFiles())).red(),
                    Span.raw(" errors ").dim()
                )))
                .build())
            .build();

        frame.renderWidget(log, area);
    }

    private static void renderFooter(Frame frame, Rect area,
            GenerationProgressState state) {
        Line footer;
        if (state.overallStatus() == GenerationProgressState.OverallStatus.DONE) {
            footer = Line.from(
                Span.raw(" [Enter]").bold().yellow(),
                Span.raw(" View Summary ").dim(),
                Span.raw("[q]").bold().yellow(),
                Span.raw(" Quit \u274C").dim()
            );
        } else if (state.overallStatus() == GenerationProgressState.OverallStatus.ERROR) {
            footer = Line.from(
                Span.raw(" [Enter]").bold().yellow(),
                Span.raw(" View Summary ").dim(),
                Span.raw("[q]").bold().yellow(),
                Span.raw(" Quit \u274C").dim()
            );
        } else {
            footer = Line.from(
                Span.raw(" [\u2191\u2193]").bold().yellow(),
                Span.raw(" Scroll Log ").dim(),
                Span.raw("\u23F3 Generation in progress...").dim()
            );
        }

        Paragraph footerWidget = Paragraph.builder()
            .text(Text.from(footer))
            .block(Block.builder()
                .borders(Borders.ALL)
                .borderType(BorderType.ROUNDED)
                .borderStyle(Style.EMPTY.fg(Color.DARK_GRAY))
                .build())
            .build();

        frame.renderWidget(footerWidget, area);
    }
}
