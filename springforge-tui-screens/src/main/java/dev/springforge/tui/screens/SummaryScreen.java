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
 * Displays file count summary and scrollable output file list.
 */
public final class SummaryScreen {

    private SummaryScreen() {}

    public static void render(Frame frame, Rect area, GenerationReport report,
            int scrollOffset) {
        List<Rect> layout = Layout.vertical()
            .constraints(
                Constraint.length(3),
                Constraint.length(8),
                Constraint.fill(),
                Constraint.length(3)
            )
            .split(area);

        renderHeader(frame, layout.get(0), report);
        renderStats(frame, layout.get(1), report);
        renderFileList(frame, layout.get(2), report, scrollOffset);
        renderFooter(frame, layout.get(3));
    }

    private static void renderHeader(Frame frame, Rect area, GenerationReport report) {
        boolean hasErrors = report.errorFiles() > 0;

        Block header = Block.builder()
            .borders(Borders.ALL)
            .borderType(BorderType.ROUNDED)
            .borderStyle(Style.EMPTY.fg(hasErrors ? Color.YELLOW : Color.GREEN))
            .title(Title.from(Line.from(
                Span.raw(" \uD83C\uDF89 SpringForge ").bold().cyan(),
                hasErrors
                    ? Span.raw("\u2014 Generation Complete (with warnings) ").bold().yellow()
                    : Span.raw("\u2014 Generation Complete! ").bold().green()
            )))
            .build();

        frame.renderWidget(header, area);
    }

    private static void renderStats(Frame frame, Rect area, GenerationReport report) {
        Text stats = Text.from(
            Line.from(Span.raw("")),
            Line.from(
                Span.raw("  \uD83D\uDCCA Total files:   ").bold(),
                Span.raw(String.valueOf(report.totalFiles())).cyan()
            ),
            Line.from(
                Span.raw("  \u2705 Created:       ").bold(),
                Span.raw(String.valueOf(report.createdFiles())).green()
            ),
            Line.from(
                Span.raw("  \u23ED Skipped:       ").bold(),
                Span.raw(String.valueOf(report.skippedFiles())).yellow()
            ),
            Line.from(
                Span.raw("  \u274C Errors:        ").bold(),
                report.errorFiles() > 0
                    ? Span.raw(String.valueOf(report.errorFiles())).red()
                    : Span.raw(String.valueOf(report.errorFiles())).green()
            )
        );

        Paragraph statsWidget = Paragraph.builder()
            .text(stats)
            .block(Block.builder()
                .borders(Borders.ALL)
                .borderType(BorderType.ROUNDED)
                .borderStyle(Style.EMPTY.fg(Color.CYAN))
                .title(Title.from(Line.from(
                    Span.raw(" \uD83D\uDCCB Summary ").bold()
                )))
                .titleBottom(Title.from(Line.from(
                    Span.raw(" \u23F1 Duration: ").dim(),
                    Span.raw(report.duration().toMillis() + "ms").cyan(),
                    Span.raw(" ")
                )).right())
                .build())
            .build();

        frame.renderWidget(statsWidget, area);
    }

    private static void renderFileList(Frame frame, Rect area, GenerationReport report,
            int scrollOffset) {
        List<FileGenerationResult> results = report.results();
        List<Line> lines = new ArrayList<>();

        int viewportHeight = area.height() - 2;
        int startIdx = Math.max(0,
            Math.min(scrollOffset, results.size() - viewportHeight));
        int endIdx = Math.min(results.size(), startIdx + viewportHeight);

        for (int i = startIdx; i < endIdx; i++) {
            FileGenerationResult result = results.get(i);
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
            lines.add(Line.from(Span.raw("  No files generated").dim()));
        }

        Paragraph tree = Paragraph.builder()
            .text(Text.from(lines.toArray(Line[]::new)))
            .block(Block.builder()
                .borders(Borders.ALL)
                .borderType(BorderType.ROUNDED)
                .borderStyle(Style.EMPTY.fg(Color.GREEN))
                .title(Title.from(Line.from(
                    Span.raw(" \uD83D\uDCC2 Output Files ").bold()
                )))
                .build())
            .build();

        frame.renderWidget(tree, area);
    }

    private static void renderFooter(Frame frame, Rect area) {
        Line footerLine = Line.from(
            Span.raw(" [\u2191\u2193]").bold().yellow(),
            Span.raw(" Scroll ").dim(),
            Span.raw("[g]").bold().yellow(),
            Span.raw(" Generate more ").dim(),
            Span.raw("[q]").bold().yellow(),
            Span.raw(" Quit").dim()
        );

        Paragraph footer = Paragraph.builder()
            .text(Text.from(footerLine))
            .block(Block.builder()
                .borders(Borders.ALL)
                .borderType(BorderType.ROUNDED)
                .borderStyle(Style.EMPTY.fg(Color.DARK_GRAY))
                .build())
            .build();

        frame.renderWidget(footer, area);
    }
}
