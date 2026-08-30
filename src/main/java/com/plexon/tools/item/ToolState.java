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
        String categoryId,
        Map<String, Long> targetProgress
) {
    public ToolState {
        if (targetProgress.isEmpty()) {
            targetProgress = Map.of();
        } else {
            Map<String, Long> normalized = new LinkedHashMap<>();
            targetProgress.forEach((target, value) -> {
                if (value != null && value > 0L) {
                    normalized.put(LevelRequirement.normalize(target), value);
                }
            });
            targetProgress = normalized.isEmpty() ? Map.of() : Map.copyOf(normalized);
        }
        categoryId = categoryId == null ? "" : categoryId.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public ToolState(
            String toolId,
            UUID instanceId,
            int level,
            long progress,
            String boundWorld,
            UUID ownerId
    ) {
        this(toolId, instanceId, level, progress, boundWorld, ownerId, "", Map.of());
    }

    public ToolState(
            String toolId,
            UUID instanceId,
            int level,
            long progress,
            String boundWorld,
            UUID ownerId,
            Map<String, Long> targetProgress
    ) {
        this(toolId, instanceId, level, progress, boundWorld, ownerId, "", targetProgress);
    }

    public ToolState withProgress(int newLevel, long newProgress) {
        Map<String, Long> breakdown = newLevel == level ? targetProgress : Map.of();
        return new ToolState(toolId, instanceId, newLevel, newProgress, boundWorld, ownerId,
                categoryId, breakdown);
    }

    public ToolState withProgress(int newLevel, long newProgress, Map<String, Long> newTargetProgress) {
        return new ToolState(toolId, instanceId, newLevel, newProgress, boundWorld, ownerId,
                categoryId, newTargetProgress);
    }

    public ToolState withCategory(String newCategoryId) {
        return new ToolState(toolId, instanceId, level, progress, boundWorld, ownerId,
                newCategoryId, targetProgress);
    }

    public ToolState withBoundWorld(String newBoundWorld) {
        return new ToolState(toolId, instanceId, level, progress, newBoundWorld, ownerId,
                categoryId, targetProgress);
    }
}
