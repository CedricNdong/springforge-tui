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
import dev.springforge.engine.scanner.EntityScanner;
import dev.springforge.engine.writer.CodeFileWriter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Edge-case integration tests covering scenarios that real-world projects
 * commonly encounter. Uses ad-hoc entity source files written to temp dirs.
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

    // ── Lombok Detection ────────────────────────────────────────────

    @Nested
    @DisplayName("Lombok detection")
    class LombokDetectionTest {

        @Test
        @DisplayName("should detect @Data as Lombok")
        void shouldDetectDataAnnotation() throws Exception {
            Path file = writeEntity("DataEntity.java", """
                package com.test.model;
                import jakarta.persistence.Entity;
                import jakarta.persistence.Id;
                import lombok.Data;

                @Entity
                @Data
                public class DataEntity {
                    @Id private Long id;
                    private String value;
                }
                """);

            EntityDescriptor entity = parser.parse(file);
            assertThat(entity.hasLombok()).isTrue();

            List<GeneratedFile> files = generateAll(List.of(entity));
            GeneratedFile dto = findFile(files, Layer.DTO_REQUEST, "DataEntity");
            assertThat(dto.content()).contains("@Data");
            assertThat(dto.content()).doesNotContain("getValue()");
        }

        @Test
        @DisplayName("should detect @Builder as Lombok")
        void shouldDetectBuilderAnnotation() throws Exception {
            Path file = writeEntity("BuilderEntity.java", """
                package com.test.model;
                import jakarta.persistence.Entity;
                import jakarta.persistence.Id;
                import lombok.Builder;
                import lombok.Data;

                @Entity
                @Data
                @Builder
                public class BuilderEntity {
                    @Id private Long id;
                    private String title;
                }
                """);

            EntityDescriptor entity = parser.parse(file);
            assertThat(entity.hasLombok()).isTrue();

            List<GeneratedFile> files = generateAll(List.of(entity));
            GeneratedFile dto = findFile(files, Layer.DTO_REQUEST, "BuilderEntity");
            assertThat(dto.content()).contains("@Builder");
        }

        @Test
        @DisplayName("should generate getters/setters when no Lombok")
        void shouldGenerateAccessorsWithoutLombok() throws Exception {
            Path file = writeEntity("PlainEntity.java", """
                package com.test.model;
                import jakarta.persistence.Entity;
                import jakarta.persistence.Id;

                @Entity
                public class PlainEntity {
                    @Id private Long id;
                    private String description;
                    private int count;
                }
                """);

            EntityDescriptor entity = parser.parse(file);
            assertThat(entity.hasLombok()).isFalse();

            List<GeneratedFile> files = generateAll(List.of(entity));
            GeneratedFile dto = findFile(files, Layer.DTO_RESPONSE, "PlainEntity");
            assertThat(dto.content()).doesNotContain("@Data");
            assertThat(dto.content()).contains("getDescription()");
            assertThat(dto.content()).contains("setDescription(");
            assertThat(dto.content()).contains("getCount()");
        }
    }

    // ── Spring Boot 2 Namespace ─────────────────────────────────────

    @Nested
    @DisplayName("Spring Boot 2 namespace")
    class SpringBoot2Test {

        @Test
        @DisplayName("should use javax.validation in RequestDto for SB2")
        void shouldUseJavaxInRequestDto() throws Exception {
            EntityDescriptor entity = parseSimpleEntity("Sb2Dto.java");
            List<GeneratedFile> files = generateAll(
                List.of(entity), SpringVersion.V2, MapperLib.MAPSTRUCT);

            GeneratedFile dto = findFile(files, Layer.DTO_REQUEST, "Sb2Dto");
            assertThat(dto.content()).contains("javax.validation");
            assertThat(dto.content()).doesNotContain("jakarta");
        }

        @Test
        @DisplayName("should use javax.validation in Controller for SB2")
        void shouldUseJavaxInController() throws Exception {
            EntityDescriptor entity = parseSimpleEntity("Sb2Ctrl.java");
            List<GeneratedFile> files = generateAll(
                List.of(entity), SpringVersion.V2, MapperLib.MAPSTRUCT);

            GeneratedFile controller = findFile(files, Layer.CONTROLLER, "Sb2Ctrl");
            assertThat(controller.content()).contains("javax.validation");
        }
    }

    // ── Circular References ─────────────────────────────────────────

    @Nested
    @DisplayName("Circular reference handling")
    class CircularRefTest {

        @Test
        @DisplayName("should flatten bidirectional @OneToMany/@ManyToOne circular refs")
        void shouldFlattenBidirectionalCircularRefs() throws Exception {
            Path parentFile = writeEntity("Department.java", """
                package com.test.model;
                import java.util.List;
                import jakarta.persistence.Entity;
                import jakarta.persistence.Id;
                import jakarta.persistence.OneToMany;

                @Entity
                public class Department {
                    @Id private Long id;
                    private String name;
                    @OneToMany(mappedBy = "department")
                    private List<Employee> employees;
                }
                """);

            Path childFile = writeEntity("Employee.java", """
                package com.test.model;
                import jakarta.persistence.Entity;
                import jakarta.persistence.Id;
                import jakarta.persistence.ManyToOne;
                import jakarta.persistence.JoinColumn;

                @Entity
                public class Employee {
                    @Id private Long id;
                    private String name;
                    @ManyToOne
                    @JoinColumn(name = "department_id")
                    private Department department;
                }
                """);

            EntityDescriptor parent = parser.parse(parentFile);
            EntityDescriptor child = parser.parse(childFile);
            List<EntityDescriptor> resolved = parser.resolveCircularRefs(
                List.of(parent, child));

            List<GeneratedFile> files = generateAll(resolved);

            // Employee ResponseDto: @ManyToOne → Long departmentId with source mapping
            GeneratedFile childDto = findFile(files, Layer.DTO_RESPONSE, "Employee");
            assertThat(childDto.content()).contains("Long departmentId");

            // Employee Mapper: source mapping for department
            GeneratedFile childMapper = findFile(files, Layer.MAPPER, "Employee");
            assertThat(childMapper.content()).contains(
                "@Mapping(source = \"department.id\", target = \"departmentId\")");

            // Department Mapper: ignore employees (collection circular ref)
            GeneratedFile parentMapper = findFile(files, Layer.MAPPER, "Department");
            assertThat(parentMapper.content()).doesNotContain(
                "source = \"employees.id\"");
        }

        @Test
        @DisplayName("should flatten @ManyToOne circular ref to Long in RequestDto")
        void shouldFlattenCircularRefInRequestDto() throws Exception {
            Path parentFile = writeEntity("Author.java", """
                package com.test.model;
                import java.util.List;
                import jakarta.persistence.Entity;
                import jakarta.persistence.Id;
                import jakarta.persistence.OneToMany;

                @Entity
                public class Author {
                    @Id private Long id;
                    private String name;
                    @OneToMany(mappedBy = "author")
                    private List<Book> books;
                }
                """);

            Path childFile = writeEntity("Book.java", """
                package com.test.model;
                import jakarta.persistence.Entity;
                import jakarta.persistence.Id;
                import jakarta.persistence.ManyToOne;
                import jakarta.persistence.JoinColumn;

                @Entity
                public class Book {
                    @Id private Long id;
                    private String title;
                    @ManyToOne
                    @JoinColumn(name = "author_id")
                    private Author author;
                }
                """);

            EntityDescriptor authorEntity = parser.parse(parentFile);
            EntityDescriptor bookEntity = parser.parse(childFile);
            List<EntityDescriptor> resolved = parser.resolveCircularRefs(
                List.of(authorEntity, bookEntity));

            List<GeneratedFile> files = generateAll(resolved);

            GeneratedFile bookRequest = findFile(files, Layer.DTO_REQUEST, "Book");
            assertThat(bookRequest.content()).contains("private Long authorId;");
            assertThat(bookRequest.content()).doesNotContain("private Author author;");
        }
    }

    // ── Complex Field Types ─────────────────────────────────────────

    @Nested
    @DisplayName("Complex field types")
    class ComplexFieldTypesTest {

        @Test
        @DisplayName("should import all complex types (BigDecimal, LocalDate, UUID, etc.)")
        void shouldImportComplexTypes() throws Exception {
            Path file = writeEntity("RichEntity.java", """
                package com.test.model;
                import java.math.BigDecimal;
                import java.time.LocalDate;
                import java.time.LocalDateTime;
                import java.util.UUID;
                import jakarta.persistence.Entity;
                import jakarta.persistence.Id;

                @Entity
                public class RichEntity {
                    @Id private Long id;
                    private BigDecimal amount;
                    private LocalDate startDate;
                    private LocalDateTime createdAt;
                    private UUID externalId;
                    private Boolean enabled;
                }
                """);

            EntityDescriptor entity = parser.parse(file);
            List<GeneratedFile> files = generateAll(List.of(entity));

            GeneratedFile dto = findFile(files, Layer.DTO_REQUEST, "RichEntity");
            assertThat(dto.content()).contains("import java.math.BigDecimal;");
            assertThat(dto.content()).contains("import java.time.LocalDate;");
            assertThat(dto.content()).contains("import java.time.LocalDateTime;");
            assertThat(dto.content()).contains("import java.util.UUID;");
            assertThat(dto.content()).contains("private BigDecimal amount;");
            assertThat(dto.content()).contains("private LocalDate startDate;");
            assertThat(dto.content()).contains("private UUID externalId;");
        }

        @Test
        @DisplayName("should handle primitive types without wrapper imports")
        void shouldHandlePrimitiveTypes() throws Exception {
            Path file = writeEntity("PrimitiveEntity.java", """
                package com.test.model;
                import jakarta.persistence.Entity;
                import jakarta.persistence.Id;

                @Entity
                public class PrimitiveEntity {
                    @Id private Long id;
                    private int count;
                    private boolean active;
                    private double rating;
                }
                """);

            EntityDescriptor entity = parser.parse(file);
            List<GeneratedFile> files = generateAll(List.of(entity));

            GeneratedFile dto = findFile(files, Layer.DTO_REQUEST, "PrimitiveEntity");
            assertThat(dto.content()).contains("private int count;");
            assertThat(dto.content()).contains("private boolean active;");
            assertThat(dto.content()).contains("private double rating;");
        }
    }

    // ── Mapper Library Switching ────────────────────────────────────

    @Nested
    @DisplayName("Mapper library switching")
    class MapperLibTest {

        @Test
        @DisplayName("should generate MapStruct interface mapper")
        void shouldGenerateMapStructMapper() throws Exception {
            EntityDescriptor entity = parseSimpleEntity("MsEntity.java");
            List<GeneratedFile> files = generateAll(
                List.of(entity), SpringVersion.V3, MapperLib.MAPSTRUCT);

            GeneratedFile mapper = findFile(files, Layer.MAPPER, "MsEntity");
            assertThat(mapper.content()).contains("@Mapper(componentModel = \"spring\")");
            assertThat(mapper.content()).contains("interface MsEntityMapper");
            assertThat(mapper.content()).doesNotContain("ModelMapper");
        }

        @Test
        @DisplayName("should generate ModelMapper class mapper")
        void shouldGenerateModelMapperMapper() throws Exception {
            EntityDescriptor entity = parseSimpleEntity("MmEntity.java");
            List<GeneratedFile> files = generateAll(
                List.of(entity), SpringVersion.V3, MapperLib.MODEL_MAPPER);

            GeneratedFile mapper = findFile(files, Layer.MAPPER, "MmEntity");
            assertThat(mapper.content()).contains("class MmEntityMapper");
            assertThat(mapper.content()).contains("ModelMapper");
            assertThat(mapper.content()).doesNotContain("@Mapper(componentModel");
        }
    }

    // ── Multiple Relationships in Same Entity ───────────────────────

    @Nested
    @DisplayName("Multiple relationships")
    class MultipleRelationshipsTest {

        @Test
        @DisplayName("should handle entity with two @ManyToOne fields")
        void shouldHandleTwoManyToOne() throws Exception {
            Path file = writeEntity("Enrollment.java", """
                package com.test.model;
                import jakarta.persistence.Entity;
                import jakarta.persistence.Id;
                import jakarta.persistence.ManyToOne;
                import jakarta.persistence.JoinColumn;

                @Entity
                public class Enrollment {
                    @Id private Long id;
                    private String semester;
                    @ManyToOne
                    @JoinColumn(name = "student_id")
                    private Student student;
                    @ManyToOne
                    @JoinColumn(name = "course_id")
                    private Course course;
                }
                """);

            Path studentFile = writeEntity("Student.java", """
                package com.test.model;
                import jakarta.persistence.Entity;
                import jakarta.persistence.Id;

                @Entity
                public class Student {
                    @Id private Long id;
                    private String name;
                }
                """);

            Path courseFile = writeEntity("Course.java", """
                package com.test.model;
                import jakarta.persistence.Entity;
                import jakarta.persistence.Id;

                @Entity
                public class Course {
                    @Id private Long id;
                    private String title;
                }
                """);

            EntityDescriptor enrollment = parser.parse(file);
            List<GeneratedFile> files = generateAll(List.of(enrollment));

            GeneratedFile requestDto = findFile(files, Layer.DTO_REQUEST, "Enrollment");
            assertThat(requestDto.content()).contains("private Long studentId;");
            assertThat(requestDto.content()).contains("private Long courseId;");
            assertThat(requestDto.content()).contains("private String semester;");

            GeneratedFile mapper = findFile(files, Layer.MAPPER, "Enrollment");
            assertThat(mapper.content()).contains(
                "@Mapping(source = \"student.id\", target = \"studentId\")");
            assertThat(mapper.content()).contains(
                "@Mapping(source = \"course.id\", target = \"courseId\")");
        }

        @Test
        @DisplayName("should handle entity with @ManyToOne + @ManyToMany")
        void shouldHandleMixedRelationships() throws Exception {
            Path file = writeEntity("Playlist.java", """
                package com.test.model;
                import java.util.List;
                import jakarta.persistence.Entity;
                import jakarta.persistence.Id;
                import jakarta.persistence.ManyToOne;
                import jakarta.persistence.ManyToMany;
                import jakarta.persistence.JoinColumn;
                import jakarta.persistence.JoinTable;

                @Entity
                public class Playlist {
                    @Id private Long id;
                    private String name;
                    @ManyToOne
                    @JoinColumn(name = "owner_id")
                    private Owner owner;
                    @ManyToMany
                    @JoinTable(name = "playlist_song",
                        joinColumns = @JoinColumn(name = "playlist_id"),
                        inverseJoinColumns = @JoinColumn(name = "song_id"))
                    private List<Song> songs;
                }
                """);

            EntityDescriptor entity = parser.parse(file);
            List<GeneratedFile> files = generateAll(List.of(entity));

            GeneratedFile requestDto = findFile(files, Layer.DTO_REQUEST, "Playlist");
            assertThat(requestDto.content()).contains("private Long ownerId;");
            assertThat(requestDto.content()).contains("private Long songsId;");

            GeneratedFile responseDto = findFile(files, Layer.DTO_RESPONSE, "Playlist");
            assertThat(responseDto.content()).contains("private Long ownerId;");
        }
    }

    // ── @OneToOne Relationship ───────────────────────────────────────

    @Nested
    @DisplayName("@OneToOne relationship")
    class OneToOneTest {

        @Test
        @DisplayName("should flatten @OneToOne to Long in both DTOs")
        void shouldFlattenOneToOne() throws Exception {
            Path file = writeEntity("Passport.java", """
                package com.test.model;
                import jakarta.persistence.Entity;
                import jakarta.persistence.Id;
                import jakarta.persistence.OneToOne;
                import jakarta.persistence.JoinColumn;

                @Entity
                public class Passport {
                    @Id private Long id;
                    private String number;
                    @OneToOne
                    @JoinColumn(name = "person_id")
                    private Person person;
                }
                """);

            EntityDescriptor entity = parser.parse(file);
            List<GeneratedFile> files = generateAll(List.of(entity));

            GeneratedFile requestDto = findFile(files, Layer.DTO_REQUEST, "Passport");
            assertThat(requestDto.content()).contains("private Long personId;");

            GeneratedFile responseDto = findFile(files, Layer.DTO_RESPONSE, "Passport");
            assertThat(responseDto.content()).contains("private Long personId;");

            GeneratedFile mapper = findFile(files, Layer.MAPPER, "Passport");
            assertThat(mapper.content()).contains(
                "@Mapping(source = \"person.id\", target = \"personId\")");
        }
    }

    // ── Entity with Only Relationships ──────────────────────────────

    @Nested
    @DisplayName("Entity with only relationships")
    class RelationshipOnlyEntityTest {

        @Test
        @DisplayName("should handle entity with id + only relationship fields")
        void shouldHandleRelationshipOnlyEntity() throws Exception {
            Path file = writeEntity("Bookmark.java", """
                package com.test.model;
                import jakarta.persistence.Entity;
                import jakarta.persistence.Id;
                import jakarta.persistence.ManyToOne;
                import jakarta.persistence.JoinColumn;

                @Entity
                public class Bookmark {
                    @Id private Long id;
                    @ManyToOne
                    @JoinColumn(name = "user_id")
                    private User user;
                    @ManyToOne
                    @JoinColumn(name = "article_id")
                    private Article article;
                }
                """);

            EntityDescriptor entity = parser.parse(file);
            List<GeneratedFile> files = generateAll(List.of(entity));

            GeneratedFile requestDto = findFile(files, Layer.DTO_REQUEST, "Bookmark");
            assertThat(requestDto.content()).contains("private Long userId;");
            assertThat(requestDto.content()).contains("private Long articleId;");
        }
    }

    // ── Scanner Edge Cases ──────────────────────────────────────────

    @Nested
    @DisplayName("Scanner edge cases")
    class ScannerEdgeCaseTest {

        @Test
        @DisplayName("should skip non-Java files in scan directory")
        void shouldSkipNonJavaFiles() throws Exception {
            Path scanDir = Files.createDirectory(tempDir.resolve("scantest"));
            Files.writeString(scanDir.resolve("README.md"), "# Not an entity");
            Files.writeString(scanDir.resolve("config.yml"), "key: value");
            Files.writeString(scanDir.resolve("Valid.java"), """
                package com.test.model;
                import jakarta.persistence.Entity;
                import jakarta.persistence.Id;

                @Entity
                public class Valid {
                    @Id private Long id;
                }
                """);

            EntityScanner entityScanner = new EntityScanner();
            List<Path> found = entityScanner.scanForEntityFiles(scanDir);

            assertThat(found).hasSize(1);
            assertThat(found.get(0).getFileName().toString()).isEqualTo("Valid.java");
        }

        @Test
        @DisplayName("should return empty list for directory with no entities")
        void shouldReturnEmptyForNoEntities() throws Exception {
            Path emptyDir = Files.createDirectory(tempDir.resolve("empty"));
            Files.writeString(emptyDir.resolve("Pojo.java"), """
                package com.test;
                public class Pojo {
                    private String name;
                }
                """);

            EntityScanner entityScanner = new EntityScanner();
            List<Path> found = entityScanner.scanForEntityFiles(emptyDir);

            assertThat(found).isEmpty();
        }
    }

    // ── No HTML Escaping ────────────────────────────────────────────

    @Nested
    @DisplayName("Template rendering")
    class TemplateRenderingTest {

        @Test
        @DisplayName("should not HTML-escape generic types like List<T>")
        void shouldNotEscapeGenerics() throws Exception {
            Path parentFile = writeEntity("Library.java", """
                package com.test.model;
                import java.util.List;
                import jakarta.persistence.Entity;
                import jakarta.persistence.Id;
                import jakarta.persistence.OneToMany;

                @Entity
                public class Library {
                    @Id private Long id;
                    private String name;
                    @OneToMany(mappedBy = "library")
                    private List<Volume> volumes;
                }
                """);

            EntityDescriptor entity = parser.parse(parentFile);
            List<GeneratedFile> files = generateAll(List.of(entity));

            GeneratedFile dto = findFile(files, Layer.DTO_RESPONSE, "Library");
            assertThat(dto.content()).doesNotContain("&lt;");
            assertThat(dto.content()).doesNotContain("&gt;");
            assertThat(dto.content()).contains("List<");
        }
    }

    // ── Migration Templates ─────────────────────────────────────────

    @Nested
    @DisplayName("Migration generation")
    class MigrationTest {

        @Test
        @DisplayName("should generate Liquibase changelog with correct column types")
        void shouldGenerateLiquibaseChangelog() throws Exception {
            EntityDescriptor entity = parseSimpleEntity("Invoice.java");
            GenerationConfig config = new GenerationConfig(
                List.of(entity), EnumSet.of(Layer.LIQUIBASE),
                SpringVersion.V3, MapperLib.MAPSTRUCT,
                ConflictStrategy.OVERWRITE, outputDir, "com.test",
                false, false
            );
            List<GeneratedFile> files = batchGenerator.generateAll(config);

            assertThat(files).hasSize(1);
            String content = files.get(0).content();
            assertThat(content).contains("<databaseChangeLog");
            assertThat(content).contains("<createTable tableName=\"invoice\">");
            assertThat(content).contains("<column name=\"id\" type=\"BIGINT\"");
        }

        @Test
        @DisplayName("should generate Flyway SQL migration")
        void shouldGenerateFlywayMigration() throws Exception {
            EntityDescriptor entity = parseSimpleEntity("Receipt.java");
            GenerationConfig config = new GenerationConfig(
                List.of(entity), EnumSet.of(Layer.FLYWAY),
                SpringVersion.V3, MapperLib.MAPSTRUCT,
                ConflictStrategy.OVERWRITE, outputDir, "com.test",
                false, false
            );
            List<GeneratedFile> files = batchGenerator.generateAll(config);

            assertThat(files).hasSize(1);
            String content = files.get(0).content();
            assertThat(content).contains("CREATE TABLE receipt");
            assertThat(content).contains("id BIGINT PRIMARY KEY");
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private Path writeEntity(String name, String content) throws Exception {
        Path file = tempDir.resolve(name);
        Files.writeString(file, content);
        return file;
    }

    private EntityDescriptor parseSimpleEntity(String fileName) throws Exception {
        String className = fileName.replace(".java", "");
        Path file = writeEntity(fileName, """
            package com.test.model;
            import jakarta.persistence.Entity;
            import jakarta.persistence.Id;

            @Entity
            public class %s {
                @Id private Long id;
                private String name;
            }
            """.formatted(className));
        return parser.parse(file);
    }

    private List<GeneratedFile> generateAll(List<EntityDescriptor> entities)
            throws Exception {
        return generateAll(entities, SpringVersion.V3, MapperLib.MAPSTRUCT);
    }

    private List<GeneratedFile> generateAll(
            List<EntityDescriptor> entities,
            SpringVersion version,
            MapperLib mapperLib) throws Exception {
        GenerationConfig config = new GenerationConfig(
            entities,
            EnumSet.of(
                Layer.DTO_REQUEST, Layer.DTO_RESPONSE,
                Layer.MAPPER, Layer.REPOSITORY,
                Layer.SERVICE, Layer.SERVICE_IMPL,
                Layer.CONTROLLER
            ),
            version, mapperLib, ConflictStrategy.OVERWRITE,
            outputDir, "com.test", false, false
        );
        List<GeneratedFile> files = batchGenerator.generateAll(config);
        writer.writeAll(files, ConflictStrategy.OVERWRITE, outputDir);
        return files;
    }

    private GeneratedFile findFile(List<GeneratedFile> files, Layer layer,
                                    String entityName) {
        return files.stream()
            .filter(f -> f.layer() == layer && f.entityName().equals(entityName))
            .findFirst()
            .orElseThrow(() -> new AssertionError(
                "No file found for layer " + layer + " entity " + entityName));
    }
}
