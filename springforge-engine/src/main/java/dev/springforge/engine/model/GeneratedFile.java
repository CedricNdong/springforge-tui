package dev.springforge.engine.model;

import java.nio.file.Path;

public record GeneratedFile(
    Path outputPath,
    String content,
    Layer layer,
    String entityName
) {}
