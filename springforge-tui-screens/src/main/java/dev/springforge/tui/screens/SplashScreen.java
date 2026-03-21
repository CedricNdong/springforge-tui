package dev.springforge.tui.screens;

import java.util.List;

import dev.springforge.tui.state.SplashState;

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
 * S1 — Splash / Scan screen.
 * Shows ASCII logo and progress bar during project scan.
 */
public final class SplashScreen {

    private SplashScreen() {}

    public static void render(Frame frame, Rect area, SplashState state) {
        List<Rect> layout = Layout.vertical()
            .constraints(
                Constraint.length(8),
                Constraint.length(3),
                Constraint.fill()
            )
            .split(area);

        renderLogo(frame, layout.get(0));
        renderProgressBar(frame, layout.get(1), state);
        renderStatus(frame, layout.get(2), state);
    }

    private static void renderLogo(Frame frame, Rect area) {
        Text logo = Text.from(
            Line.from(Span.raw("").raw("")),
            Line.from(Span.raw("  ███████╗██████╗ ██████╗ ██╗███╗   ██╗ ██████╗ ███████╗ ██████╗ ██████╗  ██████╗ ███████╗").cyan()),
            Line.from(Span.raw("  ██╔════╝██╔══██╗██╔══██╗██║████╗  ██║██╔════╝ ██╔════╝██╔═══██╗██╔══██╗██╔════╝ ██╔════╝").cyan()),
            Line.from(Span.raw("  ███████╗██████╔╝██████╔╝██║██╔██╗ ██║██║  ███╗█████╗  ██║   ██║██████╔╝██║  ███╗█████╗  ").cyan()),
            Line.from(Span.raw("  ╚════██║██╔═══╝ ██╔══██╗██║██║╚██╗██║██║   ██║██╔══╝  ██║   ██║██╔══██╗██║   ██║██╔══╝  ").cyan()),
            Line.from(Span.raw("  ███████║██║     ██║  ██║██║██║ ╚████║╚██████╔╝██║     ╚██████╔╝██║  ██║╚██████╔╝███████╗").cyan()),
            Line.from(Span.raw("  ╚══════╝╚═╝     ╚═╝  ╚═╝╚═╝╚═╝  ╚═══╝ ╚═════╝ ╚═╝      ╚═════╝ ╚═╝  ╚═╝ ╚═════╝ ╚══════╝").cyan())
        );

        Paragraph paragraph = Paragraph.builder()
            .text(logo)
            .block(Block.builder()
                .borders(Borders.NONE)
                .build())
            .build();

        frame.renderWidget(paragraph, area);
    }

    private static void renderProgressBar(Frame frame, Rect area, SplashState state) {
        double ratio = state.totalFiles() > 0
            ? (double) state.scannedFiles() / state.totalFiles()
            : 0.0;

        String label = state.scanComplete()
            ? "Scan complete!"
            : "Scanning... " + state.scannedFiles() + " files";

        Gauge gauge = Gauge.builder()
            .block(Block.builder()
                .borders(Borders.ALL)
                .borderType(BorderType.ROUNDED)
                .borderStyle(Style.EMPTY.fg(Color.CYAN))
                .title("Project Scan")
                .build())
            .gaugeStyle(Style.EMPTY.fg(Color.GREEN))
            .ratio(state.scanComplete() ? 1.0 : ratio)
            .label(label)
            .build();

        frame.renderWidget(gauge, area);
    }

    private static void renderStatus(Frame frame, Rect area, SplashState state) {
        Text statusText;
        if (state.errorMessage() != null) {
            statusText = Text.from(
                Line.from(Span.raw("Error: ").bold().red(),
                    Span.raw(state.errorMessage()).red())
            );
        } else if (state.scanComplete()) {
            statusText = Text.from(
                Line.from(Span.raw("Found ").green(),
                    Span.raw(String.valueOf(state.totalFiles())).bold().green(),
                    Span.raw(" entity files").green()),
                Line.from(Span.raw("")),
                Line.from(Span.raw("Press any key to continue...").dim())
            );
        } else if (!state.currentFile().isEmpty()) {
            statusText = Text.from(
                Line.from(Span.raw("Current: ").dim(),
                    Span.raw(state.currentFile()).white())
            );
        } else {
            statusText = Text.from(
                Line.from(Span.raw("Initializing scan...").dim())
            );
        }

        Paragraph paragraph = Paragraph.builder()
            .text(statusText)
            .block(Block.builder()
                .borders(Borders.ALL)
                .borderType(BorderType.ROUNDED)
                .borderStyle(Style.EMPTY.fg(Color.DARK_GRAY))
                .build())
            .build();

        frame.renderWidget(paragraph, area);
    }
}
