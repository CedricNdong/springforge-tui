package dev.springforge.it;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;

import dev.springforge.engine.model.ConflictStrategy;
import dev.springforge.engine.model.EntityDescriptor;
import dev.springforge.engine.model.GeneratedFile;
import dev.springforge.engine.model.GenerationConfig;
import dev.springforge.engine.model.Layer;
import dev.springforge.engine.model.MapperLib;
import dev.springforge.engine.model.SpringVersion;
import dev.springforge.engine.parser.JavaAstEntityParser;
import dev.springforge.engine.renderer.BatchGenerator;
import dev.springforge.engine.renderer.TemplateRenderer;
import dev.springforge.engine.writer.CodeFileWriter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Edge-case integration tests covering scenarios beyond the standard
 * reference entities: Lombok, Spring Boot 2 namespace, circular
 * relationships, complex field types, and ModelMapper configuration.
 */
class EdgeCaseIntegrationTest {

    @TempDir
    Path tempDir;

    @TempDir
    Path outputDir;

    private JavaAstEntityParser parser;
    private BatchGenerator batchGenerator;
    private CodeFileWriter writer;

    @BeforeEach
    void setUp() {
        parser = new JavaAstEntityParser();
        batchGenerator = new BatchGenerator(new TemplateRenderer());
        writer = new CodeFileWriter();
    }

    @Test
    @DisplayName("should generate valid code for Lombok-annotated entity")
    void shouldHandleLombokEntity() throws Exception {
        Path entityFile = createEntityFile("LombokEntity.java", """
            package com.test.model;

            import jakarta.persistence.Entity;
            import jakarta.persistence.Id;
            import jakarta.persistence.GeneratedValue;
            import jakarta.persistence.GenerationType;
            import lombok.Data;
            import lombok.Builder;
            import lombok.NoArgsConstructor;
            import lombok.AllArgsConstructor;

            @Entity
            @Data
            @Builder
            @NoArgsConstructor
            @AllArgsConstructor
            public class LombokEntity {
                @Id
                @GeneratedValue(strategy = GenerationType.IDENTITY)
                private Long id;
                private String name;
                private String description;
            }
            """);

        EntityDescriptor entity = parser.parse(entityFile);
        assertThat(entity.hasLombok()).isTrue();

        List<GeneratedFile> files = generateAndWrite(
            List.of(entity), SpringVersion.V3, MapperLib.MAPSTRUCT);

        GeneratedFile requestDto = findFile(files, Layer.DTO_REQUEST);
        assertThat(requestDto.content()).contains("@Data");
        assertThat(requestDto.content()).contains("@Builder");
        assertThat(requestDto.content()).doesNotContain("public String getName()");
    }

    @Test
    @DisplayName("should generate javax namespace for Spring Boot 2")
    void shouldGenerateJavaxNamespace() throws Exception {
        Path entityFile = createEntityFile("Sb2Entity.java", """
            package com.test.model;

            import jakarta.persistence.Entity;
            import jakarta.persistence.Id;

            @Entity
            public class Sb2Entity {
                @Id
                private Long id;
                private String value;
            }
            """);

        EntityDescriptor entity = parser.parse(entityFile);

        List<GeneratedFile> files = generateAndWrite(
            List.of(entity), SpringVersion.V2, MapperLib.MAPSTRUCT);

        GeneratedFile requestDto = findFile(files, Layer.DTO_REQUEST);
        assertThat(requestDto.content()).contains("javax.validation");

        GeneratedFile controller = findFile(files, Layer.CONTROLLER);
        assertThat(controller.content()).contains("javax.validation");
    }

