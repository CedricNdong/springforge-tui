package dev.springforge.tui.screens;

import java.util.ArrayList;
import java.util.List;

import dev.springforge.engine.model.FileGenerationResult;
import dev.springforge.engine.model.GenerationReport;
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
import dev.tamboui.widgets.paragraph.Paragraph;

/**
 * S6 — Summary screen.
 * Displays file count summary and output directory tree.
 */
public final class SummaryScreen {

    private SummaryScreen() {}

    public static void render(Frame frame, Rect area, GenerationReport report) {
        List<Rect> layout = Layout.vertical()
            .constraints(
                Constraint.length(3),
                Constraint.length(8),
                Constraint.fill(),
                Constraint.length(3)
            )
            .split(area);

        renderHeader(frame, layout.get(0));
        renderStats(frame, layout.get(1), report);
        renderFileTree(frame, layout.get(2), report);
        renderFooter(frame, layout.get(3));
    }

    private static void renderHeader(Frame frame, Rect area) {
        Block header = Block.builder()
            .borders(Borders.ALL)
            .borderType(BorderType.ROUNDED)
            .borderStyle(Style.EMPTY.fg(Color.GREEN))
            .title(Title.from(Line.from(
                Span.raw(" SpringForge ").bold().cyan(),
                Span.raw("— Generation Complete ").bold().green()
            )))
            .build();

        frame.renderWidget(header, area);
    }

    private static void renderStats(Frame frame, Rect area, GenerationReport report) {
        Text stats = Text.from(
            Line.from(Span.raw("")),
            Line.from(
                Span.raw("  Total files:   ").bold(),
                Span.raw(String.valueOf(report.totalFiles())).cyan()
            ),
            Line.from(
                Span.raw("  Created:       ").bold(),
                Span.raw(String.valueOf(report.createdFiles())).green()
            ),
            Line.from(
                Span.raw("  Skipped:       ").bold(),
                Span.raw(String.valueOf(report.skippedFiles())).yellow()
            ),
            Line.from(
                Span.raw("  Errors:        ").bold(),
                report.errorFiles() > 0
                    ? Span.raw(String.valueOf(report.errorFiles())).red()
                    : Span.raw(String.valueOf(report.errorFiles())).green()
            ),
            Line.from(
                Span.raw("  Duration:      ").bold(),
                Span.raw(report.duration().toMillis() + "ms").dim()
            )
        );

        Paragraph statsWidget = Paragraph.builder()
            .text(stats)
            .block(Block.builder()
                .borders(Borders.ALL)
                .borderType(BorderType.ROUNDED)
                .borderStyle(Style.EMPTY.fg(Color.CYAN))
                .title("Summary")
                .build())
            .build();

        frame.renderWidget(statsWidget, area);
    }

    private static void renderFileTree(Frame frame, Rect area, GenerationReport report) {
        List<Line> lines = new ArrayList<>();

        for (FileGenerationResult result : report.results()) {
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

        Paragraph tree = Paragraph.builder()
            .text(Text.from(lines.toArray(Line[]::new)))
            .block(Block.builder()
                .borders(Borders.ALL)
                .borderType(BorderType.ROUNDED)
                .borderStyle(Style.EMPTY.fg(Color.GREEN))
                .title("Output Files")
                .build())
            .build();

        frame.renderWidget(tree, area);
    }

    private static void renderFooter(Frame frame, Rect area) {
        Line footer = Line.from(
            Span.raw("[q]").bold().yellow(),
            Span.raw(" Quit  ").dim(),
            Span.raw("[g]").bold().yellow(),
            Span.raw(" Generate more").dim()
        );

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
