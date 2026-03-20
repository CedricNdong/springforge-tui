package dev.springforge.engine.model;

public record FieldDescriptor(
    String name,
    String type,
    String genericType,
    boolean isId,
    boolean isNullable,
    boolean isUnique,
    RelationType relation,
    String relatedEntityName,
    boolean isCircularRef
) {

    public FieldDescriptor withCircularRef(boolean circular) {
        return new FieldDescriptor(
            name, type, genericType, isId, isNullable, isUnique,
            relation, relatedEntityName, circular
        );
    }
}
