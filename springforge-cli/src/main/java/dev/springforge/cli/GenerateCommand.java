package dev.springforge.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Callable;

import dev.springforge.config.ConfigLoader;
import dev.springforge.config.SpringForgeConfig;
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
import dev.springforge.tui.PlainCliRenderer;
import dev.springforge.tui.TuiRenderer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/**
 * Implements {@code springforge generate} with all documented flags.
 * Supports interactive TUI and non-interactive (--no-tui) modes.
 */
@Command(
    name = "generate",
    description = "Generate API layers from entity classes",
    mixinStandardHelpOptions = true
)
public class GenerateCommand implements Callable<Integer> {

    private static final Logger LOG = LoggerFactory.getLogger(GenerateCommand.class);

    @ParentCommand
    private MainCommand parent;

    // --- Source options (mutually exclusive) ---

    @Option(names = {"-e", "--entity"},
        description = "Single Java entity file")
    private Path entityFile;

    @Option(names = {"-E", "--entities"},
        description = "Multiple Java entity files",
        arity = "1..*")
    private List<Path> entityFiles;

    @Option(names = {"-d", "--dir"},
        description = "Scan directory for @Entity classes")
    private Path scanDir;

    @Option(names = {"--all-entities"},
        description = "Auto-discover all @Entity classes in project src/")
    private boolean allEntities;

    // --- Layer selection ---

    @Option(names = {"--all"},
        description = "Generate all layers (default when no layer flag specified)")
    private boolean allLayers;

    @Option(names = {"--dto"},
        description = "Generate DTO classes only")
    private boolean dto;

    @Option(names = {"--mapper"},
        description = "Generate MapStruct mappers only")
    private boolean mapper;

    @Option(names = {"--repository"},
        description = "Generate Spring Data repositories only")
    private boolean repository;

    @Option(names = {"--service"},
        description = "Generate Service + ServiceImpl only")
    private boolean service;

    @Option(names = {"--controller"},
        description = "Generate REST controllers only")
    private boolean controller;

    @Option(names = {"--migration"},
        description = "Generate Liquibase/Flyway migration only")
    private boolean migration;

    @Option(names = {"--openapi"},
        description = "Generate OpenAPI spec only")
    private boolean openapi;

    @Option(names = {"--upload"},
        description = "Include file upload endpoint")
    private boolean upload;

    // --- Output options ---

    @Option(names = {"-o", "--output"},
        description = "Override output base directory")
    private Path outputPath;

    @Option(names = {"--overwrite"},
        description = "Overwrite existing files (default: skip)")
    private boolean overwrite;

    @Option(names = {"--dry-run"},
        description = "Print what would be generated, do not write")
    private boolean dryRun;

    @Option(names = {"--spring-version"},
        description = "Target Spring Boot version (2 or 3, default: 3)")
    private String springVersionFlag;

    @Option(names = {"--mapper-lib"},
        description = "Mapper library: mapstruct or modelmapper")
    private String mapperLibFlag;

    @Option(names = {"--db-migration"},
        description = "Database migration tool: liquibase or flyway")
    private String dbMigrationFlag;

    @Option(names = {"--openapi-format"},
        description = "OpenAPI format: yaml or json")
    private String openApiFormatFlag;

