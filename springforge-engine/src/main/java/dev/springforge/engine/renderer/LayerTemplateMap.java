package dev.springforge.engine.renderer;

import java.util.Map;

import dev.springforge.engine.model.Layer;
import dev.springforge.engine.model.MapperLib;

/**
 * Maps each Layer to its Mustache template path.
 */
public final class LayerTemplateMap {

    private static final Map<Layer, String> TEMPLATES = Map.of(
        Layer.DTO_REQUEST, "dto/RequestDto.java.mustache",
        Layer.DTO_RESPONSE, "dto/ResponseDto.java.mustache",
        Layer.MAPPER, "mapper/MapstructMapper.java.mustache",
        Layer.REPOSITORY, "repository/Repository.java.mustache",
        Layer.SERVICE, "service/Service.java.mustache",
        Layer.SERVICE_IMPL, "service/ServiceImpl.java.mustache",
        Layer.CONTROLLER, "controller/Controller.java.mustache",
        Layer.FILE_UPLOAD, "controller/FileController.java.mustache"
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
