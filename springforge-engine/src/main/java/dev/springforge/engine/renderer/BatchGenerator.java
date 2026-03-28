package dev.springforge.engine.renderer;

import java.util.ArrayList;
import java.util.List;

import dev.springforge.engine.model.EntityDescriptor;
import dev.springforge.engine.model.GeneratedFile;
import dev.springforge.engine.model.GenerationConfig;
import dev.springforge.engine.model.Layer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Batch generator that renders all layers for all entities sequentially.
 * Template rendering is CPU-bound and fast — parallelism adds overhead
 * without meaningful gain for Mustache templates.
 */
public class BatchGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(BatchGenerator.class);

    private final TemplateRenderer templateRenderer;

    public BatchGenerator(TemplateRenderer templateRenderer) {
        this.templateRenderer = templateRenderer;
    }

    /**
     * Generates all layers for all entities sequentially.
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

        LOG.info("Starting batch generation for {} entities", entities.size());
        long startTime = System.nanoTime();

        List<GeneratedFile> allFiles = new ArrayList<>();
        for (EntityDescriptor entity : entities) {
            for (Layer layer : config.layers()) {
                GeneratedFile file = templateRenderer.renderSingle(entity, layer, config);
                allFiles.add(file);
            }
            LOG.debug("Generated {} layers for entity {}",
                config.layers().size(), entity.className());
        }

        long durationMs = (System.nanoTime() - startTime) / 1_000_000;
        LOG.info("Batch generation completed: {} files in {}ms",
            allFiles.size(), durationMs);

        return allFiles;
    }
}
