package dev.springforge.engine.model;

public enum SpringVersion {
    V2("javax"),
    V3("jakarta");

    private final String namespace;

    SpringVersion(String namespace) {
        this.namespace = namespace;
    }

    public String namespace() {
        return namespace;
    }
}
