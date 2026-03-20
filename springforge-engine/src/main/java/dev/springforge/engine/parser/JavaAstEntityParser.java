package dev.springforge.engine.parser;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

import dev.springforge.engine.model.EntityDescriptor;
import dev.springforge.engine.model.FieldDescriptor;
import dev.springforge.engine.model.RelationType;
import dev.springforge.engine.model.SpringNamespace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses Java @Entity classes into EntityDescriptor via JavaParser AST.
 * No runtime reflection or classloading — pure source analysis.
 */
public class JavaAstEntityParser {

    private static final Logger LOG = LoggerFactory.getLogger(JavaAstEntityParser.class);

    private static final Set<String> LOMBOK_ANNOTATIONS = Set.of(
        "Data", "Getter", "Setter", "Builder", "NoArgsConstructor",
        "AllArgsConstructor", "RequiredArgsConstructor", "Value"
    );

    private static final Map<String, RelationType> RELATION_MAP = Map.of(
        "OneToOne", RelationType.ONE_TO_ONE,
        "OneToMany", RelationType.ONE_TO_MANY,
        "ManyToOne", RelationType.MANY_TO_ONE,
        "ManyToMany", RelationType.MANY_TO_MANY
    );

    static {
        StaticJavaParser.getParserConfiguration()
            .setLanguageLevel(ParserConfiguration.LanguageLevel.RAW);
    }

    public EntityDescriptor parse(Path javaFile) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(javaFile);

        ClassOrInterfaceDeclaration clazz = cu.findFirst(ClassOrInterfaceDeclaration.class)
            .orElseThrow(() -> new IllegalArgumentException(
                "No class found in " + javaFile));

        String packageName = cu.getPackageDeclaration()
            .map(pd -> pd.getNameAsString())
            .orElse("");

        SpringNamespace namespace = detectNamespace(cu);
        Set<String> classAnnotations = extractAnnotationNames(clazz);
        boolean hasLombok = classAnnotations.stream()
            .anyMatch(LOMBOK_ANNOTATIONS::contains);

        List<FieldDescriptor> fields = new ArrayList<>();
        String idFieldName = "";
        String idFieldType = "";

        for (FieldDeclaration field : clazz.getFields()) {
            if (hasEmbeddedAnnotation(field)) {
                LOG.warn("@Embedded not supported in v1 — field skipped: {}",
                    field.getVariables().getFirst()
                        .map(VariableDeclarator::getNameAsString)
                        .orElse("unknown"));
                continue;
            }

            for (VariableDeclarator var : field.getVariables()) {
                FieldDescriptor fd = buildFieldDescriptor(field, var);
                fields.add(fd);
                if (fd.isId()) {
                    idFieldName = fd.name();
                    idFieldType = fd.type();
                }
            }
        }

