package dev.springforge.engine.writer;

import java.nio.file.Path;

/**
 * Validates output paths to prevent directory traversal attacks.
 */
public final class PathValidator {

    private PathValidator() {}

    /**
     * Ensures the resolved output path stays within the base directory.
     * Rejects any path containing "../" traversal.
     *
     * @throws SecurityException if path traversal is detected
     */
    public static void validate(Path outputPath, Path basePath) {
        Path normalized = outputPath.normalize();
        Path normalizedBase = basePath.normalize();

        if (!normalized.startsWith(normalizedBase)) {
            throw new SecurityException(
                "Path traversal detected: " + outputPath
                    + " escapes base directory " + basePath);
        }
    }
}
