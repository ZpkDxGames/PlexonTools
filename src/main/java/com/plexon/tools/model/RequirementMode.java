package com.plexon.tools.model;

import java.util.Locale;

public enum RequirementMode {
    GENERAL("General total"),
    SPECIFIC("Specific quotas");

    private final String displayName;

    RequirementMode(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public RequirementMode next() {
        return this == GENERAL ? SPECIFIC : GENERAL;
    }

    public static RequirementMode parse(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