    @Override
    public Integer call() {
        try {
            SpringForgeConfig yamlConfig = loadConfig();
            GenerationConfig config = buildGenerationConfig(yamlConfig);

            if (config.entities().isEmpty()) {
                LOG.error("No @Entity classes found");
                System.err.println("Error: No @Entity classes found");
                return ExitCodes.ENTITY_PARSE_ERROR;
            }

            if (config.verbose()) {
                LOG.info("Generating {} layers for {} entities",
                    config.layers().size(), config.entities().size());
            }

            TemplateRenderer renderer = new TemplateRenderer();
            BatchGenerator batchGenerator = new BatchGenerator(renderer);
            List<GeneratedFile> generatedFiles = batchGenerator.generateAll(config);

            if (config.dryRun()) {
                printDryRun(generatedFiles);
                return ExitCodes.SUCCESS;
            }

            CodeFileWriter writer = new CodeFileWriter();
            GenerationReport report = writer.writeAll(
                generatedFiles, config.conflictStrategy(), config.outputBasePath());

            TuiRenderer tuiRenderer = new PlainCliRenderer();
            tuiRenderer.showSummary(report);

            return report.errorFiles() > 0
                ? ExitCodes.FILE_WRITE_ERROR
                : ExitCodes.SUCCESS;

        } catch (ConfigLoader.ConfigLoadException e) {
            LOG.error("Configuration error: {}", e.getMessage());
            System.err.println("Error: " + e.getMessage());
            return ExitCodes.CONFIG_ERROR;
        } catch (IOException e) {
            LOG.error("Entity parsing error: {}", e.getMessage());
            System.err.println("Error: " + e.getMessage());
            return ExitCodes.ENTITY_PARSE_ERROR;
        } catch (Exception e) {
            LOG.error("Unexpected error: {}", e.getMessage(), e);
            System.err.println("Error: " + e.getMessage());
            return ExitCodes.GENERAL_ERROR;
        }
    }

    SpringForgeConfig loadConfig() throws ConfigLoader.ConfigLoadException {
        ConfigLoader configLoader = new ConfigLoader();
        Path configPath = parent != null ? parent.getConfigPath() : null;
        Path projectRoot = Path.of(System.getProperty("user.dir"));
        return configLoader.load(configPath, projectRoot);
    }

    /** Entity file paths discovered during scanning — used to infer source root. */
    private List<Path> discoveredEntityFiles = List.of();

    GenerationConfig buildGenerationConfig(SpringForgeConfig yamlConfig)
            throws IOException {
        List<EntityDescriptor> entities = discoverEntities();
        EnumSet<Layer> layers = resolveLayers(yamlConfig);
        SpringVersion springVersion = resolveSpringVersion(yamlConfig);
        MapperLib mapperLib = resolveMapperLib(yamlConfig);
        ConflictStrategy conflictStrategy = resolveConflictStrategy(yamlConfig);
        Path basePath = resolveOutputPath(entities);
        String basePackage = resolveBasePackage(yamlConfig, entities);
        boolean verbose = parent != null && parent.isVerbose();

        return new GenerationConfig(
            entities, layers, springVersion, mapperLib,
            conflictStrategy, basePath, basePackage, dryRun, verbose
        );
    }

    List<EntityDescriptor> discoverEntities() throws IOException {
        List<Path> filesToParse = new ArrayList<>();

        if (entityFile != null) {
            filesToParse.add(entityFile);
        } else if (entityFiles != null && !entityFiles.isEmpty()) {
            filesToParse.addAll(entityFiles);
        } else if (scanDir != null) {
            EntityScanner scanner = new EntityScanner();
            filesToParse.addAll(scanner.scanForEntityFiles(scanDir));
        } else {
            // Default: auto-scan src/main/java (same as --all-entities)
            EntityScanner scanner = new EntityScanner();
            Path srcDir = Path.of(System.getProperty("user.dir"), "src/main/java");
            filesToParse.addAll(scanner.scanForEntityFiles(srcDir));
        }

        discoveredEntityFiles = List.copyOf(filesToParse);

        JavaAstEntityParser parser = new JavaAstEntityParser();
        List<EntityDescriptor> entities = new ArrayList<>();
        for (Path file : filesToParse) {
            try {
                entities.add(parser.parse(file));
            } catch (Exception e) {
                LOG.warn("Failed to parse entity file: {} — {}", file, e.getMessage());
            }
        }

        if (entities.size() > 1) {
            entities = parser.resolveCircularRefs(entities);
        }

        return entities;
    }

