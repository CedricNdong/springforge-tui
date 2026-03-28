package dev.springforge.tui.screens;

import java.util.ArrayList;
import java.util.List;

import dev.springforge.engine.model.ConflictStrategy;
import dev.springforge.engine.model.Layer;
import dev.springforge.engine.model.MapperLib;
import dev.springforge.engine.model.SpringVersion;
import dev.springforge.tui.state.LayerConfigState;
import dev.springforge.tui.state.LayerConfigState.ActivePanel;
import dev.springforge.tui.state.LayerConfigState.MigrationChoice;

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
 * S3 — Layer Configuration screen.
 * Two navigable panels: [1] Layers to Generate, [2] Options.
 */
public final class LayerConfigScreen {

    private LayerConfigScreen() {}

    public static void render(Frame frame, Rect area, LayerConfigState state) {
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
                Constraint.percentage(45),
                Constraint.percentage(55)
            )
            .split(mainLayout.get(1));

        renderLayerList(frame, contentLayout.get(0), state);
        renderOptions(frame, contentLayout.get(1), state);
        renderFooter(frame, mainLayout.get(2));
    }

    private static void renderHeader(Frame frame, Rect area, LayerConfigState state) {
        Block header = Block.builder()
            .borders(Borders.ALL)
            .borderType(BorderType.ROUNDED)
            .borderStyle(Style.EMPTY.fg(Color.CYAN))
            .title(Title.from(Line.from(
                Span.raw(" \u2699 SpringForge ").bold().cyan(),
                Span.raw("\u2014 Layer Configuration ").white()
            )))
            .build();

        frame.renderWidget(header, area);
    }

    private static void renderLayerList(Frame frame, Rect area, LayerConfigState state) {
        boolean active = state.activePanel() == ActivePanel.LAYERS;
        List<Layer> layers = LayerConfigState.DISPLAY_LAYERS;
        List<Line> lines = new ArrayList<>();

        for (int i = 0; i < layers.size(); i++) {
            Layer layer = layers.get(i);
            boolean selected = state.selectedLayers().contains(layer);
            boolean focused = active && (i == state.focusedIndex());

            String pointer = focused ? " \u25B6 " : "   ";
            String checkbox = selected ? "\u2705 " : "\u2B1C ";

            Span pointerSpan = focused
                ? Span.raw(pointer).cyan()
                : Span.raw(pointer);
            Span checkSpan = selected
                ? Span.raw(checkbox).green()
                : Span.raw(checkbox).dim();

            String label = formatLayerName(layer);
            Span labelSpan = focused
                ? Span.raw(label).bold().cyan()
                : Span.raw(label).white();

            lines.add(Line.from(pointerSpan, checkSpan, labelSpan));
        }

        Color borderColor = active ? Color.GREEN : Color.DARK_GRAY;
        Span panelIndicator = active
            ? Span.raw(" [1] ").bold().green()
            : Span.raw(" [1] ").dim();

        Paragraph list = Paragraph.builder()
            .text(Text.from(lines.toArray(Line[]::new)))
            .block(Block.builder()
                .borders(Borders.ALL)
                .borderType(BorderType.ROUNDED)
                .borderStyle(Style.EMPTY.fg(borderColor))
                .title(Title.from(Line.from(
                    panelIndicator,
                    Span.raw("\uD83D\uDCC2 Layers to Generate ").bold()
                )))
                .titleBottom(Title.from(Line.from(
                    Span.raw(" \uD83D\uDCE6 Entities: ").dim(),
                    Span.raw(String.valueOf(state.entityCount())).cyan(),
                    Span.raw(" | \uD83D\uDCC2 Layers: ").dim(),
                    Span.raw(String.valueOf(state.selectedLayers().size())).cyan(),
                    Span.raw(" | \uD83D\uDCC4 Files: ~").dim(),
                    Span.raw(String.valueOf(state.estimatedFileCount())).cyan(),
                    Span.raw(" ")
                )))
                .build())
            .build();

        frame.renderWidget(list, area);
    }

    private static void renderOptions(Frame frame, Rect area, LayerConfigState state) {
        boolean active = state.activePanel() == ActivePanel.OPTIONS;
        List<Line> lines = new ArrayList<>();

        // Option 0: Spring Boot version
        boolean focusSpring = active && state.optionFocusedIndex() == 0;
        String springPointer = focusSpring ? " \u25B6 " : "   ";
        lines.add(Line.from(
            focusSpring ? Span.raw(springPointer).cyan() : Span.raw(springPointer),
            focusSpring
                ? Span.raw("\uD83C\uDF31 Spring Boot:").bold().cyan()
                : Span.raw("\uD83C\uDF31 Spring Boot:").bold(),
            Span.raw("  "),
            radioOption("3.x", state.springVersion() == SpringVersion.V3),
            Span.raw("  "),
            radioOption("2.x", state.springVersion() == SpringVersion.V2)
        ));
        lines.add(Line.from(Span.raw("")));

        // Option 1: Mapper lib
        boolean focusMapper = active && state.optionFocusedIndex() == 1;
        String mapperPointer = focusMapper ? " \u25B6 " : "   ";
        lines.add(Line.from(
            focusMapper ? Span.raw(mapperPointer).cyan() : Span.raw(mapperPointer),
            focusMapper
                ? Span.raw("\uD83D\uDD04 Mapper:").bold().cyan()
                : Span.raw("\uD83D\uDD04 Mapper:").bold(),
            Span.raw("       "),
            radioOption("MapStruct", state.mapperLib() == MapperLib.MAPSTRUCT),
            Span.raw("  "),
            radioOption("ModelMapper", state.mapperLib() == MapperLib.MODEL_MAPPER)
        ));
        lines.add(Line.from(Span.raw("")));

        // Option 2: Conflict strategy
        boolean focusConflict = active && state.optionFocusedIndex() == 2;
        String conflictPointer = focusConflict ? " \u25B6 " : "   ";
        lines.add(Line.from(
            focusConflict ? Span.raw(conflictPointer).cyan() : Span.raw(conflictPointer),
            focusConflict
                ? Span.raw("\uFE0F On conflict:").bold().cyan()
                : Span.raw("\uFE0F On conflict:").bold(),
            Span.raw("  "),
            radioOption("Skip", state.conflictStrategy() == ConflictStrategy.SKIP),
            Span.raw("  "),
            radioOption("Overwrite", state.conflictStrategy() == ConflictStrategy.OVERWRITE)
        ));
        lines.add(Line.from(Span.raw("")));

        // Option 3: Migration tool
        boolean focusMigration = active && state.optionFocusedIndex() == 3;
        String migrationPointer = focusMigration ? " \u25B6 " : "   ";
        lines.add(Line.from(
            focusMigration ? Span.raw(migrationPointer).cyan() : Span.raw(migrationPointer),
            focusMigration
                ? Span.raw("\uD83D\uDDC3 Migration:").bold().cyan()
                : Span.raw("\uD83D\uDDC3 Migration:").bold(),
            Span.raw("    "),
            radioOption("Liquibase", state.migrationChoice() == MigrationChoice.LIQUIBASE),
            Span.raw("  "),
            radioOption("Flyway", state.migrationChoice() == MigrationChoice.FLYWAY),
            Span.raw("  "),
            radioOption("None", state.migrationChoice() == MigrationChoice.NONE)
        ));

        Color borderColor = active ? Color.BLUE : Color.DARK_GRAY;
        Span panelIndicator = active
            ? Span.raw(" [2] ").bold().blue()
            : Span.raw(" [2] ").dim();

        Paragraph options = Paragraph.builder()
            .text(Text.from(lines.toArray(Line[]::new)))
            .block(Block.builder()
                .borders(Borders.ALL)
                .borderType(BorderType.ROUNDED)
                .borderStyle(Style.EMPTY.fg(borderColor))
                .title(Title.from(Line.from(
                    panelIndicator,
                    Span.raw("\uD83D\uDEE0 Options ").bold()
                )))
                .build())
            .build();

        frame.renderWidget(options, area);
    }

    private static void renderFooter(Frame frame, Rect area) {
        List<Rect> footerLayout = Layout.horizontal()
            .constraints(
                Constraint.fill(),
                Constraint.length(22)
            )
            .split(area);

        Line leftLine = Line.from(
            Span.raw(" [\u2191\u2193]").bold().yellow(),
            Span.raw(" Nav ").dim(),
            Span.raw("[Space/Enter]").bold().yellow(),
            Span.raw(" Toggle ").dim(),
            Span.raw("[1]").bold().yellow(),
            Span.raw(" Layers ").dim(),
            Span.raw("[2]").bold().yellow(),
            Span.raw(" Options ").dim(),
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

    private static Span radioOption(String label, boolean selected) {
        return selected
            ? Span.raw("\u25C9 " + label).green()
            : Span.raw("\u25CB " + label).dim();
    }

    private static String formatLayerName(Layer layer) {
        return switch (layer) {
            case DTO_REQUEST -> "DTO (Request)";
            case DTO_RESPONSE -> "DTO (Response)";
            case MAPPER -> "Mapper";
            case REPOSITORY -> "Repository";
            case SERVICE -> "Service Interface";
            case SERVICE_IMPL -> "ServiceImpl";
            case CONTROLLER -> "Controller";
            case FILE_UPLOAD -> "File Upload";
            case LIQUIBASE -> "Liquibase Migration";
            case FLYWAY -> "Flyway Migration";
        };
    }
}
