package dev.springforge.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import dev.springforge.config.ConfigLoader;
import dev.springforge.config.SpringForgeConfig;
import dev.springforge.engine.model.GeneratedFile;
import dev.springforge.engine.model.GenerationConfig;
import dev.springforge.engine.renderer.BatchGenerator;
import dev.springforge.engine.renderer.TemplateRenderer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/**
 * Implements {@code springforge preview} — renders generated code to stdout
 * without writing files. Supports {@code --output-format json} for
 * machine-readable output.
 */
@Command(
    name = "preview",
    description = "Preview generated code without writing files",
    mixinStandardHelpOptions = true
)
public class PreviewCommand implements Callable<Integer> {

    private static final Logger LOG = LoggerFactory.getLogger(PreviewCommand.class);

    @ParentCommand
    private MainCommand parent;

    @Option(names = {"--output-format"},
        description = "Output format: text or json (default: text)")
    private String outputFormat;

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

    @Option(names = {"--spring-version"},
        description = "Target Spring Boot version (2 or 3)")
    private String springVersionFlag;

    @Option(names = {"--mapper-lib"},
        description = "Mapper library: mapstruct or modelmapper")
    private String mapperLibFlag;

    @Override
    public Integer call() {
        try {
            GenerateCommand generateCmd = createDelegateGenerateCommand();
            SpringForgeConfig yamlConfig = generateCmd.loadConfig();
            GenerationConfig config = generateCmd.buildGenerationConfig(yamlConfig);

            if (config.entities().isEmpty()) {
                LOG.error("No @Entity classes found");
                System.err.println("Error: No @Entity classes found");
                return ExitCodes.ENTITY_PARSE_ERROR;
            }

            TemplateRenderer renderer = new TemplateRenderer();
            BatchGenerator batchGenerator = new BatchGenerator(renderer);
            List<GeneratedFile> files = batchGenerator.generateAll(config);

            if ("json".equalsIgnoreCase(outputFormat)) {
                printJson(files);
            } else {
                printText(files);
            }

            return ExitCodes.SUCCESS;

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

    private GenerateCommand createDelegateGenerateCommand() {
        GenerateCommand cmd = new GenerateCommand();
        cmd.setEntityFile(entityFile);
        cmd.setEntityFiles(entityFiles);
        cmd.setScanDir(scanDir);
        cmd.setAllEntities(allEntities);
        cmd.setSpringVersionFlag(springVersionFlag);
        cmd.setMapperLibFlag(mapperLibFlag);
        cmd.setDryRun(true);
        cmd.setParent(parent);
        return cmd;
    }

    private void printText(List<GeneratedFile> files) {
        for (GeneratedFile file : files) {
            System.out.println("=== " + file.outputPath() + " ===");
            System.out.println(file.content());
            System.out.println();
        }
        System.out.println("Total: " + files.size() + " files");
    }

    private void printJson(List<GeneratedFile> files) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();

        root.put("status", "PREVIEW");
        root.put("files_count", files.size());

        ArrayNode results = mapper.createArrayNode();
        for (GeneratedFile file : files) {
            ObjectNode fileNode = mapper.createObjectNode();
            fileNode.put("file", file.outputPath().getFileName().toString());
            fileNode.put("path", file.outputPath().toString());
            fileNode.put("layer", file.layer().name());
            fileNode.put("entity", file.entityName());
            fileNode.put("content", file.content());
            results.add(fileNode);
        }
        root.set("results", results);

        System.out.println(mapper.writerWithDefaultPrettyPrinter()
            .writeValueAsString(root));
    }
}
