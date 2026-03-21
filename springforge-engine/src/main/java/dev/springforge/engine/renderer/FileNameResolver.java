package dev.springforge.engine.renderer;

import dev.springforge.engine.model.Layer;

/**
 * Resolves the output file name for a given entity + layer combination.
 */
public final class FileNameResolver {

    private FileNameResolver() {}

    public static String resolve(String entityClassName, Layer layer) {
        return switch (layer) {
            case DTO_REQUEST -> entityClassName + "RequestDto.java";
            case DTO_RESPONSE -> entityClassName + "ResponseDto.java";
            case MAPPER -> entityClassName + "Mapper.java";
            case REPOSITORY -> entityClassName + "Repository.java";
            case SERVICE -> entityClassName + "Service.java";
            case SERVICE_IMPL -> entityClassName + "ServiceImpl.java";
            case CONTROLLER -> entityClassName + "Controller.java";
            case FILE_UPLOAD -> entityClassName + "FileController.java";
            case LIQUIBASE -> "changelog_create_"
                + toSnakeCase(entityClassName) + ".xml";
            case FLYWAY -> "V001__create_"
                + toSnakeCase(entityClassName) + ".sql";
        };
    }

    private static String toSnakeCase(String className) {
        return className.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}
