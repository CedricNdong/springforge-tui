package dev.springforge.tui.screens;

import java.util.List;

import dev.springforge.tui.state.ErrorState;

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
 * S8 — Error screen with retry / skip options.
 */
public final class ErrorScreen {

    private ErrorScreen() {}

    public static void render(Frame frame, Rect area, ErrorState state) {
        List<Rect> layout = Layout.vertical()
            .constraints(
                Constraint.length(3),
                Constraint.fill(),
                Constraint.length(3)
            )
            .split(area);

        renderHeader(frame, layout.get(0));
        renderErrorDetails(frame, layout.get(1), state);
        renderFooter(frame, layout.get(2), state);
    }

    private static void renderHeader(Frame frame, Rect area) {
        Block header = Block.builder()
            .borders(Borders.ALL)
            .borderType(BorderType.ROUNDED)
            .borderStyle(Style.EMPTY.fg(Color.RED))
            .title(Title.from(Line.from(
                Span.raw(" SpringForge ").bold().cyan(),
                Span.raw("— Error ").bold().red()
            )))
            .build();

        frame.renderWidget(header, area);
    }

    private static void renderErrorDetails(Frame frame, Rect area, ErrorState state) {
        var lines = new java.util.ArrayList<Line>();
        lines.add(Line.from(Span.raw("")));
        lines.add(Line.from(
            Span.raw("  Error: ").bold().red(),
            Span.raw(state.errorMessage()).white()
        ));

        if (state.filePath() != null) {
            lines.add(Line.from(Span.raw("")));
            lines.add(Line.from(
                Span.raw("  File: ").bold(),
                Span.raw(state.filePath()).yellow()
            ));
        }

        lines.add(Line.from(Span.raw("")));
        lines.add(Line.from(Span.raw("  Please check the error above and choose an action.").dim()));

        Paragraph error = Paragraph.builder()
            .text(Text.from(lines.toArray(Line[]::new)))
            .block(Block.builder()
                .borders(Borders.ALL)
                .borderType(BorderType.ROUNDED)
                .borderStyle(Style.EMPTY.fg(Color.RED))
                .title("Error Details")
                .build())
            .build();

        frame.renderWidget(error, area);
    }

    private static void renderFooter(Frame frame, Rect area, ErrorState state) {
        Line footer;
        if (state.canRetry()) {
            footer = Line.from(
                Span.raw("[R]").bold().yellow(),
                Span.raw(" Retry  ").dim(),
                Span.raw("[S]").bold().yellow(),
                Span.raw(" Skip  ").dim(),
                Span.raw("[Q]").bold().yellow(),
                Span.raw(" Quit").dim()
            );
        } else {
            footer = Line.from(
                Span.raw("[Q]").bold().yellow(),
                Span.raw(" Quit").dim()
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
