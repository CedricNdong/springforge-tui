package dev.springforge.tui.screens;

import java.util.ArrayList;
import java.util.List;

import dev.springforge.engine.model.GeneratedFile;
import dev.springforge.tui.state.PreviewState;

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
 * S4 — Code Preview screen.
 * File tree (left) and syntax-highlighted code preview (right).
 * Preview is rendered in-memory — no files written yet.
 */
public final class PreviewScreen {

    private PreviewScreen() {}

    public static void render(Frame frame, Rect area, PreviewState state) {
        List<Rect> mainLayout = Layout.vertical()
            .constraints(
                Constraint.length(3),
                Constraint.fill(),
                Constraint.length(3)
            )
            .split(area);

        renderHeader(frame, mainLayout.get(0));

        List<Rect> contentLayout = Layout.horizontal()
            .constraints(
                Constraint.percentage(30),
                Constraint.percentage(70)
            )
            .split(mainLayout.get(1));

        renderFileTree(frame, contentLayout.get(0), state);
        renderCodePreview(frame, contentLayout.get(1), state);
        renderFooter(frame, mainLayout.get(2));
    }

    private static void renderHeader(Frame frame, Rect area) {
        Block header = Block.builder()
            .borders(Borders.ALL)
            .borderType(BorderType.ROUNDED)
            .borderStyle(Style.EMPTY.fg(Color.CYAN))
            .title(Title.from(Line.from(
                Span.raw(" SpringForge ").bold().cyan(),
                Span.raw("— Code Preview ").white()
            )))
            .build();

        frame.renderWidget(header, area);
    }

    private static void renderFileTree(Frame frame, Rect area, PreviewState state) {
        List<Line> lines = new ArrayList<>();
        List<GeneratedFile> files = state.files();

        String currentEntity = "";
        for (int i = 0; i < files.size(); i++) {
            GeneratedFile file = files.get(i);
            boolean selected = (i == state.selectedFileIndex());

            if (!file.entityName().equals(currentEntity)) {
                currentEntity = file.entityName();
                lines.add(Line.from(Span.raw("  " + currentEntity).bold().yellow()));
            }

            String fileName = file.outputPath().getFileName().toString();
            String prefix = selected ? "  >> " : "     ";

            Span nameSpan = selected
                ? Span.raw(fileName).bold().cyan()
                : Span.raw(fileName).white();

            lines.add(Line.from(Span.raw(prefix), nameSpan));
        }

        if (files.isEmpty()) {
            lines.add(Line.from(Span.raw("  No files to preview").dim()));
        }

        Paragraph tree = Paragraph.builder()
            .text(Text.from(lines.toArray(Line[]::new)))
            .block(Block.builder()
                .borders(Borders.ALL)
                .borderType(BorderType.ROUNDED)
                .borderStyle(Style.EMPTY.fg(Color.GREEN))
                .title("Files (" + files.size() + ")")
                .build())
            .build();

        frame.renderWidget(tree, area);
    }

    private static void renderCodePreview(Frame frame, Rect area, PreviewState state) {
        GeneratedFile selected = state.selectedFile();
        List<Line> lines = new ArrayList<>();

        if (selected == null) {
            lines.add(Line.from(Span.raw("Select a file to preview").dim()));
        } else {
            String content = selected.content();
            String[] sourceLines = content.split("\n");
            int startLine = Math.min(state.scrollOffset(), sourceLines.length);

            for (int i = startLine; i < sourceLines.length; i++) {
                String lineNum = String.format("%3d ", i + 1);
                String sourceLine = sourceLines[i];

                lines.add(Line.from(
                    Span.raw(lineNum).dim(),
                    highlightSyntax(sourceLine)
                ));
            }
        }

        String title = selected != null
            ? selected.outputPath().getFileName().toString()
            : "Preview";

        Paragraph preview = Paragraph.builder()
            .text(Text.from(lines.toArray(Line[]::new)))
            .block(Block.builder()
                .borders(Borders.ALL)
                .borderType(BorderType.ROUNDED)
                .borderStyle(Style.EMPTY.fg(Color.BLUE))
                .title(title)
                .build())
            .build();

        frame.renderWidget(preview, area);
    }

    private static void renderFooter(Frame frame, Rect area) {
        Line footer = Line.from(
            Span.raw("[↑/↓]").bold().yellow(),
            Span.raw(" Select file  ").dim(),
            Span.raw("[PgUp/PgDn]").bold().yellow(),
            Span.raw(" Scroll  ").dim(),
            Span.raw("[←]").bold().yellow(),
            Span.raw(" Back  ").dim(),
            Span.raw("[Ctrl+G]").bold().yellow(),
            Span.raw(" Generate").dim()
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

    private static Span highlightSyntax(String line) {
        String trimmed = line.stripLeading();

        if (trimmed.startsWith("package ") || trimmed.startsWith("import ")) {
            return Span.raw(line).dim();
        }
        if (trimmed.startsWith("@")) {
            return Span.raw(line).yellow();
        }
        if (trimmed.startsWith("//")) {
            return Span.raw(line).dim();
        }
        if (trimmed.startsWith("public ") || trimmed.startsWith("private ")
                || trimmed.startsWith("protected ")) {
            return Span.raw(line).cyan();
        }
        return Span.raw(line).white();
    }
}
