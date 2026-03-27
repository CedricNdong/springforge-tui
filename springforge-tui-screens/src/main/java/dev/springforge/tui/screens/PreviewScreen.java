package dev.springforge.tui.screens;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import dev.tamboui.widgets.common.SizedWidget;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.tree.TreeNode;
import dev.tamboui.widgets.tree.TreeState;
import dev.tamboui.widgets.tree.TreeWidget;

/**
 * S4 — Code Preview screen.
 * File tree (left) using TreeWidget and syntax-highlighted code preview (right).
 * Preview is rendered in-memory — no files written yet.
 */
public final class PreviewScreen {

    private PreviewScreen() {}

    public static void render(Frame frame, Rect area, PreviewState state, TreeState treeState) {
        List<Rect> mainLayout = Layout.vertical()
            .constraints(
                Constraint.length(3),
                Constraint.fill(),
                Constraint.length(3)
            )
            .split(area);

        renderHeader(frame, mainLayout.get(0), state);

        List<Rect> contentLayout = Layout.horizontal()
            .constraints(
                Constraint.percentage(30),
                Constraint.percentage(70)
            )
            .split(mainLayout.get(1));

        renderFileTree(frame, contentLayout.get(0), state, treeState);
        renderCodePreview(frame, contentLayout.get(1), state);
        renderFooter(frame, mainLayout.get(2));
    }

    private static void renderHeader(Frame frame, Rect area, PreviewState state) {
        Block header = Block.builder()
            .borders(Borders.ALL)
            .borderType(BorderType.ROUNDED)
            .borderStyle(Style.EMPTY.fg(Color.CYAN))
            .title(Title.from(Line.from(
                Span.raw(" \uD83D\uDC41 SpringForge ").bold().cyan(),
                Span.raw("\u2014 Code Preview ").white()
            )))
            .titleBottom(Title.from(Line.from(
                Span.raw(" \uD83D\uDCC4 ").dim(),
                Span.raw(String.valueOf(state.files().size())).cyan(),
                Span.raw(" files ready to generate ").dim()
            )))
            .build();

        frame.renderWidget(header, area);
    }

    private static void renderFileTree(Frame frame, Rect area, PreviewState state,
            TreeState treeState) {
        List<GeneratedFile> files = state.files();

        if (files.isEmpty()) {
            Paragraph empty = Paragraph.builder()
                .text(Text.from(Line.from(Span.raw("  No files to preview").dim())))
                .block(Block.builder()
                    .borders(Borders.ALL)
                    .borderType(BorderType.ROUNDED)
                    .borderStyle(Style.EMPTY.fg(Color.GREEN))
                    .title(Title.from(Line.from(
                        Span.raw(" \uD83D\uDCC2 Files (0) ").bold()
                    )))
                    .build())
                .build();
            frame.renderWidget(empty, area);
            return;
        }

        // Group files by entity name
        Map<String, List<GeneratedFile>> grouped = new LinkedHashMap<>();
        for (GeneratedFile file : files) {
            grouped.computeIfAbsent(file.entityName(), k -> new ArrayList<>()).add(file);
        }

        // Build tree nodes: one root per entity, file leaves underneath
        List<TreeNode<GeneratedFile>> roots = new ArrayList<>();
        for (Map.Entry<String, List<GeneratedFile>> entry : grouped.entrySet()) {
            TreeNode<GeneratedFile> entityNode = TreeNode.of(
                "\uD83D\uDCE6 " + entry.getKey());
            for (GeneratedFile file : entry.getValue()) {
                String fileName = file.outputPath().getFileName().toString();
                entityNode.add(
                    TreeNode.of(getFileIcon(fileName) + " " + fileName, file).leaf()
                );
            }
            entityNode.expanded();
            roots.add(entityNode);
        }

        TreeWidget<TreeNode<GeneratedFile>> tree = TreeWidget.<TreeNode<GeneratedFile>>builder()
            .roots(roots)
            .children(TreeNode::children)
            .isLeaf(TreeNode::isLeaf)
            .expansionState(TreeNode::isExpanded, TreeNode::expanded)
            .simpleNodeRenderer(node -> Paragraph.builder()
                .text(Text.from(Line.from(Span.raw(node.label()))))
                .build())
            .highlightStyle(Style.EMPTY.fg(Color.CYAN).bold())
            .highlightSymbol(" \u25B6 ")
            .block(Block.builder()
                .borders(Borders.ALL)
                .borderType(BorderType.ROUNDED)
                .borderStyle(Style.EMPTY.fg(Color.GREEN))
                .title(Title.from(Line.from(
                    Span.raw(" \uD83D\uDCC2 Files (").bold(),
                    Span.raw(String.valueOf(files.size())).cyan(),
                    Span.raw(") ").bold()
                )))
                .build())
            .scrollbar()
            .build();

        frame.renderStatefulWidget(tree, area, treeState);
    }

