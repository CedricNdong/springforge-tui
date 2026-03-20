package dev.springforge.engine.model;

public record FileGenerationResult(
    String filePath,
    GenerationStatus status,
    String message
) {}
