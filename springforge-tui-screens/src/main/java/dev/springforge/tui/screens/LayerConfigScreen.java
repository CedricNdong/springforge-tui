package dev.springforge.tui.screens;

import java.util.ArrayList;
import java.util.List;

import dev.springforge.engine.model.ConflictStrategy;
import dev.springforge.engine.model.Layer;
import dev.springforge.engine.model.MapperLib;
import dev.springforge.engine.model.SpringVersion;
import dev.springforge.tui.state.LayerConfigState;

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
 * Checkboxes for all generation layers and radio groups for options.
 */
public final class LayerConfigScreen {

    private LayerConfigScreen() {}

    private static final List<Layer> LAYER_ORDER = List.of(
        Layer.DTO_REQUEST, Layer.DTO_RESPONSE, Layer.MAPPER,
        Layer.REPOSITORY, Layer.SERVICE, Layer.SERVICE_IMPL,
        Layer.CONTROLLER, Layer.FILE_UPLOAD, Layer.LIQUIBASE, Layer.FLYWAY
    );

    public static void render(Frame frame, Rect area, LayerConfigState state) {
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
                Constraint.percentage(45),
                Constraint.percentage(55)
            )
            .split(mainLayout.get(1));

        renderLayerList(frame, contentLayout.get(0), state);
        renderOptions(frame, contentLayout.get(1), state);
        renderFooter(frame, mainLayout.get(2), state);
    }

    private static void renderHeader(Frame frame, Rect area) {
        Block header = Block.builder()
            .borders(Borders.ALL)
            .borderType(BorderType.ROUNDED)
            .borderStyle(Style.EMPTY.fg(Color.CYAN))
            .title(Title.from(Line.from(
                Span.raw(" SpringForge ").bold().cyan(),
                Span.raw("— Layer Configuration ").white()
            )))
            .build();

        frame.renderWidget(header, area);
    }

    private static void renderLayerList(Frame frame, Rect area, LayerConfigState state) {
        List<Line> lines = new ArrayList<>();

        for (int i = 0; i < LAYER_ORDER.size(); i++) {
            Layer layer = LAYER_ORDER.get(i);
            boolean selected = state.selectedLayers().contains(layer);
            boolean focused = (i == state.focusedIndex());

            String checkbox = selected ? "[x] " : "[ ] ";
            String label = formatLayerName(layer);

            Span checkSpan = selected
                ? Span.raw(checkbox).green()
                : Span.raw(checkbox).dim();

            Span labelSpan = focused
                ? Span.raw(label).bold().cyan()
                : Span.raw(label).white();

            lines.add(Line.from(checkSpan, labelSpan));
        }

        Paragraph list = Paragraph.builder()
            .text(Text.from(lines.toArray(Line[]::new)))
            .block(Block.builder()
                .borders(Borders.ALL)
                .borderType(BorderType.ROUNDED)
                .borderStyle(Style.EMPTY.fg(Color.GREEN))
                .title("Layers to Generate")
                .build())
            .build();

        frame.renderWidget(list, area);
    }

    private static void renderOptions(Frame frame, Rect area, LayerConfigState state) {
        List<Line> lines = new ArrayList<>();

        lines.add(Line.from(Span.raw("Spring Boot:  ").bold(),
            radioOption("3.x", state.springVersion() == SpringVersion.V3),
            Span.raw("  "),
            radioOption("2.x", state.springVersion() == SpringVersion.V2)
        ));
        lines.add(Line.from(Span.raw("")));

        lines.add(Line.from(Span.raw("Mapper:       ").bold(),
            radioOption("MapStruct", state.mapperLib() == MapperLib.MAPSTRUCT),
            Span.raw("  "),
            radioOption("ModelMapper", state.mapperLib() == MapperLib.MODEL_MAPPER)
        ));
        lines.add(Line.from(Span.raw("")));

        lines.add(Line.from(Span.raw("On conflict:  ").bold(),
            radioOption("Skip", state.conflictStrategy() == ConflictStrategy.SKIP),
            Span.raw("  "),
            radioOption("Overwrite", state.conflictStrategy() == ConflictStrategy.OVERWRITE)
        ));

        Paragraph options = Paragraph.builder()
            .text(Text.from(lines.toArray(Line[]::new)))
            .block(Block.builder()
                .borders(Borders.ALL)
                .borderType(BorderType.ROUNDED)
                .borderStyle(Style.EMPTY.fg(Color.BLUE))
                .title("Options")
                .build())
            .build();

        frame.renderWidget(options, area);
    }

    private static void renderFooter(Frame frame, Rect area, LayerConfigState state) {
        Line footer = Line.from(
            Span.raw(" Entities: ").bold(),
            Span.raw(String.valueOf(state.entityCount())).cyan(),
            Span.raw(" | Layers: ").bold(),
            Span.raw(String.valueOf(state.selectedLayers().size())).cyan(),
            Span.raw(" | Files: ~").bold(),
            Span.raw(String.valueOf(state.estimatedFileCount())).cyan(),
            Span.raw("   "),
            Span.raw("[←]").bold().yellow(),
            Span.raw(" Back  ").dim(),
            Span.raw("[Tab]").bold().yellow(),
            Span.raw(" Preview  ").dim(),
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

    private static Span radioOption(String label, boolean selected) {
        return selected
            ? Span.raw("● " + label).green()
            : Span.raw("○ " + label).dim();
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
