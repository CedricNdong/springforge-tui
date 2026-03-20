package dev.springforge.engine.writer;

import java.nio.file.Files;
import java.nio.file.Path;

import dev.springforge.engine.model.ConflictStrategy;

/**
 * Checks whether a file should be written based on the conflict strategy.
 */
public final class ConflictChecker {

    private ConflictChecker() {}

    public static boolean shouldWrite(Path outputPath, ConflictStrategy strategy) {
        if (!Files.exists(outputPath)) {
            return true;
        }
        return strategy == ConflictStrategy.OVERWRITE;
    }
}
