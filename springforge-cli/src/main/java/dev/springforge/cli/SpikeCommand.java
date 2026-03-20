package dev.springforge.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.picocli.TuiMixin;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.TuiRunner;
import dev.tamboui.tui.event.Event;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.paragraph.Paragraph;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Week 1 spike: proves TamboUI + Picocli + JavaParser work together,
 * including in a GraalVM native binary.
 */
@Command(
    name = "springforge",
    description = "SpringForge TUI — GraalVM + TamboUI + Picocli + JavaParser spike",
    mixinStandardHelpOptions = true,
    version = "0.1.0-SNAPSHOT"
)
public class SpikeCommand implements Callable<Integer> {

    @Parameters(
        index = "0",
        description = "Path to a Java @Entity file to parse",
        arity = "0..1"
    )
    private Path entityFile;

    @Option(
        names = {"--no-tui"},
        description = "Run in plain CLI mode (no interactive TUI)",
        defaultValue = "false"
    )
    private boolean noTui;

    @Mixin
    private TuiMixin tuiOptions;

    private EntityInfo parsedEntity;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new SpikeCommand()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        if (entityFile != null) {
            parsedEntity = parseEntity(entityFile);
        }

        if (noTui) {
            return runPlainCli();
        }
        return runTui();
    }

    private int runTui() throws Exception {
        try (TuiRunner runner = TuiRunner.create(tuiOptions.toConfig())) {
            runner.run(this::handleEvent, this::render);
            return 0;
        }
    }

    private boolean handleEvent(Event event, TuiRunner runner) {
        if (event instanceof KeyEvent keyEvent && keyEvent.isQuit()) {
            runner.quit();
            return false;
        }
        return false;
    }

    private void render(Frame frame) {
        Rect area = frame.area();

        List<Rect> layout = Layout.vertical()
            .constraints(
                Constraint.length(3),
                Constraint.fill(),
                Constraint.length(3)
            )
            .split(area);

        renderHeader(frame, layout.get(0));
        renderContent(frame, layout.get(1));
        renderFooter(frame, layout.get(2));
    }

    private void renderHeader(Frame frame, Rect area) {
        Block header = Block.builder()
            .borders(Borders.ALL)
            .borderType(BorderType.ROUNDED)
            .borderStyle(Style.EMPTY.fg(Color.CYAN))
            .title(Title.from(
                Line.from(
                    Span.raw(" SpringForge ").bold().cyan(),
                    Span.raw("TUI ").bold().yellow(),
                    Span.raw("— GraalVM Spike ").white()
                )
            ).centered())
            .build();

        frame.renderWidget(header, area);
    }

    private void renderContent(Frame frame, Rect area) {
        Text content;
        if (parsedEntity != null) {
            content = buildEntityText(parsedEntity);
        } else {
            content = Text.from(
                Line.from(Span.raw("No entity file provided.").dim()),
                Line.from(Span.raw("")),
                Line.from(
                    Span.raw("Usage: ").bold(),
                    Span.raw("springforge <Entity.java>").cyan()
                ),
                Line.from(Span.raw("")),
                Line.from(Span.raw("Spike validated:").bold().green()),
                Line.from(Span.raw("  + ").green(), Span.raw("Picocli CLI framework")),
                Line.from(Span.raw("  + ").green(), Span.raw("TamboUI terminal UI")),
                Line.from(Span.raw("  + ").green(), Span.raw("JavaParser AST (ready)"))
            );
        }

        Paragraph paragraph = Paragraph.builder()
            .text(content)
            .block(Block.builder()
                .borders(Borders.ALL)
                .borderType(BorderType.ROUNDED)
                .borderStyle(Style.EMPTY.fg(Color.GREEN))
                .title("Entity Details")
                .build())
            .build();

        frame.renderWidget(paragraph, area);
    }

    private void renderFooter(Frame frame, Rect area) {
        Line helpLine = Line.from(
            Span.raw(" q/Ctrl+C").bold().yellow(),
            Span.raw(" Quit  ").dim(),
            Span.raw("--help").bold().yellow(),
            Span.raw(" CLI help  ").dim(),
            Span.raw("--no-tui").bold().yellow(),
            Span.raw(" Plain mode").dim()
        );

        Paragraph footer = Paragraph.builder()
            .text(Text.from(helpLine))
            .block(Block.builder()
                .borders(Borders.ALL)
                .borderType(BorderType.ROUNDED)
                .borderStyle(Style.EMPTY.fg(Color.DARK_GRAY))
                .build())
            .build();

        frame.renderWidget(footer, area);
    }

    private int runPlainCli() {
        System.out.println("SpringForge TUI — GraalVM Spike (plain mode)");
        System.out.println("=============================================");

        if (parsedEntity == null) {
            System.out.println("No entity file provided.");
            System.out.println();
            System.out.println("Spike validated:");
            System.out.println("  + Picocli CLI framework");
            System.out.println("  + JavaParser AST (ready)");
            System.out.println("  + TamboUI (available, use without --no-tui)");
            return 0;
        }

        System.out.println("Entity: " + parsedEntity.className());
        System.out.println("Package: " + parsedEntity.packageName());
        System.out.println("Has @Entity: " + parsedEntity.hasEntityAnnotation());
        System.out.println("Fields:");
        for (FieldInfo field : parsedEntity.fields()) {
            System.out.println("  - " + field.type() + " " + field.name()
                + (field.isId() ? " (@Id)" : ""));
        }
        return 0;
    }

    static EntityInfo parseEntity(Path javaFile) throws Exception {
        if (!Files.exists(javaFile)) {
            throw new IllegalArgumentException("File not found: " + javaFile);
        }

        // Disable language-level validation to avoid MetaModel reflection
        // in GraalVM native-image. The MetaModel accesses AST node fields
        // reflectively, requiring extensive reflect-config.json entries.
        StaticJavaParser.getParserConfiguration()
            .setLanguageLevel(ParserConfiguration.LanguageLevel.RAW);

        CompilationUnit cu = StaticJavaParser.parse(javaFile);

        ClassOrInterfaceDeclaration clazz = cu.findFirst(ClassOrInterfaceDeclaration.class)
            .orElseThrow(() -> new IllegalArgumentException(
                "No class found in " + javaFile));

        String packageName = cu.getPackageDeclaration()
            .map(pd -> pd.getNameAsString())
            .orElse("");

        boolean hasEntity = clazz.getAnnotations().stream()
            .anyMatch(a -> a.getNameAsString().equals("Entity"));

        List<FieldInfo> fields = clazz.getFields().stream()
            .flatMap(field -> field.getVariables().stream()
                .map(var -> toFieldInfo(field, var.getNameAsString(),
                    var.getTypeAsString())))
            .toList();

        return new EntityInfo(
            clazz.getNameAsString(),
            packageName,
            hasEntity,
            fields
        );
    }

    private static FieldInfo toFieldInfo(FieldDeclaration field,
            String name, String type) {
        boolean isId = field.getAnnotations().stream()
            .anyMatch(a -> a.getNameAsString().equals("Id"));
        return new FieldInfo(name, type, isId);
    }

    private Text buildEntityText(EntityInfo entity) {
        var builder = new java.util.ArrayList<Line>();
        builder.add(Line.from(
            Span.raw("Class: ").bold(),
            Span.raw(entity.className()).cyan()
        ));
        builder.add(Line.from(
            Span.raw("Package: ").bold(),
            Span.raw(entity.packageName()).yellow()
        ));
        builder.add(Line.from(
            Span.raw("@Entity: ").bold(),
            entity.hasEntityAnnotation()
                ? Span.raw("yes").green()
                : Span.raw("no").red()
        ));
        builder.add(Line.from(Span.raw("")));
        builder.add(Line.from(Span.raw("Fields:").bold()));
        for (FieldInfo field : entity.fields()) {
            String prefix = field.isId() ? "  > " : "  - ";
            builder.add(Line.from(
                Span.raw(prefix),
                Span.raw(field.type()).cyan(),
                Span.raw(" "),
                Span.raw(field.name()).white(),
                field.isId() ? Span.raw(" (@Id)").yellow() : Span.raw("")
            ));
        }
        builder.add(Line.from(Span.raw("")));
        builder.add(Line.from(Span.raw("Spike validated:").bold().green()));
        builder.add(Line.from(Span.raw("  + ").green(), Span.raw("Picocli — CLI parsed")));
        builder.add(Line.from(Span.raw("  + ").green(), Span.raw("JavaParser — entity parsed")));
        builder.add(Line.from(Span.raw("  + ").green(), Span.raw("TamboUI — rendering TUI")));

        return Text.from(builder.toArray(Line[]::new));
    }

    record EntityInfo(
        String className,
        String packageName,
        boolean hasEntityAnnotation,
        List<FieldInfo> fields
    ) {}

    record FieldInfo(
        String name,
        String type,
        boolean isId
    ) {}
}
