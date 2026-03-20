package dev.springforge.engine.model;

import java.time.Duration;
import java.util.List;

public record GenerationReport(
    int totalFiles,
    int createdFiles,
    int skippedFiles,
    int errorFiles,
    List<FileGenerationResult> results,
    Duration duration
) {}
