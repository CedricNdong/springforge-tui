package dev.springforge.it;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;

import dev.springforge.engine.model.ConflictStrategy;
import dev.springforge.engine.model.EntityDescriptor;
import dev.springforge.engine.model.GeneratedFile;
import dev.springforge.engine.model.GenerationConfig;
import dev.springforge.engine.model.GenerationReport;
import dev.springforge.engine.model.Layer;
import dev.springforge.engine.model.MapperLib;
import dev.springforge.engine.model.SpringVersion;
import dev.springforge.engine.parser.JavaAstEntityParser;
import dev.springforge.engine.renderer.BatchGenerator;
import dev.springforge.engine.renderer.TemplateRenderer;
import dev.springforge.engine.scanner.EntityScanner;
import dev.springforge.engine.writer.CodeFileWriter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E test that verifies the full SpringForge pipeline:
 * scan → parse → generate → write.
 *
 * <p>Uses the reference entities (User, Product, Order) from this module's
 * source directory. Generated code compilation is verified implicitly by
 * the Gradle {@code compileJava} task depending on {@code generateSpringForgeCode}.
 */
class GeneratedCodeCompilationTest {

    private static final Path ENTITY_DIR = Path.of("src/main/java/com/example/model");

    @TempDir
    Path outputDir;

    private EntityScanner scanner;
    private JavaAstEntityParser parser;
    private TemplateRenderer templateRenderer;
    private BatchGenerator batchGenerator;
    private CodeFileWriter writer;

    @BeforeEach
    void setUp() {
        scanner = new EntityScanner();
        parser = new JavaAstEntityParser();
        templateRenderer = new TemplateRenderer();
        batchGenerator = new BatchGenerator(templateRenderer);
        writer = new CodeFileWriter();
    }

    @Test
    @DisplayName("should scan and find all 3 reference entities")
    void shouldScanReferenceEntities() throws Exception {
        List<Path> entityFiles = scanner.scanForEntityFiles(ENTITY_DIR);

        assertThat(entityFiles).hasSize(3);
        assertThat(entityFiles.stream().map(p -> p.getFileName().toString()))
            .containsExactlyInAnyOrder("User.java", "Product.java", "Order.java");
    }

