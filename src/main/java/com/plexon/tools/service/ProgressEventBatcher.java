package com.plexon.tools.service;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Main-thread accumulator for high-frequency public progression notifications.
 * Authoritative tool state is committed before entries reach this batcher.
 */
final class ProgressEventBatcher {
    private final Map<UUID, ArrayList<PendingProgress>> byInstance = new LinkedHashMap<>();
    private int groupCount;

    void add(
            UUID playerId,
            UUID instanceId,
            String toolId,
            String category,
            String progressType,
            Material material,
            int level,
            long amount
    ) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(toolId, "toolId");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(progressType, "progressType");
        if (level < 1 || amount <= 0L) {
            throw new IllegalArgumentException("level and amount must be positive");
        }

        ArrayList<PendingProgress> groups = byInstance.computeIfAbsent(
                instanceId, ignored -> new ArrayList<>(2));
        for (PendingProgress pending : groups) {
            if (!pending.matches(playerId, toolId, category, progressType, material, level)) {
                continue;
            }
            if (Long.MAX_VALUE - pending.amount >= amount) {
                pending.amount += amount;
                return;
            }
            // Keep totals exact instead of saturating if an impossible-in-practice
            // amount would overflow a single public event.
            break;
        }
        groups.add(new PendingProgress(playerId, instanceId, toolId, category,
                progressType, material, level, amount));
        groupCount++;
    }

    List<PendingProgress> drainAll() {
        if (groupCount == 0) {
            return List.of();
        }
        ArrayList<PendingProgress> drained = new ArrayList<>(groupCount);
        for (ArrayList<PendingProgress> groups : byInstance.values()) {
            drained.addAll(groups);
        }
        byInstance.clear();
        groupCount = 0;
        return drained;
    }

    List<PendingProgress> drainInstance(UUID instanceId) {
        ArrayList<PendingProgress> drained = byInstance.remove(instanceId);
        if (drained == null || drained.isEmpty()) {
            return List.of();
        }
        groupCount -= drained.size();
        return drained;
    }

    List<PendingProgress> drainPlayer(UUID playerId) {
        if (groupCount == 0) {
            return List.of();
        }
        ArrayList<PendingProgress> drained = new ArrayList<>();
        Iterator<Map.Entry<UUID, ArrayList<PendingProgress>>> entries =
                byInstance.entrySet().iterator();
        while (entries.hasNext()) {
            ArrayList<PendingProgress> groups = entries.next().getValue();
            groups.removeIf(pending -> {
                if (!pending.playerId.equals(playerId)) {
                    return false;
                }
                drained.add(pending);
                groupCount--;
                return true;
            });
            if (groups.isEmpty()) {
                entries.remove();
            }
        }
        return drained.isEmpty() ? List.of() : drained;
    }

    int size() {
        return groupCount;
    }

    void clear() {
        byInstance.clear();
        groupCount = 0;
    }

    static final class PendingProgress {
        private final UUID playerId;
        private final UUID instanceId;
        private final String toolId;
        private final String category;
        private final String progressType;
        private final Material material;
        private final int level;
        private long amount;

        private PendingProgress(
                UUID playerId,
                UUID instanceId,
                String toolId,
                String category,
                String progressType,
                Material material,
                int level,
                long amount
        ) {
            this.playerId = playerId;
            this.instanceId = instanceId;
            this.toolId = toolId;
            this.category = category;
            this.progressType = progressType;
            this.material = material;
            this.level = level;
            this.amount = amount;
        }

        UUID playerId() { return playerId; }
        UUID instanceId() { return instanceId; }
        String toolId() { return toolId; }
        String category() { return category; }
        String progressType() { return progressType; }
        Material material() { return material; }
        int level() { return level; }
        long amount() { return amount; }

        private boolean matches(
                UUID expectedPlayerId,
                String expectedToolId,
                String expectedCategory,
                String expectedProgressType,
                Material expectedMaterial,
                int expectedLevel
        ) {
            return level == expectedLevel
                    && playerId.equals(expectedPlayerId)
                    && toolId.equals(expectedToolId)
                    && category.equals(expectedCategory)
                    && progressType.equals(expectedProgressType)
                    && material == expectedMaterial;
        }
    }
}
