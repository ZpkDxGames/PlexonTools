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
        TrackingType trackingType,
        RequirementMode defaultRequirementMode,
        NavigableMap<Integer, ToolLevel> levels
) {
    public ToolDefinition {
        allowedWorlds = Set.copyOf(allowedWorlds);
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("Tool category is required.");
        }
        if (defaultRequirementMode == null) {
            throw new IllegalArgumentException("Default requirement mode is required.");
        }
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

    public boolean tracks(Material material, int level) {
        return trackingType.usesMaterialTargets()
                && level(level).map(profile -> profile.requirement().accepts(material.name())).orElse(false);
    }

    public boolean tracks(EntityType entityType, int level) {
        return trackingType.usesEntityTargets()
                && level(level).map(profile -> profile.requirement().accepts(entityType.name())).orElse(false);
    }

    public boolean tracks(String target, int level) {
        return level(level).map(profile -> profile.requirement().accepts(target)).orElse(false);
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
