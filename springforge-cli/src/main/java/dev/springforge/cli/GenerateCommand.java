package dev.springforge.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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
import dev.springforge.tui.TamboUiRenderer;
import dev.springforge.tui.TuiRenderer;
import dev.springforge.tui.state.EntitySelectionState;
import dev.springforge.tui.state.GenerationProgressState;
import dev.springforge.tui.state.LayerConfigState;
import dev.springforge.tui.state.PreviewState;
import dev.springforge.tui.state.SplashState;

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
            if (shouldUseTui()) {
                return runInteractiveTui();
            }
            return runNonInteractive();
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

    /**
     * Determines whether to use the interactive TUI.
     * TUI is used when: no --no-tui flag, not a dumb terminal,
     * and no explicit layer flags are provided (user wants to choose interactively).
     */
    boolean shouldUseTui() {
        boolean noTui = parent != null && parent.isNoTui();
        if (noTui || PlainCliRenderer.isDumbTerminal()) {
            return false;
        }
        // If user provided explicit layer flags, skip TUI
        boolean hasLayerFlags = allLayers || dto || mapper || repository
            || service || controller || migration || openapi || upload;
        return !hasLayerFlags && !dryRun;
    }

    /**
     * Non-interactive CLI flow: resolve everything from flags/config,
     * generate, write, show summary.
     */
    Integer runNonInteractive()
            throws ConfigLoader.ConfigLoadException, IOException {
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
    }

    /**
     * Interactive TUI flow with screen navigation and back support.
     *
     * <p>Flow: Splash → Entity Selection → Layer Config → Preview → Write → Summary.
     * Escape/back returns to the previous screen.
     */
    Integer runInteractiveTui()
            throws ConfigLoader.ConfigLoadException, IOException {
        SpringForgeConfig yamlConfig = loadConfig();
        List<EntityDescriptor> allEntities = discoverEntities();

        if (allEntities.isEmpty()) {
            System.err.println("Error: No @Entity classes found");
            return ExitCodes.ENTITY_PARSE_ERROR;
        }

        // Suppress SLF4J output during TUI mode to avoid corrupting the display
        suppressLogging();

        try (TamboUiRenderer tui = new TamboUiRenderer()) {
            // Show splash briefly
            tui.showSplash(SplashState.initial()
                .withComplete(allEntities.size()));

            // Flow state
            AtomicReference<List<EntityDescriptor>> selectedEntities = new AtomicReference<>();
            AtomicReference<LayerConfigState> layerConfig = new AtomicReference<>();
            AtomicReference<List<GeneratedFile>> generatedFiles = new AtomicReference<>();
            AtomicBoolean cancelled = new AtomicBoolean(false);

            TuiFlowStep step = TuiFlowStep.ENTITY_SELECTION;

            while (step != TuiFlowStep.DONE) {
                switch (step) {
                    case ENTITY_SELECTION -> {
                        step = runEntitySelection(tui, allEntities,
                            selectedEntities, cancelled);
                    }
                    case LAYER_CONFIG -> {
                        step = runLayerConfig(tui,
                            selectedEntities.get().size(), layerConfig);
                    }
                    case PREVIEW -> {
                        step = runPreview(tui, yamlConfig,
                            selectedEntities.get(), layerConfig.get(),
                            generatedFiles);
                    }
                    case WRITE -> {
                        step = runWrite(tui, yamlConfig,
                            selectedEntities.get(), layerConfig.get(),
                            generatedFiles.get());
                    }
                    default -> step = TuiFlowStep.DONE;
                }
            }

            if (cancelled.get()) {
                return ExitCodes.SUCCESS;
            }
        } finally {
            restoreLogging();
        }

        return ExitCodes.SUCCESS;
    }

    /**
     * Suppress SLF4J Simple logger output during TUI mode.
     * SLF4J Simple writes to stderr which corrupts the TamboUI alt-screen.
     */
    private void suppressLogging() {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "off");
    }

    private void restoreLogging() {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info");
    }

    private TuiFlowStep runEntitySelection(TamboUiRenderer tui,
            List<EntityDescriptor> allEntities,
            AtomicReference<List<EntityDescriptor>> selectedOut,
            AtomicBoolean cancelledOut) {

        EntitySelectionState state = EntitySelectionState.initial(allEntities);
        AtomicReference<TuiFlowStep> nextStep = new AtomicReference<>();

        tui.showEntitySelection(state, new TuiRenderer.EntitySelectionCallbacks() {
            @Override
            public void onConfirm(List<EntityDescriptor> selected) {
                selectedOut.set(selected);
                nextStep.set(TuiFlowStep.LAYER_CONFIG);
            }

            @Override
            public void onCancel() {
                cancelledOut.set(true);
                nextStep.set(TuiFlowStep.DONE);
            }
        });

        return nextStep.get();
    }

    private TuiFlowStep runLayerConfig(TamboUiRenderer tui, int entityCount,
            AtomicReference<LayerConfigState> configOut) {

        LayerConfigState state = LayerConfigState.initial(entityCount);
        AtomicReference<TuiFlowStep> nextStep = new AtomicReference<>();

        tui.showLayerConfig(state, new TuiRenderer.LayerConfigCallbacks() {
            @Override
            public void onConfirm(LayerConfigState config) {
                configOut.set(config);
                nextStep.set(TuiFlowStep.PREVIEW);
            }

            @Override
            public void onBack() {
                nextStep.set(TuiFlowStep.ENTITY_SELECTION);
            }
        });

        return nextStep.get();
    }

    private TuiFlowStep runPreview(TamboUiRenderer tui, SpringForgeConfig yamlConfig,
            List<EntityDescriptor> entities, LayerConfigState layerConfig,
            AtomicReference<List<GeneratedFile>> filesOut) {

        Path basePath = resolveOutputPath(entities);
        String basePackage = resolveBasePackage(yamlConfig, entities);
        boolean verbose = parent != null && parent.isVerbose();

        GenerationConfig config = new GenerationConfig(
            entities, layerConfig.selectedLayers(),
            layerConfig.springVersion(), layerConfig.mapperLib(),
            layerConfig.conflictStrategy(), basePath, basePackage,
            false, verbose
        );

        TemplateRenderer renderer = new TemplateRenderer();
        BatchGenerator batchGenerator = new BatchGenerator(renderer);
        List<GeneratedFile> files = batchGenerator.generateAll(config);
        filesOut.set(files);

        PreviewState state = PreviewState.initial(files);
        AtomicReference<TuiFlowStep> nextStep = new AtomicReference<>();

        tui.showPreview(state, new TuiRenderer.PreviewCallbacks() {
            @Override
            public void onConfirm() {
                nextStep.set(TuiFlowStep.WRITE);
            }

            @Override
            public void onBack() {
                nextStep.set(TuiFlowStep.LAYER_CONFIG);
            }
        });

        return nextStep.get();
    }

    private TuiFlowStep runWrite(TamboUiRenderer tui, SpringForgeConfig yamlConfig,
            List<EntityDescriptor> entities, LayerConfigState layerConfig,
            List<GeneratedFile> files) {

        Path basePath = resolveOutputPath(entities);

        // Show progress
        GenerationProgressState progressState =
            GenerationProgressState.initial(files.size());
        tui.showProgress(progressState);

        // Write files
        CodeFileWriter writer = new CodeFileWriter();
        GenerationReport report = writer.writeAll(
            files, layerConfig.conflictStrategy(), basePath);

        // Show summary — blocks until user quits
        tui.showSummary(report);

        return TuiFlowStep.DONE;
    }

    enum TuiFlowStep {
        ENTITY_SELECTION, LAYER_CONFIG, PREVIEW, WRITE, DONE
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
