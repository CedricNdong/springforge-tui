package dev.springforge.engine.scanner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class EntityScannerTest {

    private final EntityScanner scanner = new EntityScanner();

    @Test
    @DisplayName("should find all @Entity Java files in directory")
    void shouldFindEntityFiles() throws IOException {
        Path dir = Path.of("src/test/resources/entities");
        List<Path> files = scanner.scanForEntityFiles(dir);

        assertThat(files)
            .hasSizeGreaterThanOrEqualTo(4)
            .allMatch(p -> p.toString().endsWith(".java"));
    }

    @Test
    @DisplayName("should not include files without @Entity annotation")
    void shouldExcludeNonEntityFiles() throws IOException {
        Path dir = Path.of("src/test/resources/entities");
        List<Path> files = scanner.scanForEntityFiles(dir);

        assertThat(files)
            .noneMatch(p -> p.getFileName().toString().equals("PlainClass.java"))
            .noneMatch(p -> p.getFileName().toString().equals("EmptyFile.java"));
    }

    @Test
    @DisplayName("should return empty list when no entities found")
    void shouldReturnEmptyWhenNoEntities(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("NotEntity.java"),
            "public class NotEntity { }");

        List<Path> files = scanner.scanForEntityFiles(tempDir);
        assertThat(files).isEmpty();
    }

    @Test
    @DisplayName("should return empty list when directory does not exist")
    void shouldReturnEmptyWhenDirNotFound() throws IOException {
        List<Path> files = scanner.scanForEntityFiles(Path.of("nonexistent/dir"));
        assertThat(files).isEmpty();
    }

    @Test
    @DisplayName("should return empty list for non-directory path")
    void shouldReturnEmptyForNonDirectory() throws IOException {
        List<Path> files = scanner.scanForEntityFiles(
            Path.of("src/test/resources/entities/User.java"));
        assertThat(files).isEmpty();
    }

    @Test
    @DisplayName("should scan nested directories")
    void shouldScanNestedDirectories(@TempDir Path tempDir) throws IOException {
        Path nested = tempDir.resolve("com/example/model");
        Files.createDirectories(nested);
        Files.writeString(nested.resolve("MyEntity.java"),
            "import jakarta.persistence.Entity;\n@Entity\npublic class MyEntity {}");

        List<Path> files = scanner.scanForEntityFiles(tempDir);
        assertThat(files).hasSize(1);
        assertThat(files.get(0).getFileName().toString()).isEqualTo("MyEntity.java");
    }
}
