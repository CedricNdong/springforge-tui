package dev.springforge.cli;

import java.nio.file.Path;
import java.nio.file.Paths;

import dev.springforge.cli.SpikeCommand.EntityInfo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpikeCommandTest {

    private Path testResource(String name) {
        return Paths.get("src/test/resources/" + name);
    }

    @Nested
    @DisplayName("JavaParser entity parsing")
    class EntityParsingTest {

        @Test
        @DisplayName("should parse @Entity class with fields and annotations")
        void shouldParseEntityWithFieldsAndAnnotations() throws Exception {
            EntityInfo entity = SpikeCommand.parseEntity(testResource("SampleEntity.java"));

            assertThat(entity.className()).isEqualTo("User");
            assertThat(entity.packageName()).isEqualTo("com.example.model");
            assertThat(entity.hasEntityAnnotation()).isTrue();
            assertThat(entity.fields()).hasSize(4);
        }

        @Test
        @DisplayName("should detect @Id field")
        void shouldDetectIdField() throws Exception {
            EntityInfo entity = SpikeCommand.parseEntity(testResource("SampleEntity.java"));

            assertThat(entity.fields())
                .filteredOn(SpikeCommand.FieldInfo::isId)
                .hasSize(1)
                .first()
                .satisfies(field -> {
                    assertThat(field.name()).isEqualTo("id");
                    assertThat(field.type()).isEqualTo("Long");
                });
        }

        @Test
        @DisplayName("should parse all field names and types")
        void shouldParseAllFieldNamesAndTypes() throws Exception {
            EntityInfo entity = SpikeCommand.parseEntity(testResource("SampleEntity.java"));

            assertThat(entity.fields())
                .extracting(SpikeCommand.FieldInfo::name)
                .containsExactly("id", "username", "email", "orders");

            assertThat(entity.fields())
                .extracting(SpikeCommand.FieldInfo::type)
                .containsExactly("Long", "String", "String", "List<Order>");
        }

        @Test
        @DisplayName("should report hasEntityAnnotation=false when @Entity is missing")
        void shouldReportMissingEntityAnnotation() throws Exception {
            EntityInfo entity = SpikeCommand.parseEntity(
                testResource("NoEntityAnnotation.java"));

            assertThat(entity.className()).isEqualTo("PlainClass");
            assertThat(entity.hasEntityAnnotation()).isFalse();
        }

        @Test
        @DisplayName("should throw when file has no class declaration")
        void shouldThrowWhenNoClassFound() {
            assertThatThrownBy(() ->
                SpikeCommand.parseEntity(testResource("EmptyFile.java")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No class found");
        }

        @Test
        @DisplayName("should throw when file does not exist")
        void shouldThrowWhenFileNotFound() {
            assertThatThrownBy(() ->
                SpikeCommand.parseEntity(Path.of("nonexistent/Missing.java")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File not found");
        }
    }

    @Nested
    @DisplayName("Picocli CLI argument parsing")
    class CliParsingTest {

        @Test
        @DisplayName("should accept --no-tui flag")
        void shouldAcceptNoTuiFlag() {
            SpikeCommand command = new SpikeCommand();
            new CommandLine(command).parseArgs("--no-tui");
            // no exception = parsed successfully
        }

        @Test
        @DisplayName("should accept entity file as positional parameter")
        void shouldAcceptEntityFileParameter() {
            SpikeCommand command = new SpikeCommand();
            new CommandLine(command).parseArgs("--no-tui", "src/test/resources/SampleEntity.java");
            // no exception = parsed successfully
        }

        @Test
        @DisplayName("should accept --version flag")
        void shouldAcceptVersionFlag() {
            SpikeCommand command = new SpikeCommand();
            CommandLine cli = new CommandLine(command);
            // --version causes ParseResult.isVersionHelpRequested
            var result = cli.parseArgs("--version");
            assertThat(result.isVersionHelpRequested()).isTrue();
        }

        @Test
        @DisplayName("should accept --help flag")
        void shouldAcceptHelpFlag() {
            SpikeCommand command = new SpikeCommand();
            CommandLine cli = new CommandLine(command);
            var result = cli.parseArgs("--help");
            assertThat(result.isUsageHelpRequested()).isTrue();
        }
    }

    @Nested
    @DisplayName("Plain CLI mode (--no-tui)")
    class PlainCliModeTest {

        @Test
        @DisplayName("should run successfully in --no-tui mode without entity file")
        void shouldRunWithoutEntityFile() {
            SpikeCommand command = new SpikeCommand();
            CommandLine cli = new CommandLine(command);
            int exitCode = cli.execute("--no-tui");

            assertThat(exitCode).isZero();
        }

        @Test
        @DisplayName("should run successfully in --no-tui mode with entity file")
        void shouldRunWithEntityFile() {
            SpikeCommand command = new SpikeCommand();
            CommandLine cli = new CommandLine(command);
            int exitCode = cli.execute("--no-tui", "src/test/resources/SampleEntity.java");

            assertThat(exitCode).isZero();
        }

        @Test
        @DisplayName("should fail in --no-tui mode with nonexistent file")
        void shouldFailWithNonexistentFile() {
            SpikeCommand command = new SpikeCommand();
            CommandLine cli = new CommandLine(command);
            int exitCode = cli.execute("--no-tui", "nonexistent/Missing.java");

            assertThat(exitCode).isNotZero();
        }
    }
}
