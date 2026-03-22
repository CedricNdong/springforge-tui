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
 * Shows ASCII logo, tagline, progress bar, and quick-start info.
 */
public final class SplashScreen {

    private SplashScreen() {}

    public static void render(Frame frame, Rect area, SplashState state) {
        List<Rect> layout = Layout.vertical()
            .constraints(
                Constraint.length(10),   // logo + tagline
                Constraint.length(3),    // progress bar
                Constraint.fill()        // status + quick reference
            )
            .split(area);

        renderLogoAndTagline(frame, layout.get(0));
        renderProgressBar(frame, layout.get(1), state);
        renderBottomPanel(frame, layout.get(2), state);
    }

    private static void renderLogoAndTagline(Frame frame, Rect area) {
        Text logo = Text.from(
            Line.from(Span.raw("")),
            Line.from(Span.raw("  ███████╗██████╗ ██████╗ ██╗███╗   ██╗ ██████╗ ███████╗ ██████╗ ██████╗  ██████╗ ███████╗").cyan()),
            Line.from(Span.raw("  ██╔════╝██╔══██╗██╔══██╗██║████╗  ██║██╔════╝ ██╔════╝██╔═══██╗██╔══██╗██╔════╝ ██╔════╝").cyan()),
            Line.from(Span.raw("  ███████╗██████╔╝██████╔╝██║██╔██╗ ██║██║  ███╗█████╗  ██║   ██║██████╔╝██║  ███╗█████╗  ").cyan()),
            Line.from(Span.raw("  ╚════██║██╔═══╝ ██╔══██╗██║██║╚██╗██║██║   ██║██╔══╝  ██║   ██║██╔══██╗██║   ██║██╔══╝  ").cyan()),
            Line.from(Span.raw("  ███████║██║     ██║  ██║██║██║ ╚████║╚██████╔╝██║     ╚██████╔╝██║  ██║╚██████╔╝███████╗").cyan()),
            Line.from(Span.raw("  ╚══════╝╚═╝     ╚═╝  ╚═╝╚═╝╚═╝  ╚═══╝ ╚═════╝ ╚═╝      ╚═════╝ ╚═╝  ╚═╝ ╚═════╝ ╚══════╝").cyan(),
                       Span.raw("  TUI").bold().yellow()),
            Line.from(Span.raw("")),
            Line.from(Span.raw("    From @Entity to full API stack — in seconds, not hours.").dim().italic())
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
            ? "Scan complete — " + state.totalFiles() + " entities found"
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

    private static void renderBottomPanel(Frame frame, Rect area, SplashState state) {
        List<Rect> columns = Layout.horizontal()
            .constraints(
                Constraint.percentage(50),
                Constraint.percentage(50)
            )
            .split(area);

        renderStatus(frame, columns.get(0), state);
        renderQuickReference(frame, columns.get(1), state);
    }

    private static void renderStatus(Frame frame, Rect area, SplashState state) {
        Text statusText;
        if (state.errorMessage() != null) {
            statusText = Text.from(
                Line.from(Span.raw(" Error: ").bold().red(),
                    Span.raw(state.errorMessage()).red()),
                Line.from(Span.raw("")),
                Line.from(Span.raw(" Check your project path and try again.").dim())
            );
        } else if (state.scanComplete()) {
            statusText = Text.from(
                Line.from(Span.raw(" Found ").green(),
                    Span.raw(String.valueOf(state.totalFiles())).bold().green(),
                    Span.raw(" @Entity classes").green()),
                Line.from(Span.raw("")),
                Line.from(Span.raw(" Press any key to continue...").bold().yellow())
            );
        } else if (!state.currentFile().isEmpty()) {
            statusText = Text.from(
                Line.from(Span.raw(" Parsing: ").dim(),
                    Span.raw(state.currentFile()).white())
            );
        } else {
            statusText = Text.from(
                Line.from(Span.raw(" Initializing scan...").dim())
            );
        }

        Paragraph paragraph = Paragraph.builder()
            .text(statusText)
            .block(Block.builder()
                .borders(Borders.ALL)
                .borderType(BorderType.ROUNDED)
                .borderStyle(Style.EMPTY.fg(state.errorMessage() != null
                    ? Color.RED : Color.GREEN))
                .title(Title.from(
                    Line.from(Span.raw(" Status ").bold())
                ))
                .build())
            .build();

        frame.renderWidget(paragraph, area);
    }

    private static void renderQuickReference(Frame frame, Rect area, SplashState state) {
        Text refText;
        if (state.scanComplete()) {
            refText = Text.from(
                Line.from(Span.raw(" What happens next:").bold().cyan()),
                Line.from(Span.raw("")),
                Line.from(Span.raw("  1. ").cyan(), Span.raw("Select entities to generate")),
                Line.from(Span.raw("  2. ").cyan(), Span.raw("Configure layers & options")),
                Line.from(Span.raw("  3. ").cyan(), Span.raw("Preview generated code")),
                Line.from(Span.raw("  4. ").cyan(), Span.raw("Write files to disk")),
                Line.from(Span.raw("")),
                Line.from(Span.raw(" Key shortcuts:").bold().yellow()),
                Line.from(Span.raw("  Tab").bold(), Span.raw("     Next screen").dim()),
                Line.from(Span.raw("  Esc/←").bold(), Span.raw("   Go back").dim()),
                Line.from(Span.raw("  Ctrl+G").bold(), Span.raw("  Quick generate").dim()),
                Line.from(Span.raw("  Ctrl+C").bold(), Span.raw("  Quit").dim()),
                Line.from(Span.raw("")),
                Line.from(Span.raw("  springforge --help").yellow(),
                    Span.raw(" for CLI options").dim())
            );
        } else {
            refText = Text.from(
                Line.from(Span.raw(" SpringForge TUI").bold().cyan()),
                Line.from(Span.raw("")),
                Line.from(Span.raw(" Generates a complete Spring Boot API")),
                Line.from(Span.raw(" stack from your @Entity classes:")),
                Line.from(Span.raw("")),
                Line.from(Span.raw("  DTO").cyan(), Span.raw("          Data Transfer Objects")),
                Line.from(Span.raw("  Mapper").cyan(), Span.raw("       Entity ↔ DTO mapping")),
                Line.from(Span.raw("  Repository").cyan(), Span.raw("   Spring Data JPA")),
                Line.from(Span.raw("  Service").cyan(), Span.raw("      Business logic layer")),
                Line.from(Span.raw("  Controller").cyan(), Span.raw("   REST endpoints")),
                Line.from(Span.raw("")),
                Line.from(Span.raw("  springforge --help").yellow(),
                    Span.raw(" for CLI options").dim())
            );
        }

        Paragraph paragraph = Paragraph.builder()
            .text(refText)
            .block(Block.builder()
                .borders(Borders.ALL)
                .borderType(BorderType.ROUNDED)
                .borderStyle(Style.EMPTY.fg(Color.CYAN))
                .title(Title.from(
                    Line.from(Span.raw(" Quick Reference ").bold())
                ))
                .build())
            .build();

        frame.renderWidget(paragraph, area);
    }
}
