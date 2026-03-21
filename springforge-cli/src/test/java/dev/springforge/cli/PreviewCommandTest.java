package dev.springforge.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

class PreviewCommandTest {

    @TempDir
    Path tempDir;

    private Path entityFile;
    private ByteArrayOutputStream stdOut;
    private PrintStream originalOut;

    @BeforeEach
    void setUp() throws Exception {
        entityFile = tempDir.resolve("Product.java");
        Files.writeString(entityFile, """
            package com.example.model;

            import jakarta.persistence.Entity;
            import jakarta.persistence.Id;

            @Entity
            public class Product {
                @Id
                private Long id;
                private String name;
                private double price;
            }
            """);

        stdOut = new ByteArrayOutputStream();
        originalOut = System.out;
        System.setOut(new PrintStream(stdOut));
    }

    @Test
    @DisplayName("should preview generated code to stdout in text format")
    void shouldPreviewAsText() {
        System.setOut(new PrintStream(stdOut));

        MainCommand main = new MainCommand();
        CommandLine cli = new CommandLine(main);
        int exitCode = cli.execute(
            "preview",
            "--entity", entityFile.toString()
        );

        System.setOut(originalOut);
        String output = stdOut.toString();
        assertThat(exitCode).isEqualTo(0);
        assertThat(output).contains("Product");
    }

    @Test
    @DisplayName("should preview generated code in JSON format")
    void shouldPreviewAsJson() {
        System.setOut(new PrintStream(stdOut));

        MainCommand main = new MainCommand();
        CommandLine cli = new CommandLine(main);
        int exitCode = cli.execute(
            "preview",
            "--entity", entityFile.toString(),
            "--output-format", "json"
        );

        System.setOut(originalOut);
        String output = stdOut.toString();
        assertThat(exitCode).isEqualTo(0);
        assertThat(output).contains("\"status\"");
        assertThat(output).contains("\"PREVIEW\"");
        assertThat(output).contains("\"files_count\"");
    }

    @Test
    @DisplayName("should return exit code 3 when no entities found")
    void shouldReturnExitCode3WhenNoEntities() {
        System.setOut(originalOut);

        MainCommand main = new MainCommand();
        CommandLine cli = new CommandLine(main);
        int exitCode = cli.execute(
            "preview",
            "--entity", tempDir.resolve("nonexistent.java").toString()
        );

        assertThat(exitCode).isEqualTo(ExitCodes.ENTITY_PARSE_ERROR);
    }
}