    EnumSet<Layer> resolveLayers(SpringForgeConfig yamlConfig) {
        boolean anyLayerSelected = dto || mapper || repository || service
            || controller || migration || openapi || upload;

        if (!anyLayerSelected || allLayers) {
            EnumSet<Layer> all = EnumSet.of(
                Layer.DTO_REQUEST, Layer.DTO_RESPONSE,
                Layer.MAPPER, Layer.REPOSITORY,
                Layer.SERVICE, Layer.SERVICE_IMPL,
                Layer.CONTROLLER
            );
            if (upload) {
                all.add(Layer.FILE_UPLOAD);
            }
            if (migration) {
                addMigrationLayer(all, yamlConfig);
            }
            return all;
        }

        EnumSet<Layer> selected = EnumSet.noneOf(Layer.class);
        if (dto) {
            selected.add(Layer.DTO_REQUEST);
            selected.add(Layer.DTO_RESPONSE);
        }
        if (mapper) selected.add(Layer.MAPPER);
        if (repository) selected.add(Layer.REPOSITORY);
        if (service) {
            selected.add(Layer.SERVICE);
            selected.add(Layer.SERVICE_IMPL);
        }
        if (controller) selected.add(Layer.CONTROLLER);
        if (upload) selected.add(Layer.FILE_UPLOAD);
        if (migration) addMigrationLayer(selected, yamlConfig);

        return selected;
    }

    private void addMigrationLayer(EnumSet<Layer> layers, SpringForgeConfig yamlConfig) {
        String tool = dbMigrationFlag != null
            ? dbMigrationFlag
            : yamlConfig.getGeneration().getMigrationTool();

        if ("flyway".equalsIgnoreCase(tool)) {
            layers.add(Layer.FLYWAY);
        } else {
            layers.add(Layer.LIQUIBASE);
        }
    }

    SpringVersion resolveSpringVersion(SpringForgeConfig yamlConfig) {
        String version = springVersionFlag != null
            ? springVersionFlag
            : yamlConfig.getProject().getSpringBootVersion();
        return "2".equals(version) ? SpringVersion.V2 : SpringVersion.V3;
    }

    MapperLib resolveMapperLib(SpringForgeConfig yamlConfig) {
        String lib = mapperLibFlag != null
            ? mapperLibFlag
            : yamlConfig.getGeneration().getMapperLib();
        return "modelmapper".equalsIgnoreCase(lib)
            ? MapperLib.MODEL_MAPPER
            : MapperLib.MAPSTRUCT;
    }

    ConflictStrategy resolveConflictStrategy(SpringForgeConfig yamlConfig) {
        if (overwrite) {
            return ConflictStrategy.OVERWRITE;
        }
        String strategy = yamlConfig.getGeneration().getOnConflict();
        return "overwrite".equalsIgnoreCase(strategy)
            ? ConflictStrategy.OVERWRITE
            : ConflictStrategy.SKIP;
    }

    /**
     * Resolves the output base directory.
     *
     * <ol>
     *   <li>If {@code --output} flag is given, use it directly.</li>
     *   <li>Otherwise, infer the source root from the first entity file path
     *       by stripping the package suffix
     *       (e.g. {@code src/main/java/de/foo/model/User.java}
     *       with package {@code de.foo.model} → {@code src/main/java}).</li>
     *   <li>If inference fails, warn the user and prompt interactively
     *       (or fall back to {@code src/main/java} in non-interactive mode).</li>
     * </ol>
     */
    Path resolveOutputPath(List<EntityDescriptor> entities) {
        if (outputPath != null) {
            return outputPath;
        }

        Path inferred = inferSourceRoot(entities);
        if (inferred != null) {
            return inferred;
        }

        return promptForOutputPath();
    }

