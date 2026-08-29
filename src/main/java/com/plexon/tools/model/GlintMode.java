package com.plexon.tools.model;

import java.util.Locale;

public enum GlintMode {
    AUTO(null, "Automatic"),
    ON(Boolean.TRUE, "Always on"),
    OFF(Boolean.FALSE, "Always off");

    private final Boolean override;
    private final String displayName;

    GlintMode(Boolean override, String displayName) {
        this.override = override;
        this.displayName = displayName;
    }

    public Boolean override() {
        return override;
    }

    public String displayName() {
        return displayName;
    }

    public GlintMode next() {
        GlintMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static GlintMode parse(String value) {
        if (value == null || value.isBlank()) {
            return AUTO;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "TRUE", "FORCE", "FORCED", "ENABLED" -> ON;
            case "FALSE", "DISABLED" -> OFF;
            default -> valueOf(value.trim().toUpperCase(Locale.ROOT));
        };
    }
}
