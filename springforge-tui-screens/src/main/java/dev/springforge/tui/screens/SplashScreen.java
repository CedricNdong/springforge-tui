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
            Line.from(Span.raw("    \u26A1 From @Entity to full API stack")
                .dim().italic(),
                Span.raw(" \u2014 in seconds, not hours.").dim().italic())
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
            ? "\u2705 Scan complete \u2014 " + state.totalFiles() + " entities found"
            : "\u23F3 Scanning... " + state.scannedFiles() + " files";

        Gauge gauge = Gauge.builder()
            .block(Block.builder()
                .borders(Borders.ALL)
                .borderType(BorderType.ROUNDED)
                .borderStyle(Style.EMPTY.fg(Color.CYAN))
                .title("\uD83D\uDD0D Project Scan")
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
                Constraint.percentage(35),
                Constraint.percentage(65)
            )
            .split(area);

        renderStatus(frame, columns.get(0), state);
        renderQuickReference(frame, columns.get(1), state);
    }

    private static void renderStatus(Frame frame, Rect area, SplashState state) {
        Text statusText;
        if (state.errorMessage() != null) {
            statusText = Text.from(
                Line.from(Span.raw(" \u274C Error: ").bold().red(),
                    Span.raw(state.errorMessage()).red()),
                Line.from(Span.raw("")),
                Line.from(Span.raw(" Check your project path").dim()),
                Line.from(Span.raw(" and try again.").dim())
            );
        } else if (state.scanComplete()) {
            statusText = Text.from(
                Line.from(Span.raw(" \u2705 Found ").green(),
                    Span.raw(String.valueOf(state.totalFiles())).bold().green(),
                    Span.raw(" @Entity classes").green()),
                Line.from(Span.raw("")),
                Line.from(state.configFound()
                    ? Span.raw(" \uD83D\uDCC4 springforge.yml found").green()
                    : Span.raw(" \uD83D\uDCC4 No springforge.yml").dim()),
                Line.from(Span.raw("")),
                Line.from(Span.raw(" \uD83D\uDC49 Press ").dim(),
                    Span.raw("[Tab]").bold().yellow(),
                    Span.raw(" to continue...").dim())
            );
        } else if (!state.currentFile().isEmpty()) {
            statusText = Text.from(
                Line.from(Span.raw(" \u23F3 Parsing: ").dim(),
                    Span.raw(state.currentFile()).white())
            );
        } else {
            statusText = Text.from(
                Line.from(Span.raw(" \u23F3 Initializing scan...").dim())
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
                    Line.from(Span.raw(" \uD83D\uDCCA Status ").bold())
                ))
                .build())
            .build();

        frame.renderWidget(paragraph, area);
    }

    private static void renderQuickReference(Frame frame, Rect area, SplashState state) {
        Text refText;
        if (state.scanComplete()) {
            var lines = new java.util.ArrayList<Line>();

            lines.add(Line.from(Span.raw(" \uD83D\uDCCB What happens next:").bold().cyan()));
            lines.add(Line.from(Span.raw("")));
            lines.add(Line.from(Span.raw("  \uD83D\uDCE6 1. ").cyan(), Span.raw("Select entities to generate")));
            lines.add(Line.from(Span.raw("  \u2699  2. ").cyan(), Span.raw("Configure layers & options")));
            lines.add(Line.from(Span.raw("  \uD83D\uDC41  3. ").cyan(), Span.raw("Preview generated code")));
            lines.add(Line.from(Span.raw("  \uD83D\uDCBE 4. ").cyan(), Span.raw("Write files to disk")));
            lines.add(Line.from(Span.raw("")));

            // Config file info
            if (state.configFound()) {
                lines.add(Line.from(
                    Span.raw(" \uD83D\uDCC4 springforge.yml").bold().green(),
                    Span.raw(" detected — your previous settings are ready!")));
            } else {
                lines.add(Line.from(
                    Span.raw(" \uD83D\uDCC4 springforge.yml").bold().yellow(),
                    Span.raw(" not found — your choices will be saved for next time.")));
            }
            lines.add(Line.from(Span.raw("")));

            // Key shortcuts
            lines.add(Line.from(Span.raw(" \u2328  Some key shortcuts:").bold().yellow()));
            lines.add(Line.from(
                Span.raw("  Tab").bold(), Span.raw("  Next screen   ").dim(),
                Span.raw("Esc/\u2190").bold(), Span.raw("  Go back").dim()));
            lines.add(Line.from(
                Span.raw("  Ctrl+G").bold(), Span.raw(" Generate     ").dim(),
                Span.raw("Ctrl+C").bold(), Span.raw("  Quit").dim()));
            lines.add(Line.from(Span.raw("  \u2139 Each screen shows its available keys in the footer.").dim()));
            lines.add(Line.from(Span.raw("")));

            // CLI help
            lines.add(Line.from(
                Span.raw("  springforge --help").yellow(),
                Span.raw("  for all CLI options").dim()));
            lines.add(Line.from(Span.raw("")));

            // Fun message
            lines.add(Line.from(Span.raw("  \u2615 Love SpringForge? Star us on GitHub & spread the word!").dim()));
            lines.add(Line.from(Span.raw("  \uD83D\uDE80 Happy generating! ").bold().green()));

            refText = Text.from(lines.toArray(Line[]::new));
        } else {
            refText = Text.from(
                Line.from(Span.raw(" \uD83D\uDE80 SpringForge TUI").bold().cyan()),
                Line.from(Span.raw("")),
                Line.from(Span.raw(" Generates a complete Spring Boot API")),
                Line.from(Span.raw(" stack from your @Entity classes:")),
                Line.from(Span.raw("")),
                Line.from(Span.raw("  \uD83D\uDCE6 DTO").cyan(), Span.raw("          Data Transfer Objects")),
                Line.from(Span.raw("  \uD83D\uDD04 Mapper").cyan(), Span.raw("       Entity \u2194 DTO mapping")),
                Line.from(Span.raw("  \uD83D\uDDC4  Repository").cyan(), Span.raw("   Spring Data JPA")),
                Line.from(Span.raw("  \u2699  Service").cyan(), Span.raw("      Business logic layer")),
                Line.from(Span.raw("  \uD83C\uDF10 Controller").cyan(), Span.raw("   REST endpoints")),
                Line.from(Span.raw("")),
                Line.from(Span.raw("  \u2328 Key shortcuts are shown in the footer").dim()),
                Line.from(Span.raw("  of each screen.").dim()),
                Line.from(Span.raw("")),
                Line.from(Span.raw("  springforge --help").yellow(),
                    Span.raw("  for CLI options").dim()),
                Line.from(Span.raw("")),
                Line.from(Span.raw("  \u2615 Love SpringForge? Star us on GitHub!").dim())
            );
        }

        Paragraph paragraph = Paragraph.builder()
            .text(refText)
            .block(Block.builder()
                .borders(Borders.ALL)
                .borderType(BorderType.ROUNDED)
                .borderStyle(Style.EMPTY.fg(Color.CYAN))
                .title(Title.from(
                    Line.from(Span.raw(" \u2728 Quick Reference ").bold())
                ))
                .build())
            .build();

        frame.renderWidget(paragraph, area);
    }
}
