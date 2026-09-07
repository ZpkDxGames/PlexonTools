package com.plexon.tools.api;

import org.bukkit.Material;

import java.util.Objects;
import java.util.Set;

/** Immutable public view of one configured progressive tool definition. */
public record ToolDefinitionView(
        String id,
        boolean enabled,
        String displayName,
        Material baseMaterial,
        Set<String> allowedWorlds,
        String category,
        String progressionScope,
        String progressionAnchorWorld,
        String progressType,
        int maxLevel
) {
    public ToolDefinitionView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(baseMaterial, "baseMaterial");
        allowedWorlds = Set.copyOf(allowedWorlds);
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(progressionScope, "progressionScope");
        Objects.requireNonNull(progressionAnchorWorld, "progressionAnchorWorld");
        Objects.requireNonNull(progressType, "progressType");
    }
}
