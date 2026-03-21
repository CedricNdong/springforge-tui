package dev.springforge.engine.renderer;

import java.nio.file.Path;

import dev.springforge.engine.model.EntityDescriptor;
import dev.springforge.engine.model.GenerationConfig;
import dev.springforge.engine.model.Layer;

/**
 * Resolves the output file path for a given entity + layer combination.
 * All paths are relative to the configured outputBasePath.
 */
public final class OutputPathResolver {

    private OutputPathResolver() {}

    public static Path resolve(EntityDescriptor entity, Layer layer,
            GenerationConfig config) {
        String packagePath = switch (layer) {
            case DTO_REQUEST, DTO_RESPONSE ->
                config.dtoPackage().replace('.', '/');
            case MAPPER ->
                config.mapperPackage().replace('.', '/');
            case REPOSITORY ->
                config.repositoryPackage().replace('.', '/');
            case SERVICE ->
                config.servicePackage().replace('.', '/');
            case SERVICE_IMPL ->
                config.servicePackage().replace('.', '/') + "/impl";
            case CONTROLLER, FILE_UPLOAD ->
                config.controllerPackage().replace('.', '/');
            case LIQUIBASE ->
                "resources/db/changelog";
            case FLYWAY ->
                "resources/db/migration";
        };

        String fileName = FileNameResolver.resolve(entity.className(), layer);
        return config.outputBasePath().resolve(packagePath).resolve(fileName);
    }
}