        return new EntityDescriptor(
            clazz.getNameAsString(),
            packageName,
            fields,
            classAnnotations,
            namespace,
            hasLombok,
            idFieldName,
            idFieldType
        );
    }

    /**
     * Detects circular references across a batch of parsed entities.
     * Returns new list with isCircularRef flags set on back-reference fields.
     */
    public List<EntityDescriptor> resolveCircularRefs(List<EntityDescriptor> entities) {
        Set<String> entityNames = new HashSet<>();
        for (EntityDescriptor e : entities) {
            entityNames.add(e.className());
        }

        Map<String, Set<String>> graph = new HashMap<>();
        for (EntityDescriptor entity : entities) {
            Set<String> targets = new HashSet<>();
            for (FieldDescriptor field : entity.fields()) {
                if (field.relation() != RelationType.NONE
                        && field.relatedEntityName() != null
                        && entityNames.contains(field.relatedEntityName())) {
                    targets.add(field.relatedEntityName());
                }
            }
            graph.put(entity.className(), targets);
        }

        Set<String> circularPairs = new HashSet<>();
        for (Map.Entry<String, Set<String>> entry : graph.entrySet()) {
            String source = entry.getKey();
            for (String target : entry.getValue()) {
                Set<String> targetRefs = graph.getOrDefault(target, Set.of());
                if (targetRefs.contains(source)) {
                    circularPairs.add(target + "->" + source);
                }
            }
        }

        if (circularPairs.isEmpty()) {
            return entities;
        }

        List<EntityDescriptor> resolved = new ArrayList<>();
        for (EntityDescriptor entity : entities) {
            List<FieldDescriptor> updatedFields = new ArrayList<>();
            for (FieldDescriptor field : entity.fields()) {
                String key = entity.className() + "->" + field.relatedEntityName();
                if (circularPairs.contains(key)) {
                    updatedFields.add(field.withCircularRef(true));
                } else {
                    updatedFields.add(field);
                }
            }
            resolved.add(new EntityDescriptor(
                entity.className(), entity.packageName(), updatedFields,
                entity.classAnnotations(), entity.namespace(), entity.hasLombok(),
                entity.idFieldName(), entity.idFieldType()
            ));
        }
        return resolved;
    }

    private FieldDescriptor buildFieldDescriptor(FieldDeclaration field,
            VariableDeclarator var) {
        String name = var.getNameAsString();
        String type = var.getTypeAsString();
        String genericType = extractGenericType(var.getType());
        boolean isId = hasAnnotation(field, "Id");
        boolean isNullable = !hasAnnotation(field, "Column")
            || !hasColumnNonNull(field);
        boolean isUnique = hasColumnUnique(field);
        RelationType relation = detectRelation(field);
        String relatedEntityName = (relation != RelationType.NONE)
            ? (genericType != null ? genericType : type)
            : null;

        return new FieldDescriptor(
            name, type, genericType, isId, isNullable,
            isUnique, relation, relatedEntityName, false
        );
    }

    private SpringNamespace detectNamespace(CompilationUnit cu) {
        for (ImportDeclaration imp : cu.getImports()) {
            String importName = imp.getNameAsString();
            if (importName.startsWith("jakarta.persistence")) {
                return SpringNamespace.JAKARTA;
            }
            if (importName.startsWith("javax.persistence")) {
                return SpringNamespace.JAVAX;
            }
        }
        return SpringNamespace.JAKARTA;
    }

    private Set<String> extractAnnotationNames(ClassOrInterfaceDeclaration clazz) {
        Set<String> names = new HashSet<>();
        for (AnnotationExpr ann : clazz.getAnnotations()) {
            names.add(ann.getNameAsString());
        }
        return names;
    }

    private boolean hasAnnotation(FieldDeclaration field, String name) {
        return field.getAnnotations().stream()
            .anyMatch(a -> a.getNameAsString().equals(name));
    }

    private boolean hasEmbeddedAnnotation(FieldDeclaration field) {
        return hasAnnotation(field, "Embedded")
            || hasAnnotation(field, "EmbeddedId");
    }

    private boolean hasColumnNonNull(FieldDeclaration field) {
        return field.getAnnotationByName("Column")
            .flatMap(a -> a.toNormalAnnotationExpr())
            .map(a -> a.getPairs().stream()
                .anyMatch(p -> p.getNameAsString().equals("nullable")
                    && p.getValue().toString().equals("false")))
            .orElse(false);
    }

    private boolean hasColumnUnique(FieldDeclaration field) {
        return field.getAnnotationByName("Column")
            .flatMap(a -> a.toNormalAnnotationExpr())
            .map(a -> a.getPairs().stream()
                .anyMatch(p -> p.getNameAsString().equals("unique")
                    && p.getValue().toString().equals("true")))
            .orElse(false);
    }

    private RelationType detectRelation(FieldDeclaration field) {
        for (AnnotationExpr ann : field.getAnnotations()) {
            RelationType rel = RELATION_MAP.get(ann.getNameAsString());
            if (rel != null) {
                return rel;
            }
        }
        return RelationType.NONE;
    }

    private String extractGenericType(Type type) {
        if (type instanceof ClassOrInterfaceType classType) {
            return classType.getTypeArguments()
                .filter(args -> !args.isEmpty())
                .map(args -> args.get(0).asString())
                .orElse(null);
        }
        return null;
    }
}
