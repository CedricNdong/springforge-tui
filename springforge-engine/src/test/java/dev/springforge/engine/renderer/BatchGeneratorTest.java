package dev.springforge.engine.renderer;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import dev.springforge.engine.model.ConflictStrategy;
import dev.springforge.engine.model.EntityDescriptor;
import dev.springforge.engine.model.FieldDescriptor;
import dev.springforge.engine.model.GeneratedFile;
import dev.springforge.engine.model.GenerationConfig;
import dev.springforge.engine.model.Layer;
import dev.springforge.engine.model.MapperLib;
import dev.springforge.engine.model.RelationType;
import dev.springforge.engine.model.SpringNamespace;
import dev.springforge.engine.model.SpringVersion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BatchGeneratorTest {

    private BatchGenerator batchGenerator;

    @BeforeEach
    void setUp() {
        batchGenerator = new BatchGenerator(new TemplateRenderer());
    }

    private EntityDescriptor createEntity(String name) {
        return new EntityDescriptor(
            name, "com.example.model",
            List.of(
                new FieldDescriptor("id", "Long", null, true, false, false,
                    RelationType.NONE, null, false),
                new FieldDescriptor("name", "String", null, false, false, false,
                    RelationType.NONE, null, false)
            ),
            Set.of("Entity", "Data"),
            SpringNamespace.JAKARTA,
            true, "id", "Long"
        );
    }

    private GenerationConfig configWith(List<EntityDescriptor> entities,
            EnumSet<Layer> layers) {
        return new GenerationConfig(
            entities, layers, SpringVersion.V3,
            MapperLib.MAPSTRUCT, ConflictStrategy.SKIP,
            Path.of("target/generated"), "com.example",
            false, false
        );
    }

    @Test
    @DisplayName("should process all entities with all layers")
    void shouldProcessAllEntities() {
        List<EntityDescriptor> entities = List.of(
            createEntity("User"),
            createEntity("Product"),
            createEntity("Order")
        );
        EnumSet<Layer> layers = EnumSet.of(
            Layer.DTO_REQUEST, Layer.DTO_RESPONSE, Layer.REPOSITORY);

        GenerationConfig config = configWith(entities, layers);
        List<GeneratedFile> files = batchGenerator.generateAll(config);

        assertThat(files).hasSize(9);
    }

    @Test
    @DisplayName("should handle single entity without virtual threads")
    void shouldHandleSingleEntity() {
        List<EntityDescriptor> entities = List.of(createEntity("User"));
        EnumSet<Layer> layers = EnumSet.of(Layer.DTO_REQUEST);

        GenerationConfig config = configWith(entities, layers);
        List<GeneratedFile> files = batchGenerator.generateAll(config);

        assertThat(files).hasSize(1);
        assertThat(files.get(0).entityName()).isEqualTo("User");
    }

    @Test
    @DisplayName("should handle empty entity list")
    void shouldHandleEmptyEntityList() {
        GenerationConfig config = configWith(List.of(), EnumSet.of(Layer.DTO_REQUEST));
        List<GeneratedFile> files = batchGenerator.generateAll(config);

        assertThat(files).isEmpty();
    }

    @Test
    @DisplayName("should generate all layers for each entity")
    void shouldGenerateAllLayersForEachEntity() {
        List<EntityDescriptor> entities = List.of(
            createEntity("User"),
            createEntity("Product")
        );
        EnumSet<Layer> allLayers = EnumSet.allOf(Layer.class);

        GenerationConfig config = configWith(entities, allLayers);
        List<GeneratedFile> files = batchGenerator.generateAll(config);

        assertThat(files).hasSize(entities.size() * allLayers.size());
    }

    @Test
    @DisplayName("should generate 10 entities x all layers in under 3 seconds")
    void shouldMeetPerformanceTarget() {
        List<EntityDescriptor> entities = IntStream.rangeClosed(1, 10)
            .mapToObj(i -> createEntity("Entity" + i))
            .toList();
        EnumSet<Layer> allLayers = EnumSet.allOf(Layer.class);

        GenerationConfig config = configWith(entities, allLayers);

        long startTime = System.nanoTime();
        List<GeneratedFile> files = batchGenerator.generateAll(config);
        long durationMs = (System.nanoTime() - startTime) / 1_000_000;

        assertThat(files).hasSize(10 * allLayers.size());
        assertThat(durationMs).isLessThan(3000);
    }

    @Test
    @DisplayName("should produce correct content for each entity")
    void shouldProduceCorrectContent() {
        List<EntityDescriptor> entities = List.of(
            createEntity("User"),
            createEntity("Product")
        );
        EnumSet<Layer> layers = EnumSet.of(Layer.DTO_REQUEST);

        GenerationConfig config = configWith(entities, layers);
        List<GeneratedFile> files = batchGenerator.generateAll(config);

        assertThat(files).extracting(GeneratedFile::entityName)
            .containsExactlyInAnyOrder("User", "Product");

        for (GeneratedFile file : files) {
            assertThat(file.content()).contains("RequestDto");
            assertThat(file.content()).contains("private String name;");
        }
    }
}
