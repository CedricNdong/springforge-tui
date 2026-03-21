package dev.springforge.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Main entry point for the SpringForge CLI.
 * Dispatches to subcommands: generate, init, preview.
 */
@Command(
    name = "springforge",
    description = "SpringForge — Generate Spring Boot API layers from @Entity classes",
    mixinStandardHelpOptions = true,
    versionProvider = ManifestVersionProvider.class,
    subcommands = {
        GenerateCommand.class,
        InitCommand.class,
        PreviewCommand.class
    }
)
public class MainCommand implements Runnable {

    @Option(
        names = {"-c", "--config"},
        description = "Path to springforge.yml (overrides local file)",
        scope = CommandLine.ScopeType.INHERIT
    )
    private java.nio.file.Path configPath;

    @Option(
        names = {"-v", "--verbose"},
        description = "Enable verbose output",
        scope = CommandLine.ScopeType.INHERIT
    )
    private boolean verbose;

    @Option(
        names = {"--no-color"},
        description = "Disable colored output",
        scope = CommandLine.ScopeType.INHERIT
    )
    private boolean noColor;

    @Option(
        names = {"--no-tui"},
        description = "Force non-interactive mode",
        scope = CommandLine.ScopeType.INHERIT
    )
    private boolean noTui;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new MainCommand()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        new CommandLine(this).usage(System.out);
    }

    public java.nio.file.Path getConfigPath() {
        return configPath;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public boolean isNoColor() {
        return noColor;
    }

    public boolean isNoTui() {
        return noTui;
    }
}
