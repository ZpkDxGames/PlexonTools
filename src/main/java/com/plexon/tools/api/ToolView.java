package com.plexon.tools.api;

import org.bukkit.Material;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Immutable snapshot of one issued PlexonTools instance. */
public record ToolView(
        UUID instanceId,
        String toolId,
        String category,
        UUID ownerId,
        String boundWorld,
        int level,
        long progress,
        Map<String, Long> targetProgress,
        String progressType,
        Material material
) {
    public ToolView {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(toolId, "toolId");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(boundWorld, "boundWorld");
        Objects.requireNonNull(progressType, "progressType");
        targetProgress = Map.copyOf(targetProgress == null ? Map.of() : targetProgress);
    }
}
