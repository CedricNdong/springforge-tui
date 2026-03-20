package dev.springforge.engine.writer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import dev.springforge.engine.model.ConflictStrategy;
import dev.springforge.engine.model.FileGenerationResult;
import dev.springforge.engine.model.GeneratedFile;
import dev.springforge.engine.model.GenerationReport;
import dev.springforge.engine.model.GenerationStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes generated files to disk with conflict checking and path validation.
 * Named CodeFileWriter to avoid conflict with java.io.FileWriter.
 */
public class CodeFileWriter {

    private static final Logger LOG = LoggerFactory.getLogger(CodeFileWriter.class);

    public GenerationReport writeAll(List<GeneratedFile> files,
            ConflictStrategy strategy, Path basePath) {
        Instant start = Instant.now();

        List<FileGenerationResult> results = new ArrayList<>();
        int created = 0;
        int skipped = 0;
        int errors = 0;

        for (GeneratedFile file : files) {
            FileGenerationResult result = writeSingle(file, strategy, basePath);
            results.add(result);
            switch (result.status()) {
                case CREATED -> created++;
                case SKIPPED -> skipped++;
                case ERROR -> errors++;
            }
        }

        Duration duration = Duration.between(start, Instant.now());
        return new GenerationReport(
            files.size(), created, skipped, errors, results, duration
        );
    }

    FileGenerationResult writeSingle(GeneratedFile file,
            ConflictStrategy strategy, Path basePath) {
        Path outputPath = file.outputPath();
        String pathStr = outputPath.toString();

        try {
            PathValidator.validate(outputPath, basePath);
        } catch (SecurityException e) {
            LOG.error("Path traversal rejected: {}", outputPath);
            return new FileGenerationResult(
                pathStr, GenerationStatus.ERROR, e.getMessage());
        }

        if (!ConflictChecker.shouldWrite(outputPath, strategy)) {
            LOG.info("Skipped: {} (already exists)", pathStr);
            return new FileGenerationResult(
                pathStr, GenerationStatus.SKIPPED, "File already exists");
        }

        try {
            Files.createDirectories(outputPath.getParent());
            Files.writeString(outputPath, file.content(), StandardCharsets.UTF_8);

            long size = Files.size(outputPath);
            LOG.info("Created: {} ({}B)", pathStr, size);
            return new FileGenerationResult(
                pathStr, GenerationStatus.CREATED,
                "Written (" + size + "B)");
        } catch (IOException e) {
            LOG.error("Failed to write: {}", pathStr, e);
            return new FileGenerationResult(
                pathStr, GenerationStatus.ERROR, e.getMessage());
        }
    }
}
