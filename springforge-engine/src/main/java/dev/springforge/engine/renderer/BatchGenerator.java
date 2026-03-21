package dev.springforge.engine.renderer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import dev.springforge.engine.model.EntityDescriptor;
import dev.springforge.engine.model.GeneratedFile;
import dev.springforge.engine.model.GenerationConfig;
import dev.springforge.engine.model.Layer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Batch generator using Java 21 virtual threads for parallel per-entity generation.
 * Processes all entities concurrently while each entity's layers are generated sequentially.
 */
public class BatchGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(BatchGenerator.class);

    private final TemplateRenderer templateRenderer;

    public BatchGenerator(TemplateRenderer templateRenderer) {
        this.templateRenderer = templateRenderer;
    }

    /**
     * Generates all layers for all entities using virtual threads.
     * Each entity is processed in its own virtual thread.
     *
     * @param config generation config with entities and layers
     * @return list of all generated files
     */
    public List<GeneratedFile> generateAll(GenerationConfig config) {
        List<EntityDescriptor> entities = config.entities();
        if (entities.isEmpty()) {
            return List.of();
        }

        if (entities.size() == 1) {
            return templateRenderer.renderAll(config);
        }

        LOG.info("Starting batch generation for {} entities using virtual threads",
            entities.size());
        long startTime = System.nanoTime();

        List<GeneratedFile> allFiles = Collections.synchronizedList(new ArrayList<>());

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (EntityDescriptor entity : entities) {
                futures.add(executor.submit(() -> {
                    List<GeneratedFile> entityFiles = generateForEntity(entity, config);
                    allFiles.addAll(entityFiles);
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Batch generation interrupted", e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new RuntimeException("Batch generation failed", e.getCause());
        }

        long durationMs = (System.nanoTime() - startTime) / 1_000_000;
        LOG.info("Batch generation completed: {} files in {}ms",
            allFiles.size(), durationMs);

        return allFiles;
    }

    private List<GeneratedFile> generateForEntity(EntityDescriptor entity,
            GenerationConfig config) {
        List<GeneratedFile> files = new ArrayList<>();
        for (Layer layer : config.layers()) {
            GeneratedFile file = templateRenderer.renderSingle(entity, layer, config);
            files.add(file);
        }
        LOG.debug("Generated {} files for entity {}", files.size(), entity.className());
        return files;
    }
}