    private static void renderCodePreview(Frame frame, Rect area, PreviewState state) {
        GeneratedFile selected = state.selectedFile();
        List<Line> lines = new ArrayList<>();

        if (selected == null) {
            lines.add(Line.from(Span.raw(" Select a file to preview").dim()));
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

        String fileName = selected != null
            ? selected.outputPath().getFileName().toString()
            : "Preview";

        Paragraph preview = Paragraph.builder()
            .text(Text.from(lines.toArray(Line[]::new)))
            .block(Block.builder()
                .borders(Borders.ALL)
                .borderType(BorderType.ROUNDED)
                .borderStyle(Style.EMPTY.fg(Color.BLUE))
                .title(Title.from(Line.from(
                    Span.raw(" \uD83D\uDCDD " + fileName + " ").bold()
                )))
                .build())
            .build();

        frame.renderWidget(preview, area);
    }

    private static void renderFooter(Frame frame, Rect area) {
        List<Rect> footerLayout = Layout.horizontal()
            .constraints(
                Constraint.fill(),
                Constraint.length(22)
            )
            .split(area);

        // Left: navigation keys
        Line leftLine = Line.from(
            Span.raw(" [\u2191\u2193]").bold().yellow(),
            Span.raw(" Navigate ").dim(),
            Span.raw("[\u2192]").bold().yellow(),
            Span.raw(" Expand ").dim(),
            Span.raw("[\u2190]").bold().yellow(),
            Span.raw(" Collapse ").dim(),
            Span.raw("[j/k]").bold().yellow(),
            Span.raw(" Scroll ").dim(),
            Span.raw("[Tab]").bold().yellow(),
            Span.raw(" Next ").dim(),
            Span.raw("[\u21E7Tab]").bold().yellow(),
            Span.raw(" Back").dim()
        );

        Paragraph leftFooter = Paragraph.builder()
            .text(Text.from(leftLine))
            .block(Block.builder()
                .borders(Borders.ALL)
                .borderType(BorderType.ROUNDED)
                .borderStyle(Style.EMPTY.fg(Color.DARK_GRAY))
                .build())
            .build();

        frame.renderWidget(leftFooter, footerLayout.get(0));

        // Right: generate
        Line rightLine = Line.from(
            Span.raw(" [Ctrl+G]").bold().yellow(),
            Span.raw(" Generate").dim()
        );

        Paragraph rightFooter = Paragraph.builder()
            .text(Text.from(rightLine))
            .block(Block.builder()
                .borders(Borders.ALL)
                .borderType(BorderType.ROUNDED)
                .borderStyle(Style.EMPTY.fg(Color.DARK_GRAY))
                .build())
            .build();

        frame.renderWidget(rightFooter, footerLayout.get(1));
    }

    private static String getFileIcon(String fileName) {
        if (fileName.endsWith("Controller.java")) return "\uD83C\uDF10";
        if (fileName.endsWith("Service.java")) return "\u2699";
        if (fileName.endsWith("ServiceImpl.java")) return "\u2699";
        if (fileName.endsWith("Repository.java")) return "\uD83D\uDDC4";
        if (fileName.endsWith("Mapper.java")) return "\uD83D\uDD04";
        if (fileName.contains("Dto") || fileName.contains("DTO")) return "\uD83D\uDCE6";
        if (fileName.endsWith(".sql") || fileName.endsWith(".xml")) return "\uD83D\uDCBE";
        return "\uD83D\uDCC4";
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
