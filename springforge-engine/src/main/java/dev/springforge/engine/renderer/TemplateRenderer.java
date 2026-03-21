package dev.springforge.engine.renderer;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;

import dev.springforge.engine.model.EntityDescriptor;
import dev.springforge.engine.model.GeneratedFile;
import dev.springforge.engine.model.GenerationConfig;
import dev.springforge.engine.model.Layer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renders Mustache templates into generated Java source files.
 * Templates are resolved from classpath (built-in) or user overrides.
 */
public class TemplateRenderer {

    private static final Logger LOG = LoggerFactory.getLogger(TemplateRenderer.class);

    private final MustacheFactory mustacheFactory;
    private final Path userTemplateDir;

    public TemplateRenderer() {
        this(null);
    }

    public TemplateRenderer(Path projectRoot) {
        this.mustacheFactory = new DefaultMustacheFactory() {
            @Override
            public void encode(String value, java.io.Writer writer) {
                try {
                    writer.write(value);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        this.userTemplateDir = projectRoot != null
            ? projectRoot.resolve(".springforge/templates")
            : null;
    }

    public List<GeneratedFile> renderAll(GenerationConfig config) {
        List<GeneratedFile> results = new ArrayList<>();

        for (EntityDescriptor entity : config.entities()) {
            for (Layer layer : config.layers()) {
                GeneratedFile file = renderSingle(entity, layer, config);
                results.add(file);
            }
        }

        return results;
    }

    public GeneratedFile renderSingle(EntityDescriptor entity, Layer layer,
            GenerationConfig config) {
        String templatePath = LayerTemplateMap.get(layer, config.mapperLib());
        Map<String, Object> context = TemplateContextBuilder.build(entity, config);

        String rendered = render(templatePath, context);

        var outputPath = OutputPathResolver.resolve(entity, layer, config);
        LOG.debug("Rendered {} for entity {}", templatePath, entity.className());

        return new GeneratedFile(outputPath, rendered, layer, entity.className());
    }

    String render(String templatePath, Map<String, Object> context) {
        try (Reader reader = resolveTemplate(templatePath)) {
            Mustache mustache = mustacheFactory.compile(reader, templatePath);
            StringWriter writer = new StringWriter();
            mustache.execute(writer, context);
            writer.flush();
            return writer.toString();
        } catch (IOException e) {
            throw new RuntimeException(
                "Failed to render template: " + templatePath, e);
        }
    }

    private Reader resolveTemplate(String templatePath) throws IOException {
        if (userTemplateDir != null) {
            Path userTemplate = userTemplateDir.resolve(templatePath);
            if (Files.exists(userTemplate)) {
                LOG.debug("Using user template override: {}", userTemplate);
                return new StringReader(
                    Files.readString(userTemplate, StandardCharsets.UTF_8));
            }
        }

        var is = getClass().getResourceAsStream("/templates/" + templatePath);
        if (is == null) {
            throw new IOException("Template not found on classpath: " + templatePath);
        }
        return new InputStreamReader(is, StandardCharsets.UTF_8);
    }
}
