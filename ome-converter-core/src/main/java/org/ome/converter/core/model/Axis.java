package org.ome.converter.core.model;

public enum Axis {
    T("t", "time", "second"),
    C("c", "channel", null),
    Z("z", "space", "micrometer"),
    Y("y", "space", "micrometer"),
    X("x", "space", "micrometer");

    private final String name;
    private final String type;
    private final String defaultUnit;

    Axis(String name, String type, String defaultUnit) {
        this.name = name;
        this.type = type;
        this.defaultUnit = defaultUnit;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getDefaultUnit() {
        return defaultUnit;
    }
}