    private Path inferSourceRoot(List<EntityDescriptor> entities) {
        if (discoveredEntityFiles.isEmpty() || entities.isEmpty()) {
            return null;
        }
        Path firstFile = discoveredEntityFiles.get(0).toAbsolutePath().normalize();
        String packagePath = entities.get(0).packageName().replace('.', '/');
        String suffix = packagePath + "/" + entities.get(0).className() + ".java";
        String filePath = firstFile.toString();
        if (filePath.endsWith(suffix)) {
            return Path.of(filePath.substring(0,
                filePath.length() - suffix.length() - 1));
        }
        return null;
    }

    private Path promptForOutputPath() {
        Path fallback = Path.of(System.getProperty("user.dir"), "src/main/java");
        java.io.Console console = System.console();

        if (console == null) {
            LOG.warn("Could not auto-detect source root. "
                + "Using fallback: {}. Use --output to override.", fallback);
            return fallback;
        }

        System.err.println();
        System.err.println("Warning: Could not auto-detect the source root directory.");
        System.err.println("Without a springforge.yml or --output flag, generated files");
        System.err.println("may not be placed in the correct location.");
        System.err.println();
        System.err.println("Options:");
        System.err.println("  [1] Continue with default: " + fallback);
        System.err.println("  [2] Enter a custom output path");
        System.err.println("  [3] Abort (run 'springforge init' first)");
        System.err.println();

        String choice = console.readLine("Choose [1/2/3] (default: 1): ");
        if (choice == null || choice.isBlank() || "1".equals(choice.trim())) {
            return fallback;
        }
        if ("3".equals(choice.trim())) {
            System.err.println("Aborted. Run 'springforge init' to create a config file.");
            throw new RuntimeException("Generation aborted by user");
        }
        // Option 2: ask for path
        String path = console.readLine("Enter output path: ");
        if (path == null || path.isBlank()) {
            return fallback;
        }
        return Path.of(path.trim());
    }

    /**
     * Resolves the base package for generated code. If the user has not
     * configured a custom base package (i.e., still the default "com.example"),
     * infer it from the entity package by stripping the trailing ".model"
     * segment (e.g., "de.thegeekengineer.model" → "de.thegeekengineer").
     */
    String resolveBasePackage(SpringForgeConfig yamlConfig,
                              List<EntityDescriptor> entities) {
        String configured = yamlConfig.getProject().getBasePackage();
        if (!"com.example".equals(configured) || entities.isEmpty()) {
            return configured;
        }
        String entityPackage = entities.get(0).packageName();
        if (entityPackage.endsWith(".model")) {
            return entityPackage.substring(0, entityPackage.length() - ".model".length());
        }
        if (entityPackage.endsWith(".entity")) {
            return entityPackage.substring(0, entityPackage.length() - ".entity".length());
        }
        if (entityPackage.endsWith(".domain")) {
            return entityPackage.substring(0, entityPackage.length() - ".domain".length());
        }
        return entityPackage;
    }

    // Package-private setters for PreviewCommand delegation
    void setEntityFile(Path entityFile) { this.entityFile = entityFile; }
    void setEntityFiles(List<Path> entityFiles) { this.entityFiles = entityFiles; }
    void setScanDir(Path scanDir) { this.scanDir = scanDir; }
    void setAllEntities(boolean allEntities) { this.allEntities = allEntities; }
    void setSpringVersionFlag(String flag) { this.springVersionFlag = flag; }
    void setMapperLibFlag(String flag) { this.mapperLibFlag = flag; }
    void setDryRun(boolean dryRun) { this.dryRun = dryRun; }
    void setParent(MainCommand parent) { this.parent = parent; }

    private void printDryRun(List<GeneratedFile> files) {
        System.out.println("=== Dry Run — Files that would be generated ===");
        System.out.println();
        for (GeneratedFile file : files) {
            System.out.printf("  [%s] %s%n", file.layer(), file.outputPath());
        }
        System.out.println();
        System.out.println("Total: " + files.size() + " files");
        System.out.println("(No files were written)");
    }
}
