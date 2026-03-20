package dev.springforge.engine.model;

public enum SpringNamespace {
    JAVAX("javax"),
    JAKARTA("jakarta");

    private final String packagePrefix;

    SpringNamespace(String packagePrefix) {
        this.packagePrefix = packagePrefix;
    }

    public String packagePrefix() {
        return packagePrefix;
    }
}