    @Test
    @DisplayName("should parse entities with correct fields and relationships")
    void shouldParseEntitiesCorrectly() throws Exception {
        List<Path> entityFiles = scanner.scanForEntityFiles(ENTITY_DIR);
        List<EntityDescriptor> entities = entityFiles.stream()
            .map(f -> {
                try {
                    return parser.parse(f);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            })
            .toList();

        entities = parser.resolveCircularRefs(entities);

        assertThat(entities).hasSize(3);

        EntityDescriptor user = entities.stream()
            .filter(e -> e.className().equals("User"))
            .findFirst().orElseThrow();
        assertThat(user.idFieldType()).isEqualTo("Long");
        assertThat(user.packageName()).isEqualTo("com.example.model");

        EntityDescriptor product = entities.stream()
            .filter(e -> e.className().equals("Product"))
            .findFirst().orElseThrow();
        assertThat(product.fields()).hasSizeGreaterThanOrEqualTo(5);

        EntityDescriptor order = entities.stream()
            .filter(e -> e.className().equals("Order"))
            .findFirst().orElseThrow();
        assertThat(order.fields().stream()
            .anyMatch(f -> f.name().equals("user")))
            .isTrue();
    }

    @Test
    @DisplayName("should generate all layers for all entities")
    void shouldGenerateAllLayers() throws Exception {
        List<EntityDescriptor> entities = parseAllEntities();

        GenerationConfig config = new GenerationConfig(
            entities,
            EnumSet.of(
                Layer.DTO_REQUEST, Layer.DTO_RESPONSE,
                Layer.MAPPER, Layer.REPOSITORY,
                Layer.SERVICE, Layer.SERVICE_IMPL,
                Layer.CONTROLLER
            ),
            SpringVersion.V3,
            MapperLib.MAPSTRUCT,
            ConflictStrategy.OVERWRITE,
            outputDir,
            "com.example",
            false,
            false
        );

        List<GeneratedFile> files = batchGenerator.generateAll(config);

        // 3 entities × 7 layers = 21 files
        assertThat(files).hasSize(21);
        assertThat(files.stream().map(f -> f.layer()).distinct())
            .containsExactlyInAnyOrder(
                Layer.DTO_REQUEST, Layer.DTO_RESPONSE,
                Layer.MAPPER, Layer.REPOSITORY,
                Layer.SERVICE, Layer.SERVICE_IMPL,
                Layer.CONTROLLER
            );
    }

    @Test
    @DisplayName("should write all generated files to output directory")
    void shouldWriteAllFiles() throws Exception {
        List<EntityDescriptor> entities = parseAllEntities();

        GenerationConfig config = new GenerationConfig(
            entities,
            EnumSet.of(
                Layer.DTO_REQUEST, Layer.DTO_RESPONSE,
                Layer.MAPPER, Layer.REPOSITORY,
                Layer.SERVICE, Layer.SERVICE_IMPL,
                Layer.CONTROLLER
            ),
            SpringVersion.V3,
            MapperLib.MAPSTRUCT,
            ConflictStrategy.OVERWRITE,
            outputDir,
            "com.example",
            false,
            false
        );

        List<GeneratedFile> files = batchGenerator.generateAll(config);
        GenerationReport report = writer.writeAll(
            files, ConflictStrategy.OVERWRITE, outputDir);

        assertThat(report.totalFiles()).isEqualTo(21);
        assertThat(report.createdFiles()).isEqualTo(21);
        assertThat(report.errorFiles()).isZero();

        // Verify files exist on disk
        assertThat(Files.exists(outputDir.resolve(
            "com/example/dto/UserRequestDto.java"))).isTrue();
        assertThat(Files.exists(outputDir.resolve(
            "com/example/controller/ProductController.java"))).isTrue();
        assertThat(Files.exists(outputDir.resolve(
            "com/example/service/impl/OrderServiceImpl.java"))).isTrue();
    }

    @Test
    @DisplayName("should generate valid Java source files with correct package declarations")
    void shouldGenerateValidPackageDeclarations() throws Exception {
        List<EntityDescriptor> entities = parseAllEntities();

        GenerationConfig config = new GenerationConfig(
            entities,
            EnumSet.of(Layer.DTO_REQUEST, Layer.CONTROLLER),
            SpringVersion.V3,
            MapperLib.MAPSTRUCT,
            ConflictStrategy.OVERWRITE,
            outputDir,
            "com.example",
            false,
            false
        );

        List<GeneratedFile> files = batchGenerator.generateAll(config);
        writer.writeAll(files, ConflictStrategy.OVERWRITE, outputDir);

        String dtoContent = Files.readString(
            outputDir.resolve("com/example/dto/ProductRequestDto.java"));
        assertThat(dtoContent).contains("package com.example.dto;");
        assertThat(dtoContent).contains("import java.math.BigDecimal;");
        assertThat(dtoContent).contains("private BigDecimal price;");

        String controllerContent = Files.readString(
            outputDir.resolve("com/example/controller/UserController.java"));
        assertThat(controllerContent).contains("package com.example.controller;");
        assertThat(controllerContent).contains("@RestController");
        assertThat(controllerContent).contains("@RequestMapping");
    }

    private List<EntityDescriptor> parseAllEntities() throws Exception {
        List<Path> entityFiles = scanner.scanForEntityFiles(ENTITY_DIR);
        List<EntityDescriptor> entities = entityFiles.stream()
            .map(f -> {
                try {
                    return parser.parse(f);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            })
            .toList();
        return parser.resolveCircularRefs(entities);
    }
}
