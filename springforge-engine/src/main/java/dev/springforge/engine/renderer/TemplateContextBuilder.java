package dev.springforge.engine.renderer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

        Set<String> dtoImports = collectDtoImports(entity.fields(), false);
        entityMap.put("dtoImports", dtoImports.stream()
            .map(imp -> Map.of("import", imp))
            .toList());

        Set<String> requestDtoImports = collectDtoImports(entity.fields(), true);
        entityMap.put("requestDtoImports", requestDtoImports.stream()
            .map(imp -> Map.of("import", imp))
            .toList());

        entityMap.put("tableName", toSnakeCase(entity.className()));
        List<Map<String, Object>> columns = new ArrayList<>();
        for (int i = 0; i < entity.fields().size(); i++) {
            FieldDescriptor field = entity.fields().get(i);
            if (field.relation() != RelationType.NONE) {
                continue;
            }
            Map<String, Object> col = new HashMap<>();
            col.put("columnName", toSnakeCase(field.name()));
            col.put("columnType", javaTypeToSqlType(field.type()));
            col.put("isPrimaryKey", field.isId());
            col.put("isNullable", field.isNullable());
            col.put("isUnique", field.isUnique());
            col.put("isLast", false);
            columns.add(col);
        }
        if (!columns.isEmpty()) {
            columns.get(columns.size() - 1).put("isLast", true);
        }
        entityMap.put("columns", columns);

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

        boolean needsMapperIgnore = false;
        boolean hasResponseSourceMapping = false;
        boolean responseMapperIgnore = false;
        if (field.isCircularRef() && field.relatedEntityName() != null) {
            boolean isCollection = field.relation() == RelationType.ONE_TO_MANY
                    || field.relation() == RelationType.MANY_TO_MANY;
            if (isCollection) {
                // Circular ref collection: ignore in ResponseDto (can't map list.id)
                map.put("dtoType", "List<Long>");
                map.put("dtoFieldName", field.name() + "Ids");
                responseMapperIgnore = true;
            } else {
                // Circular ref single: flatten to Long ID
                map.put("dtoType", "Long");
                map.put("dtoFieldName", field.name() + "Id");
                map.put("responseSource", field.name() + ".id");
                map.put("responseTarget", field.name() + "Id");
                hasResponseSourceMapping = true;
            }
            map.put("requestDtoType", "Long");
            map.put("requestDtoFieldName", field.name() + "Id");
            needsMapperIgnore = true;
        } else if (field.relation() != RelationType.NONE
                && field.relatedEntityName() != null) {
            if (field.relation() == RelationType.ONE_TO_MANY
                    || field.relation() == RelationType.MANY_TO_MANY) {
                // Response DTO: list relationships are ignored (complex mapping)
                map.put("dtoType", "List<" + field.relatedEntityName() + "ResponseDto>");
                map.put("dtoFieldName", field.name());
                responseMapperIgnore = true;
            } else {
                // Response DTO: @ManyToOne/@OneToOne → flatten to Long ID
                map.put("dtoType", "Long");
                map.put("dtoFieldName", field.name() + "Id");
                map.put("responseSource", field.name() + ".id");
                map.put("responseTarget", field.name() + "Id");
                hasResponseSourceMapping = true;
            }
            // Request DTO: always flatten to Long ID
            map.put("requestDtoType", "Long");
            map.put("requestDtoFieldName", field.name() + "Id");
            needsMapperIgnore = true;
        } else {
            map.put("dtoType", field.type());
            map.put("dtoFieldName", field.name());
            map.put("requestDtoType", field.type());
            map.put("requestDtoFieldName", field.name());
        }
        map.put("mapperIgnore", needsMapperIgnore);
        map.put("hasResponseSourceMapping", hasResponseSourceMapping);
        map.put("responseMapperIgnore", responseMapperIgnore);

        String dtoFieldName = (String) map.get("dtoFieldName");
        String dtoCapitalized = dtoFieldName.isEmpty() ? ""
            : Character.toUpperCase(dtoFieldName.charAt(0))
                + dtoFieldName.substring(1);
        map.put("dtoFieldNameCapitalized", dtoCapitalized);

        String reqFieldName = (String) map.get("requestDtoFieldName");
        String reqCapitalized = reqFieldName.isEmpty() ? ""
            : Character.toUpperCase(reqFieldName.charAt(0))
                + reqFieldName.substring(1);
        map.put("requestDtoFieldNameCapitalized", reqCapitalized);

        return map;
    }

    private static final Map<String, String> TYPE_IMPORTS = Map.ofEntries(
        Map.entry("BigDecimal", "java.math.BigDecimal"),
        Map.entry("BigInteger", "java.math.BigInteger"),
        Map.entry("LocalDate", "java.time.LocalDate"),
        Map.entry("LocalDateTime", "java.time.LocalDateTime"),
        Map.entry("LocalTime", "java.time.LocalTime"),
        Map.entry("Instant", "java.time.Instant"),
        Map.entry("ZonedDateTime", "java.time.ZonedDateTime"),
        Map.entry("OffsetDateTime", "java.time.OffsetDateTime"),
        Map.entry("Duration", "java.time.Duration"),
        Map.entry("UUID", "java.util.UUID"),
        Map.entry("List", "java.util.List"),
        Map.entry("Set", "java.util.Set"),
        Map.entry("Map", "java.util.Map")
    );

    private static Set<String> collectDtoImports(List<FieldDescriptor> fields,
                                                   boolean forRequest) {
        Set<String> imports = new LinkedHashSet<>();
        for (FieldDescriptor field : fields) {
            if (field.isId()) {
                continue;
            }
            boolean hasRelation = field.relation() != RelationType.NONE
                    && field.relatedEntityName() != null;

            if (forRequest && hasRelation) {
                // Request DTOs flatten relationships to Long — no List/DTO imports
                continue;
            }

            String type = field.type();
            if (TYPE_IMPORTS.containsKey(type)) {
                imports.add(TYPE_IMPORTS.get(type));
            }
            if (type.startsWith("List<")) {
                imports.add("java.util.List");
            } else if (type.startsWith("Set<")) {
                imports.add("java.util.Set");
            }
            if (!forRequest
                    && (field.relation() == RelationType.ONE_TO_MANY
                        || field.relation() == RelationType.MANY_TO_MANY)
                    && field.relatedEntityName() != null) {
                imports.add("java.util.List");
            }
        }
        return imports;
    }

    private static String toSnakeCase(String name) {
        return name.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    private static final Map<String, String> SQL_TYPE_MAP = Map.ofEntries(
        Map.entry("Long", "BIGINT"),
        Map.entry("long", "BIGINT"),
        Map.entry("Integer", "INTEGER"),
        Map.entry("int", "INTEGER"),
        Map.entry("String", "VARCHAR(255)"),
        Map.entry("Boolean", "BOOLEAN"),
        Map.entry("boolean", "BOOLEAN"),
        Map.entry("Double", "DOUBLE"),
        Map.entry("double", "DOUBLE"),
        Map.entry("Float", "FLOAT"),
        Map.entry("float", "FLOAT"),
        Map.entry("BigDecimal", "DECIMAL(19,2)"),
        Map.entry("LocalDate", "DATE"),
        Map.entry("LocalDateTime", "TIMESTAMP"),
        Map.entry("Instant", "TIMESTAMP"),
        Map.entry("UUID", "UUID"),
        Map.entry("byte[]", "BLOB"),
        Map.entry("Short", "SMALLINT"),
        Map.entry("short", "SMALLINT")
    );

    private static String javaTypeToSqlType(String javaType) {
        return SQL_TYPE_MAP.getOrDefault(javaType, "VARCHAR(255)");
    }
}
