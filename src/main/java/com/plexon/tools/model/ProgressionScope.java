package com.plexon.tools.model;

import java.util.Locale;

/**
 * Defines which world boundary owns a player's progress for one tool definition.
 */
public enum ProgressionScope {
    /** Each allowed world keeps its own instance and progression state. */
    WORLD,
    /** One canonical owner/tool instance is used across every allowed world. */
    PLAYER;

    public static ProgressionScope parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Progression scope cannot be blank.");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "PLAYER", "SHARED", "GLOBAL", "CROSS_WORLD" -> PLAYER;
            case "WORLD", "PER_WORLD" -> WORLD;
            default -> throw new IllegalArgumentException(
                    "Unknown progression scope '" + value + "'. Use PLAYER or WORLD.");
        };
    }
}
