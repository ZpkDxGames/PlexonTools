package com.plexon.tools.model;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.Collections;
import java.util.Locale;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

public record ToolDefinition(
        String id,
        boolean enabled,
        String displayName,
        Material baseMaterial,
        Set<String> allowedWorlds,
        TrackingType trackingType,
        Set<Material> blockTargets,
        Set<EntityType> entityTargets,
        NavigableMap<Integer, ToolLevel> levels
) {
    public ToolDefinition {
        allowedWorlds = Set.copyOf(allowedWorlds);
        blockTargets = Set.copyOf(blockTargets);
        entityTargets = Set.copyOf(entityTargets);
        levels = Collections.unmodifiableNavigableMap(new TreeMap<>(levels));
    }

    public ToolLevel firstLevel() {
        return levels.firstEntry().getValue();
    }

    public Optional<ToolLevel> level(int number) {
        return Optional.ofNullable(levels.get(number));
    }

    public Optional<ToolLevel> nextLevel(int current) {
        var entry = levels.higherEntry(current);
        return entry == null ? Optional.empty() : Optional.of(entry.getValue());
    }

    public int maxLevel() {
        return levels.lastKey();
    }

    public boolean isAllowedWorld(String worldName) {
        return allowedWorlds.stream().anyMatch(world -> world.equalsIgnoreCase(worldName));
    }

    public boolean tracks(Material material) {
        return trackingType == TrackingType.BLOCKS_BROKEN
                && (blockTargets.isEmpty() || blockTargets.contains(material));
    }

    public boolean tracks(EntityType entityType) {
        return trackingType == TrackingType.MOBS_KILLED
                && (entityTargets.isEmpty() || entityTargets.contains(entityType));
    }

    public Set<String> targetNames() {
        if (trackingType == TrackingType.BLOCKS_BROKEN) {
            return blockTargets.stream()
                    .map(material -> material.name().toUpperCase(Locale.ROOT))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
        return entityTargets.stream()
                .map(type -> type.name().toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
