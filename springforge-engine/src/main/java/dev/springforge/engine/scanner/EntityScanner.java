package dev.springforge.engine.scanner;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scans a source directory for .java files containing @Entity annotation.
 * Uses a fast string pre-filter before any AST parsing.
 */
public class EntityScanner {

    private static final Logger LOG = LoggerFactory.getLogger(EntityScanner.class);
    private static final String ENTITY_MARKER = "@Entity";

    public List<Path> scanForEntityFiles(Path srcDir) throws IOException {
        if (!Files.exists(srcDir)) {
            LOG.warn("Source directory does not exist: {}", srcDir);
            return Collections.emptyList();
        }
        if (!Files.isDirectory(srcDir)) {
            LOG.warn("Path is not a directory: {}", srcDir);
            return Collections.emptyList();
        }

        List<Path> entityFiles = new ArrayList<>();

        Files.walkFileTree(srcDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                if (isJavaFile(file) && containsEntityAnnotation(file)) {
                    entityFiles.add(file);
                    LOG.debug("Found @Entity file: {}", file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                LOG.warn("Failed to visit file: {}", file, exc);
                return FileVisitResult.CONTINUE;
            }
        });

        LOG.info("Found {} @Entity files in {}", entityFiles.size(), srcDir);
        return Collections.unmodifiableList(entityFiles);
    }

    private static boolean isJavaFile(Path file) {
        return file.toString().endsWith(".java");
    }

    private static boolean containsEntityAnnotation(Path file) throws IOException {
        String content = Files.readString(file);
        return content.contains(ENTITY_MARKER);
    }
}
