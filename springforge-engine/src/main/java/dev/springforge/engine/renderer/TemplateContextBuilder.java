package dev.springforge.engine.renderer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.springforge.engine.model.EntityDescriptor;
import dev.springforge.engine.model.FieldDescriptor;
import dev.springforge.engine.model.GenerationConfig;
import dev.springforge.engine.model.RelationType;

/**
 * Builds the template context map from EntityDescriptor + GenerationConfig.
 * All conditional logic is resolved here — templates remain logic-less.
 */
public final class TemplateContextBuilder {

    private TemplateContextBuilder() {}

    public static Map<String, Object> build(EntityDescriptor entity,
            GenerationConfig config) {
        Map<String, Object> ctx = new HashMap<>();

        Map<String, Object> entityMap = new HashMap<>();
        entityMap.put("name", entity.className());
        entityMap.put("nameLower", entity.classNameLower());
        entityMap.put("namePlural", entity.classNamePlural());
        entityMap.put("package", entity.packageName());
        entityMap.put("hasLombok", entity.hasLombok());

        Map<String, Object> idField = new HashMap<>();
        idField.put("name", entity.idFieldName());
        idField.put("type", entity.idFieldType());
        entityMap.put("idField", idField);

        List<Map<String, Object>> fieldMaps = new ArrayList<>();
        List<Map<String, Object>> nonIdFields = new ArrayList<>();
        for (FieldDescriptor field : entity.fields()) {
            Map<String, Object> fm = buildFieldMap(field);
            fieldMaps.add(fm);
            if (!field.isId()) {
                nonIdFields.add(fm);
            }
        }
        entityMap.put("fields", fieldMaps);
        entityMap.put("nonIdFields", nonIdFields);

        ctx.put("entity", entityMap);

        Map<String, Object> configMap = new HashMap<>();
        configMap.put("namespace", config.springVersion().namespace());
        configMap.put("basePackage", config.basePackage());
        configMap.put("dtoPackage", config.dtoPackage());
        configMap.put("mapperPackage", config.mapperPackage());
        configMap.put("servicePackage", config.servicePackage());
        configMap.put("controllerPackage", config.controllerPackage());
        configMap.put("repositoryPackage", config.repositoryPackage());
        configMap.put("apiPath", "/api/v1/" + entity.classNamePlural());
        configMap.put("springVersion", config.springVersion().name().substring(1));
        configMap.put("mapperLib", config.mapperLib().name().toLowerCase());
        configMap.put("useLombok", entity.hasLombok());
        configMap.put("useMapstruct",
            config.mapperLib() == dev.springforge.engine.model.MapperLib.MAPSTRUCT);
        ctx.put("config", configMap);

        return ctx;
    }

    private static Map<String, Object> buildFieldMap(FieldDescriptor field) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", field.name());
        map.put("type", field.type());
        map.put("isId", field.isId());
        map.put("isNullable", field.isNullable());
        map.put("isUnique", field.isUnique());
        map.put("isCircularRef", field.isCircularRef());
        map.put("hasRelation", field.relation() != RelationType.NONE);
        map.put("relatedEntityName", field.relatedEntityName());

        String nameCapitalized = field.name().isEmpty() ? ""
            : Character.toUpperCase(field.name().charAt(0)) + field.name().substring(1);
        map.put("nameCapitalized", nameCapitalized);

        if (field.isCircularRef() && field.relatedEntityName() != null) {
            map.put("dtoType", "Long");
            map.put("dtoFieldName", field.name() + "Id");
        } else if (field.relation() != RelationType.NONE
                && field.relatedEntityName() != null) {
            if (field.relation() == RelationType.ONE_TO_MANY
                    || field.relation() == RelationType.MANY_TO_MANY) {
                map.put("dtoType", "List<" + field.relatedEntityName() + "ResponseDto>");
                map.put("dtoFieldName", field.name());
            } else {
                map.put("dtoType", field.relatedEntityName() + "ResponseDto");
                map.put("dtoFieldName", field.name());
            }
        } else {
            map.put("dtoType", field.type());
            map.put("dtoFieldName", field.name());
        }

        return map;
    }
}
