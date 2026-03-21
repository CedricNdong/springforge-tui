package dev.springforge.engine.renderer;

import java.util.Map;

import dev.springforge.engine.model.Layer;
import dev.springforge.engine.model.MapperLib;

/**
 * Maps each Layer to its Mustache template path.
 */
public final class LayerTemplateMap {

    private static final Map<Layer, String> TEMPLATES = Map.ofEntries(
        Map.entry(Layer.DTO_REQUEST, "dto/RequestDto.java.mustache"),
        Map.entry(Layer.DTO_RESPONSE, "dto/ResponseDto.java.mustache"),
        Map.entry(Layer.MAPPER, "mapper/MapstructMapper.java.mustache"),
        Map.entry(Layer.REPOSITORY, "repository/Repository.java.mustache"),
        Map.entry(Layer.SERVICE, "service/Service.java.mustache"),
        Map.entry(Layer.SERVICE_IMPL, "service/ServiceImpl.java.mustache"),
        Map.entry(Layer.CONTROLLER, "controller/Controller.java.mustache"),
        Map.entry(Layer.FILE_UPLOAD, "controller/FileController.java.mustache"),
        Map.entry(Layer.LIQUIBASE, "migration/Liquibase.xml.mustache"),
        Map.entry(Layer.FLYWAY, "migration/Flyway.sql.mustache")
    );

    private LayerTemplateMap() {}

    public static String get(Layer layer, MapperLib mapperLib) {
        if (layer == Layer.MAPPER && mapperLib == MapperLib.MODEL_MAPPER) {
            return "mapper/ModelMapperConfig.java.mustache";
        }
        String template = TEMPLATES.get(layer);
        if (template == null) {
            throw new IllegalArgumentException("No template for layer: " + layer);
        }
        return template;
    }
}
