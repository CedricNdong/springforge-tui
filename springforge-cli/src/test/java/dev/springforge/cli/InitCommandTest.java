package dev.springforge.cli;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class InitCommandTest {

    @TempDir
    Path tempDir;

    private ByteArrayOutputStream outputStream;
    private PrintStream printStream;

    @BeforeEach
    void setUp() {
        outputStream = new ByteArrayOutputStream();
        printStream = new PrintStream(outputStream);
        System.setProperty("user.dir", tempDir.toString());
    }

    @Test
    @DisplayName("should create springforge.yml with default values")
    void shouldCreateConfigWithDefaults() {
        String input = "\n\n\n\n\n\n"; // Accept all defaults
        BufferedReader reader = new BufferedReader(new StringReader(input));

        InitCommand cmd = new InitCommand(reader, printStream);
        int exitCode = cmd.call();

        assertThat(exitCode).isEqualTo(ExitCodes.SUCCESS);
        Path configFile = tempDir.resolve("springforge.yml");
        assertThat(Files.exists(configFile)).isTrue();
    }

    @Test
    @DisplayName("should create springforge.yml with custom values")
    void shouldCreateConfigWithCustomValues() throws Exception {
        String input = "com.myapp\n3\nmodelmapper\nliquibase\nyaml\noverwrite\n";
        BufferedReader reader = new BufferedReader(new StringReader(input));

        InitCommand cmd = new InitCommand(reader, printStream);
        int exitCode = cmd.call();

        assertThat(exitCode).isEqualTo(ExitCodes.SUCCESS);
        String content = Files.readString(tempDir.resolve("springforge.yml"));
        assertThat(content).contains("com.myapp");
        assertThat(content).contains("modelmapper");
        assertThat(content).contains("liquibase");
    }

    @Test
    @DisplayName("should abort when existing config and user says no")
    void shouldAbortWhenExistingConfigAndNo() throws Exception {
        Files.writeString(tempDir.resolve("springforge.yml"), "existing: true");

        String input = "n\n";
        BufferedReader reader = new BufferedReader(new StringReader(input));

        InitCommand cmd = new InitCommand(reader, printStream);
        int exitCode = cmd.call();

        assertThat(exitCode).isEqualTo(ExitCodes.SUCCESS);
        String output = outputStream.toString();
        assertThat(output).contains("Aborted");
    }

    @Test
    @DisplayName("should overwrite when existing config and user says yes")
    void shouldOverwriteWhenUserConfirms() throws Exception {
        Files.writeString(tempDir.resolve("springforge.yml"), "existing: true");

        String input = "y\ncom.newapp\n\n\n\n\n\n";
        BufferedReader reader = new BufferedReader(new StringReader(input));

        InitCommand cmd = new InitCommand(reader, printStream);
        int exitCode = cmd.call();

        assertThat(exitCode).isEqualTo(ExitCodes.SUCCESS);
        String content = Files.readString(tempDir.resolve("springforge.yml"));
        assertThat(content).contains("com.newapp");
    }

    @Test
    @DisplayName("should work in --no-tui mode (non-interactive with defaults)")
    void shouldWorkInNoTuiMode() {
        String input = "\n\n\n\n\n\n";
        BufferedReader reader = new BufferedReader(new StringReader(input));

        InitCommand cmd = new InitCommand(reader, printStream);
        int exitCode = cmd.call();

        assertThat(exitCode).isEqualTo(ExitCodes.SUCCESS);
        assertThat(Files.exists(tempDir.resolve("springforge.yml"))).isTrue();
    }
}
