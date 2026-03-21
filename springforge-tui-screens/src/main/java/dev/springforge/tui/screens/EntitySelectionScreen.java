package dev.springforge.tui.screens;

import java.util.ArrayList;
import java.util.List;

import dev.springforge.engine.model.EntityDescriptor;
import dev.springforge.engine.model.FieldDescriptor;
import dev.springforge.tui.state.EntitySelectionState;

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
 * S2 — Entity Selection screen.
 * Lists all discovered @Entity classes with checkboxes.
 * Detail panel shows fields, types, annotations for focused entity.
 */
public final class EntitySelectionScreen {

    private EntitySelectionScreen() {}

    public static void render(Frame frame, Rect area, EntitySelectionState state) {
        List<Rect> mainLayout = Layout.vertical()
            .constraints(
                Constraint.length(3),
                Constraint.fill(),
                Constraint.length(3)
            )
            .split(area);

        renderHeader(frame, mainLayout.get(0), state);

        if (state.showHelp()) {
            renderHelpOverlay(frame, mainLayout.get(1));
        } else {
            List<Rect> contentLayout = Layout.horizontal()
                .constraints(
                    Constraint.percentage(40),
                    Constraint.percentage(60)
                )
                .split(mainLayout.get(1));

            renderEntityList(frame, contentLayout.get(0), state);
            renderEntityDetail(frame, contentLayout.get(1), state);
        }

        renderFooter(frame, mainLayout.get(2), state);
    }

    private static void renderHeader(Frame frame, Rect area, EntitySelectionState state) {
        String filterInfo = state.filterText().isEmpty()
            ? ""
            : "  Filter: " + state.filterText();

        Block header = Block.builder()
            .borders(Borders.ALL)
            .borderType(BorderType.ROUNDED)
            .borderStyle(Style.EMPTY.fg(Color.CYAN))
            .title(Title.from(Line.from(
                Span.raw(" SpringForge ").bold().cyan(),
                Span.raw("— Entity Selection ").white(),
                Span.raw(filterInfo).yellow()
            )))
            .build();

        frame.renderWidget(header, area);
    }

    private static void renderEntityList(Frame frame, Rect area,
            EntitySelectionState state) {
        List<EntityDescriptor> filtered = state.filteredEntities();
        List<Line> lines = new ArrayList<>();

        for (int i = 0; i < filtered.size(); i++) {
            EntityDescriptor entity = filtered.get(i);
            boolean selected = state.selectedEntityNames().contains(entity.className());
            boolean focused = (i == state.focusedIndex());

            String checkbox = selected ? "[x] " : "[ ] ";
            Span checkSpan = selected
                ? Span.raw(checkbox).green()
                : Span.raw(checkbox).dim();

            Span nameSpan = focused
                ? Span.raw(entity.className()).bold().cyan()
                : Span.raw(entity.className()).white();

            lines.add(Line.from(checkSpan, nameSpan));
        }

        if (filtered.isEmpty()) {
            lines.add(Line.from(Span.raw("No entities match filter").dim()));
        }

        lines.add(Line.from(Span.raw("")));
        lines.add(Line.from(
            Span.raw("[A]").bold().yellow(), Span.raw(" All  ").dim(),
            Span.raw("[N]").bold().yellow(), Span.raw(" None  ").dim(),
            Span.raw("[/]").bold().yellow(), Span.raw(" Filter").dim()
        ));

        Paragraph list = Paragraph.builder()
            .text(Text.from(lines.toArray(Line[]::new)))
            .block(Block.builder()
                .borders(Borders.ALL)
                .borderType(BorderType.ROUNDED)
                .borderStyle(Style.EMPTY.fg(Color.GREEN))
                .title("Entities (" + filtered.size() + ")")
                .build())
            .build();

        frame.renderWidget(list, area);
    }

