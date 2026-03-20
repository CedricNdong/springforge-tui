package dev.springforge.engine.model;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;

public record GenerationConfig(
    List<EntityDescriptor> entities,
    EnumSet<Layer> layers,
    SpringVersion springVersion,
    MapperLib mapperLib,
    ConflictStrategy conflictStrategy,
    Path outputBasePath,
    String basePackage,
    boolean dryRun,
    boolean verbose
) {

    public String dtoPackage() {
        return basePackage + ".dto";
    }

    public String mapperPackage() {
        return basePackage + ".mapper";
    }

    public String repositoryPackage() {
        return basePackage + ".repository";
    }

    public String servicePackage() {
        return basePackage + ".service";
    }

    public String controllerPackage() {
        return basePackage + ".controller";
    }
}
