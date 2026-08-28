package com.plexon.tools.model;

import java.util.Locale;

public enum TrackingType {
    BLOCKS_BROKEN("Blocks broken"),
    MOBS_KILLED("Mobs killed");

    private final String displayName;

    TrackingType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public TrackingType next() {
        TrackingType[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static TrackingType parse(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
