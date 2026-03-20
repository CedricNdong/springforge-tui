package dev.springforge.engine.parser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.springforge.engine.model.EntityDescriptor;
import dev.springforge.engine.model.FieldDescriptor;
import dev.springforge.engine.model.RelationType;
import dev.springforge.engine.model.SpringNamespace;

import org.yaml.snakeyaml.Yaml;

/**
 * Parses YAML entity definitions into EntityDescriptor objects.
 * Produces the same model as JavaAstEntityParser for interchangeable use.
 */
public class YamlEntityParser {

    private static final Map<String, RelationType> RELATION_MAP = Map.of(
        "OneToOne", RelationType.ONE_TO_ONE,
        "OneToMany", RelationType.ONE_TO_MANY,
        "ManyToOne", RelationType.MANY_TO_ONE,
        "ManyToMany", RelationType.MANY_TO_MANY
    );

    @SuppressWarnings("unchecked")
    public List<EntityDescriptor> parse(Path yamlFile) throws IOException {
        Map<String, Object> root;
        try (InputStream is = Files.newInputStream(yamlFile)) {
            Yaml yaml = new Yaml();
            root = yaml.load(is);
        }

        if (root == null || !root.containsKey("entities")) {
            throw new IllegalArgumentException(
                "Invalid YAML: missing 'entities' key in " + yamlFile);
        }

        List<Map<String, Object>> entityDefs =
            (List<Map<String, Object>>) root.get("entities");

        List<EntityDescriptor> result = new ArrayList<>();
        for (Map<String, Object> entityDef : entityDefs) {
            result.add(parseEntity(entityDef));
        }
        return Collections.unmodifiableList(result);
    }

    @SuppressWarnings("unchecked")
    private EntityDescriptor parseEntity(Map<String, Object> def) {
        String name = requireString(def, "name");
        String pkg = getStringOrDefault(def, "package", "");
        boolean lombok = getBoolOrDefault(def, "lombok", false);

        Set<String> annotations = new HashSet<>();
        annotations.add("Entity");
        if (lombok) {
            annotations.add("Data");
        }

        List<Map<String, Object>> fieldDefs =
            (List<Map<String, Object>>) def.getOrDefault("fields", List.of());

        List<FieldDescriptor> fields = new ArrayList<>();
        String idFieldName = "";
        String idFieldType = "";

        for (Map<String, Object> fieldDef : fieldDefs) {
            FieldDescriptor fd = parseField(fieldDef);
            fields.add(fd);
            if (fd.isId()) {
                idFieldName = fd.name();
                idFieldType = fd.type();
            }
        }

        return new EntityDescriptor(
            name, pkg, fields, annotations,
            SpringNamespace.JAKARTA, lombok, idFieldName, idFieldType
        );
    }

    private FieldDescriptor parseField(Map<String, Object> def) {
        String name = requireString(def, "name");
        String type = requireString(def, "type");
        boolean isId = getBoolOrDefault(def, "id", false);
        boolean isNullable = getBoolOrDefault(def, "nullable", true);
        boolean isUnique = getBoolOrDefault(def, "unique", false);

        String relationStr = getStringOrDefault(def, "relation", null);
        RelationType relation = RelationType.NONE;
        String relatedEntityName = null;
        String genericType = null;

        if (relationStr != null) {
            relation = RELATION_MAP.getOrDefault(relationStr, RelationType.NONE);
            if (relation == RelationType.ONE_TO_MANY
                    || relation == RelationType.MANY_TO_MANY) {
                genericType = type;
                relatedEntityName = type;
                type = "List<" + type + ">";
            } else {
                relatedEntityName = type;
            }
        }

        return new FieldDescriptor(
            name, type, genericType, isId, isNullable,
            isUnique, relation, relatedEntityName, false
        );
    }

    private static String requireString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required field: " + key);
        }
        return value.toString();
    }

    private static String getStringOrDefault(Map<String, Object> map,
            String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private static boolean getBoolOrDefault(Map<String, Object> map,
            String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value instanceof Boolean b) {
            return b;
        }
        return defaultValue;
    }
}