    private static void renderEntityDetail(Frame frame, Rect area,
            EntitySelectionState state) {
        EntityDescriptor focused = state.focusedEntity();
        List<Line> lines = new ArrayList<>();

        if (focused == null) {
            lines.add(Line.from(Span.raw("No entity selected").dim()));
        } else {
            lines.add(Line.from(
                Span.raw("Package: ").bold(),
                Span.raw(focused.packageName()).yellow()
            ));
            lines.add(Line.from(
                Span.raw("Namespace: ").bold(),
                Span.raw(focused.namespace().name().toLowerCase()).white()
            ));
            lines.add(Line.from(
                Span.raw("Lombok: ").bold(),
                focused.hasLombok()
                    ? Span.raw("yes").green()
                    : Span.raw("no").dim()
            ));
            lines.add(Line.from(Span.raw("")));
            lines.add(Line.from(Span.raw("Fields:").bold()));

            for (FieldDescriptor field : focused.fields()) {
                String prefix = field.isId() ? "  > " : "  - ";
                List<Span> spans = new ArrayList<>();
                spans.add(Span.raw(prefix));
                spans.add(Span.raw(field.type()).cyan());
                spans.add(Span.raw(" "));
                spans.add(Span.raw(field.name()).white());
                if (field.isId()) spans.add(Span.raw(" (@Id)").yellow());
                if (field.isNullable()) spans.add(Span.raw(" nullable").dim());
                if (field.isUnique()) spans.add(Span.raw(" unique").dim());
                if (field.relation() != dev.springforge.engine.model.RelationType.NONE) {
                    spans.add(Span.raw(" @" + field.relation().name()).magenta());
                }
                lines.add(Line.from(spans.toArray(Span[]::new)));
            }

            lines.add(Line.from(Span.raw("")));
            lines.add(Line.from(
                Span.raw("Annotations: ").bold(),
                Span.raw(String.join(", ",
                    focused.classAnnotations().stream().map(a -> "@" + a).toList())).dim()
            ));
        }

        Paragraph detail = Paragraph.builder()
            .text(Text.from(lines.toArray(Line[]::new)))
            .block(Block.builder()
                .borders(Borders.ALL)
                .borderType(BorderType.ROUNDED)
                .borderStyle(Style.EMPTY.fg(Color.BLUE))
                .title("Entity Details — " +
                    (focused != null ? focused.className() : ""))
                .build())
            .build();

        frame.renderWidget(detail, area);
    }

    private static void renderFooter(Frame frame, Rect area,
            EntitySelectionState state) {
        Line footer = Line.from(
            Span.raw(" Selected: ").bold(),
            Span.raw(state.selectedEntityNames().size() + " entities").cyan(),
            Span.raw("   "),
            Span.raw("[Tab]").bold().yellow(),
            Span.raw(" Layer Config  ").dim(),
            Span.raw("[Ctrl+G]").bold().yellow(),
            Span.raw(" Generate  ").dim(),
            Span.raw("[?]").bold().yellow(),
            Span.raw(" Help  ").dim(),
            Span.raw("[q]").bold().yellow(),
            Span.raw(" Quit").dim()
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

    private static void renderHelpOverlay(Frame frame, Rect area) {
        Text helpText = Text.from(
            Line.from(Span.raw("  Keyboard Shortcuts").bold().cyan()),
            Line.from(Span.raw("")),
            Line.from(Span.raw("  ↑/↓       ").bold().yellow(), Span.raw("Navigate entities")),
            Line.from(Span.raw("  Space     ").bold().yellow(), Span.raw("Toggle selection")),
            Line.from(Span.raw("  A         ").bold().yellow(), Span.raw("Select all")),
            Line.from(Span.raw("  N         ").bold().yellow(), Span.raw("Deselect all")),
            Line.from(Span.raw("  /         ").bold().yellow(), Span.raw("Fuzzy search / filter")),
            Line.from(Span.raw("  Tab       ").bold().yellow(), Span.raw("Next screen")),
            Line.from(Span.raw("  Esc       ").bold().yellow(), Span.raw("Go back / cancel")),
            Line.from(Span.raw("  Ctrl+G    ").bold().yellow(), Span.raw("Start generation")),
            Line.from(Span.raw("  ?         ").bold().yellow(), Span.raw("Toggle this help")),
            Line.from(Span.raw("  q         ").bold().yellow(), Span.raw("Quit")),
            Line.from(Span.raw("")),
            Line.from(Span.raw("  Press ? to close").dim())
        );

        Paragraph help = Paragraph.builder()
            .text(helpText)
            .block(Block.builder()
                .borders(Borders.ALL)
                .borderType(BorderType.ROUNDED)
                .borderStyle(Style.EMPTY.fg(Color.YELLOW))
                .title(" Help ")
                .build())
            .build();

        frame.renderWidget(help, area);
    }
}
