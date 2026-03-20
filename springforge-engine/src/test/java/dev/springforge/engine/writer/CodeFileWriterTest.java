package dev.springforge.engine.writer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import dev.springforge.engine.model.ConflictStrategy;
import dev.springforge.engine.model.FileGenerationResult;
import dev.springforge.engine.model.GeneratedFile;
import dev.springforge.engine.model.GenerationReport;
import dev.springforge.engine.model.GenerationStatus;
import dev.springforge.engine.model.Layer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class CodeFileWriterTest {

    private final CodeFileWriter writer = new CodeFileWriter();

    @Nested
    @DisplayName("File writing")
    class FileWritingTest {

        @Test
        @DisplayName("should create file and parent directories")
        void shouldCreateFileAndDirs(@TempDir Path tempDir) {
            Path outputPath = tempDir.resolve("com/example/dto/UserRequestDto.java");
            GeneratedFile file = new GeneratedFile(
                outputPath, "package com.example.dto;", Layer.DTO_REQUEST, "User");

            GenerationReport report = writer.writeAll(
                List.of(file), ConflictStrategy.SKIP, tempDir);

            assertThat(report.createdFiles()).isEqualTo(1);
            assertThat(Files.exists(outputPath)).isTrue();
        }

        @Test
        @DisplayName("should write correct content to file")
        void shouldWriteCorrectContent(@TempDir Path tempDir) throws IOException {
            Path outputPath = tempDir.resolve("Test.java");
            String content = "public class Test {}";
            GeneratedFile file = new GeneratedFile(
                outputPath, content, Layer.DTO_REQUEST, "Test");

            writer.writeAll(List.of(file), ConflictStrategy.SKIP, tempDir);

            assertThat(Files.readString(outputPath)).isEqualTo(content);
        }

        @Test
        @DisplayName("should report correct counts in GenerationReport")
        void shouldReportCorrectCounts(@TempDir Path tempDir) throws IOException {
            Path existing = tempDir.resolve("Existing.java");
            Files.writeString(existing, "old content");

            List<GeneratedFile> files = List.of(
                new GeneratedFile(
                    tempDir.resolve("New.java"), "new", Layer.DTO_REQUEST, "New"),
                new GeneratedFile(
                    existing, "updated", Layer.DTO_RESPONSE, "Existing")
            );

            GenerationReport report = writer.writeAll(
                files, ConflictStrategy.SKIP, tempDir);

            assertThat(report.totalFiles()).isEqualTo(2);
            assertThat(report.createdFiles()).isEqualTo(1);
            assertThat(report.skippedFiles()).isEqualTo(1);
            assertThat(report.errorFiles()).isZero();
        }
    }

    @Nested
    @DisplayName("Skip strategy")
    class SkipStrategyTest {

        @Test
        @DisplayName("should skip existing files with SKIP strategy")
        void shouldSkipExistingFiles(@TempDir Path tempDir) throws IOException {
            Path existing = tempDir.resolve("Existing.java");
            Files.writeString(existing, "original");

            GeneratedFile file = new GeneratedFile(
                existing, "replaced", Layer.DTO_REQUEST, "Existing");

            GenerationReport report = writer.writeAll(
                List.of(file), ConflictStrategy.SKIP, tempDir);

            assertThat(report.skippedFiles()).isEqualTo(1);
            assertThat(Files.readString(existing)).isEqualTo("original");
        }
    }

    @Nested
    @DisplayName("Overwrite strategy")
    class OverwriteStrategyTest {

        @Test
        @DisplayName("should overwrite existing files with OVERWRITE strategy")
        void shouldOverwriteExistingFiles(@TempDir Path tempDir) throws IOException {
            Path existing = tempDir.resolve("Existing.java");
            Files.writeString(existing, "original");

            GeneratedFile file = new GeneratedFile(
                existing, "replaced", Layer.DTO_REQUEST, "Existing");

            GenerationReport report = writer.writeAll(
                List.of(file), ConflictStrategy.OVERWRITE, tempDir);

            assertThat(report.createdFiles()).isEqualTo(1);
            assertThat(Files.readString(existing)).isEqualTo("replaced");
        }
    }

    @Nested
    @DisplayName("Path traversal rejection")
    class PathTraversalTest {

        @Test
        @DisplayName("should reject ../ path traversal")
        void shouldRejectPathTraversal(@TempDir Path tempDir) {
            Path maliciousPath = tempDir.resolve("../../../etc/passwd");
            GeneratedFile file = new GeneratedFile(
                maliciousPath, "malicious", Layer.DTO_REQUEST, "Evil");

            GenerationReport report = writer.writeAll(
                List.of(file), ConflictStrategy.OVERWRITE, tempDir);

            assertThat(report.errorFiles()).isEqualTo(1);

            FileGenerationResult result = report.results().get(0);
            assertThat(result.status()).isEqualTo(GenerationStatus.ERROR);
            assertThat(result.message()).contains("Path traversal");
        }
    }
}
