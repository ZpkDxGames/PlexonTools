package com.plexon.tools.item;

import com.plexon.tools.model.LevelRequirement;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record ToolState(
        String toolId,
        UUID instanceId,
        int level,
        long progress,
        String boundWorld,
        UUID ownerId,
        Map<String, Long> targetProgress
) {
    public ToolState {
        Map<String, Long> normalized = new LinkedHashMap<>();
        targetProgress.forEach((target, value) -> {
            if (value != null && value > 0L) {
                normalized.put(LevelRequirement.normalize(target), value);
            }
        });
        targetProgress = Map.copyOf(normalized);
    }

    public ToolState(
            String toolId,
            UUID instanceId,
            int level,
            long progress,
            String boundWorld,
            UUID ownerId
    ) {
        this(toolId, instanceId, level, progress, boundWorld, ownerId, Map.of());
    }

    public ToolState withProgress(int newLevel, long newProgress) {
        Map<String, Long> breakdown = newLevel == level ? targetProgress : Map.of();
        return new ToolState(toolId, instanceId, newLevel, newProgress, boundWorld, ownerId, breakdown);
    }

    public ToolState withProgress(int newLevel, long newProgress, Map<String, Long> newTargetProgress) {
        return new ToolState(toolId, instanceId, newLevel, newProgress, boundWorld, ownerId,
                newTargetProgress);
    }
}
