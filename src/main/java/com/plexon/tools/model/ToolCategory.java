package com.plexon.tools.model;

import org.bukkit.Material;

import java.util.List;

public record ToolCategory(
        String id,
        String displayName,
        Material icon,
        int slot,
        List<String> description
) {
    public ToolCategory {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Category ID is required.");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Category display name is required.");
        }
        if (icon == null || !icon.isItem()) {
            throw new IllegalArgumentException("Category icon must be an item material.");
        }
        if (slot < 0 || slot > 53) {
            throw new IllegalArgumentException("Category slot must be between 0 and 53.");
        }
        description = List.copyOf(description);
    }
}
