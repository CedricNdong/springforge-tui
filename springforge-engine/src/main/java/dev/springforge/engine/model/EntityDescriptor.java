package dev.springforge.engine.model;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public record EntityDescriptor(
    String className,
    String packageName,
    List<FieldDescriptor> fields,
    Set<String> classAnnotations,
    SpringNamespace namespace,
    boolean hasLombok,
    String idFieldName,
    String idFieldType
) {

    public Optional<FieldDescriptor> idField() {
        return fields.stream()
            .filter(FieldDescriptor::isId)
            .findFirst();
    }

    public String classNameLower() {
        if (className.isEmpty()) {
            return "";
        }
        return Character.toLowerCase(className.charAt(0)) + className.substring(1);
    }

    public String classNamePlural() {
        String lower = classNameLower();
        if (lower.endsWith("s") || lower.endsWith("x") || lower.endsWith("z")
                || lower.endsWith("sh") || lower.endsWith("ch")) {
            return lower + "es";
        }
        if (lower.endsWith("y") && lower.length() > 1
                && !isVowel(lower.charAt(lower.length() - 2))) {
            return lower.substring(0, lower.length() - 1) + "ies";
        }
        return lower + "s";
    }

    private static boolean isVowel(char c) {
        return "aeiou".indexOf(Character.toLowerCase(c)) >= 0;
    }
}
