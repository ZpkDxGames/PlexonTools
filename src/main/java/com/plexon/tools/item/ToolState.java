package com.plexon.tools.item;

import java.util.UUID;

public record ToolState(
        String toolId,
        UUID instanceId,
        int level,
        long progress,
        String boundWorld,
        UUID ownerId
) {
    public ToolState withProgress(int newLevel, long newProgress) {
        return new ToolState(toolId, instanceId, newLevel, newProgress, boundWorld, ownerId);
    }
}
