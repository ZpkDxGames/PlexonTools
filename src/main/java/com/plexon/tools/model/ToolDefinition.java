package com.plexon.tools.model;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.Collections;
import java.util.LinkedHashSet;
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
        String category,
        ProgressionScope progressionScope,
        String progressionAnchorWorld,
        TrackingType trackingType,
        RequirementMode defaultRequirementMode,
        NavigableMap<Integer, ToolLevel> levels
) {
    public ToolDefinition {
        Set<String> normalizedAllowedWorlds = Collections.unmodifiableSet(
                new LinkedHashSet<>(allowedWorlds));
        if (normalizedAllowedWorlds.isEmpty()) {
            throw new IllegalArgumentException("At least one allowed world is required.");
        }
        allowedWorlds = normalizedAllowedWorlds;
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("Tool category is required.");
        }
        if (defaultRequirementMode == null) {
            throw new IllegalArgumentException("Default requirement mode is required.");
        }
        progressionScope = progressionScope == null ? ProgressionScope.WORLD : progressionScope;
        String requestedAnchor = progressionAnchorWorld == null
                ? "" : progressionAnchorWorld.trim();
        progressionAnchorWorld = normalizedAllowedWorlds.stream()
                .filter(world -> world.equalsIgnoreCase(requestedAnchor))
                .findFirst()
                .orElseGet(() -> {
                    if (!requestedAnchor.isBlank()) {
                        throw new IllegalArgumentException(
                                "Progression anchor world must also be listed in allowed_worlds: "
                                        + requestedAnchor);
                    }
                    return normalizedAllowedWorlds.iterator().next();
                });
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
        for (String world : allowedWorlds) {
            if (world.equalsIgnoreCase(worldName)) {
                return true;
            }
        }
        return false;
    }

    public boolean sharesProgressAcrossWorlds() {
        return progressionScope == ProgressionScope.PLAYER;
    }

    public String persistenceWorld(String requestedWorld) {
        return sharesProgressAcrossWorlds() ? progressionAnchorWorld : requestedWorld;
    }

    public boolean tracks(Material material, int level) {
        ToolLevel profile = levels.get(level);
        return trackingType.usesMaterialTargets() && profile != null
                && profile.requirement().accepts(material.name());
    }

    public boolean tracks(EntityType entityType, int level) {
        ToolLevel profile = levels.get(level);
        return trackingType.usesEntityTargets() && profile != null
                && profile.requirement().accepts(entityType.name());
    }

    public boolean tracks(String target, int level) {
        ToolLevel profile = levels.get(level);
        return profile != null && profile.requirement().accepts(target);
    }

    public Set<String> targetNames() {
        Set<String> targets = new LinkedHashSet<>();
        levels.values().forEach(level -> targets.addAll(level.requirement().targets().keySet()));
        return Set.copyOf(targets);
    }

    public Set<String> targetNames(int level) {
        return level(level).map(profile -> profile.requirement().targets().keySet()).orElse(Set.of());
    }

    public boolean hasMixedRequirementModes() {
        return levels.values().stream()
                .map(level -> level.requirement().mode())
                .distinct()
                .limit(2)
                .count() > 1L;
    }
}