    @Test
    @DisplayName("should flatten circular references to ID fields in DTOs")
    void shouldFlattenCircularReferences() throws Exception {
        Path parentFile = createEntityFile("Parent.java", """
            package com.test.model;

            import java.util.List;
            import jakarta.persistence.Entity;
            import jakarta.persistence.Id;
            import jakarta.persistence.OneToMany;

            @Entity
            public class Parent {
                @Id
                private Long id;
                private String name;
                @OneToMany(mappedBy = "parent")
                private List<Child> children;
            }
            """);

        Path childFile = createEntityFile("Child.java", """
            package com.test.model;

            import jakarta.persistence.Entity;
            import jakarta.persistence.Id;
            import jakarta.persistence.ManyToOne;
            import jakarta.persistence.JoinColumn;

            @Entity
            public class Child {
                @Id
                private Long id;
                private String label;
                @ManyToOne
                @JoinColumn(name = "parent_id")
                private Parent parent;
            }
            """);

        EntityDescriptor parentEntity = parser.parse(parentFile);
        EntityDescriptor childEntity = parser.parse(childFile);
        List<EntityDescriptor> resolved = parser.resolveCircularRefs(
            List.of(parentEntity, childEntity));

        List<GeneratedFile> files = generateAndWrite(
            resolved, SpringVersion.V3, MapperLib.MAPSTRUCT);

        // Parent's ResponseDto should flatten children to Long (circular ref)
        GeneratedFile parentResponse = files.stream()
            .filter(f -> f.entityName().equals("Parent")
                && f.layer() == Layer.DTO_RESPONSE)
            .findFirst().orElseThrow();
        assertThat(parentResponse.content()).contains("Long childrenId");

        // Child's ResponseDto should flatten parent to Long (circular ref)
        GeneratedFile childResponse = files.stream()
            .filter(f -> f.entityName().equals("Child")
                && f.layer() == Layer.DTO_RESPONSE)
            .findFirst().orElseThrow();
        assertThat(childResponse.content()).contains("Long parentId");
    }

    @Test
    @DisplayName("should handle entity with complex field types")
    void shouldHandleComplexFieldTypes() throws Exception {
        Path entityFile = createEntityFile("ComplexEntity.java", """
            package com.test.model;

            import java.math.BigDecimal;
            import java.time.LocalDate;
            import java.time.LocalDateTime;
            import java.util.UUID;
            import jakarta.persistence.Entity;
            import jakarta.persistence.Id;

            @Entity
            public class ComplexEntity {
                @Id
                private Long id;
                private BigDecimal price;
                private LocalDate birthDate;
                private LocalDateTime createdAt;
                private UUID externalRef;
                private Boolean active;
            }
            """);

        EntityDescriptor entity = parser.parse(entityFile);

        List<GeneratedFile> files = generateAndWrite(
            List.of(entity), SpringVersion.V3, MapperLib.MAPSTRUCT);

        GeneratedFile dto = findFile(files, Layer.DTO_REQUEST);
        assertThat(dto.content()).contains("import java.math.BigDecimal;");
        assertThat(dto.content()).contains("import java.time.LocalDate;");
        assertThat(dto.content()).contains("import java.time.LocalDateTime;");
        assertThat(dto.content()).contains("import java.util.UUID;");
        assertThat(dto.content()).contains("private BigDecimal price;");
        assertThat(dto.content()).contains("private UUID externalRef;");
    }

    @Test
    @DisplayName("should generate ModelMapper configuration instead of MapStruct")
    void shouldGenerateModelMapperConfig() throws Exception {
        Path entityFile = createEntityFile("MmEntity.java", """
            package com.test.model;

            import jakarta.persistence.Entity;
            import jakarta.persistence.Id;

            @Entity
            public class MmEntity {
                @Id
                private Long id;
                private String title;
            }
            """);

        EntityDescriptor entity = parser.parse(entityFile);

        List<GeneratedFile> files = generateAndWrite(
            List.of(entity), SpringVersion.V3, MapperLib.MODEL_MAPPER);

        GeneratedFile mapper = findFile(files, Layer.MAPPER);
        assertThat(mapper.content()).contains("ModelMapper");
        assertThat(mapper.content()).doesNotContain("@Mapper");
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private Path createEntityFile(String name, String content) throws Exception {
        Path file = tempDir.resolve(name);
        Files.writeString(file, content);
        return file;
    }

    private List<GeneratedFile> generateAndWrite(
            List<EntityDescriptor> entities,
            SpringVersion springVersion,
            MapperLib mapperLib) throws Exception {
        GenerationConfig config = new GenerationConfig(
            entities,
            EnumSet.of(
                Layer.DTO_REQUEST, Layer.DTO_RESPONSE,
                Layer.MAPPER, Layer.REPOSITORY,
                Layer.SERVICE, Layer.SERVICE_IMPL,
                Layer.CONTROLLER
            ),
            springVersion,
            mapperLib,
            ConflictStrategy.OVERWRITE,
            outputDir,
            "com.test",
            false,
            false
        );
        List<GeneratedFile> files = batchGenerator.generateAll(config);
        writer.writeAll(files, ConflictStrategy.OVERWRITE, outputDir);
        return files;
    }

    private GeneratedFile findFile(List<GeneratedFile> files, Layer layer) {
        return files.stream()
            .filter(f -> f.layer() == layer)
            .findFirst()
            .orElseThrow(() -> new AssertionError(
                "No file found for layer: " + layer));
    }
}
