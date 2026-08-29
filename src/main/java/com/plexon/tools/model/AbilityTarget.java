package com.plexon.tools.model;

import java.util.Locale;

public enum AbilityTarget {
    HOLDER,
    TARGET;

    public AbilityTarget next() {
        return this == HOLDER ? TARGET : HOLDER;
    }

    public static AbilityTarget parse(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
