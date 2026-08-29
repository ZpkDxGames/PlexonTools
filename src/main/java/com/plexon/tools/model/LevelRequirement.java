package com.plexon.tools.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public record LevelRequirement(
        RequirementMode mode,
        long amount,
        Map<String, Long> targets
) {
    public LevelRequirement {
        if (mode == null) {
            throw new IllegalArgumentException("Requirement mode is required.");
        }
        if (amount < 1L) {
            throw new IllegalArgumentException("General requirement must be at least 1.");
        }
        Objects.requireNonNull(targets, "Requirement targets are required.");
        Map<String, Long> normalized = new LinkedHashMap<>();
        targets.forEach((target, required) -> {
            String key = normalize(target);
            if (key.isBlank()) {
                throw new IllegalArgumentException("Requirement targets cannot be blank.");
            }
            if (required == null || required < 1L) {
                throw new IllegalArgumentException("Target requirements must be at least 1.");
            }
            normalized.put(key, required);
        });
        targets = Collections.unmodifiableMap(normalized);
    }

    public static LevelRequirement general(long amount) {
        return new LevelRequirement(RequirementMode.GENERAL, amount, Map.of());
    }

    public static LevelRequirement filtered(long amount, Iterable<String> targets) {
        Map<String, Long> filters = new LinkedHashMap<>();
        targets.forEach(target -> filters.put(normalize(target), amount));
        return new LevelRequirement(RequirementMode.GENERAL, amount, filters);
    }

    public static LevelRequirement specific(Map<String, Long> targets) {
        long total = 0L;
        for (long required : targets.values()) {
            total = saturatingAdd(total, Math.max(0L, required));
        }
        return new LevelRequirement(RequirementMode.SPECIFIC, Math.max(1L, total), targets);
    }

    public boolean accepts(String target) {
        String normalized = normalize(target);
        return mode == RequirementMode.GENERAL
                ? targets.isEmpty() || targets.containsKey(normalized)
                : targets.containsKey(normalized);
    }

    public boolean isLegacyFilteredGeneral() {
        return mode == RequirementMode.GENERAL && !targets.isEmpty();
    }

    public long requiredTotal() {
        if (mode == RequirementMode.GENERAL) {
            return amount;
        }
        long total = 0L;
        for (long required : targets.values()) {
            total = saturatingAdd(total, required);
        }
        return total;
    }

    public long creditedProgress(long generalProgress, Map<String, Long> targetProgress) {
        if (mode == RequirementMode.GENERAL) {
            return Math.min(Math.max(0L, generalProgress), amount);
        }
        long total = 0L;
        for (Map.Entry<String, Long> target : targets.entrySet()) {
            long current = Math.max(0L, targetProgress.getOrDefault(target.getKey(), 0L));
            total = saturatingAdd(total, Math.min(current, target.getValue()));
        }
        return total;
    }

    public long rawProgress(long generalProgress, Map<String, Long> targetProgress) {
        if (mode == RequirementMode.GENERAL) {
            return Math.max(0L, generalProgress);
        }
        long total = 0L;
        for (String target : targets.keySet()) {
            total = saturatingAdd(total, Math.max(0L, targetProgress.getOrDefault(target, 0L)));
        }
        return total;
    }

    public long remaining(long generalProgress, Map<String, Long> targetProgress) {
        return Math.max(0L, requiredTotal() - creditedProgress(generalProgress, targetProgress));
    }

    public int percentage(long generalProgress, Map<String, Long> targetProgress) {
        long required = requiredTotal();
        if (required <= 0L) {
            return 0;
        }
        double ratio = creditedProgress(generalProgress, targetProgress) / (double) required;
        return (int) Math.floor(Math.max(0.0D, Math.min(1.0D, ratio)) * 100.0D);
    }

    public boolean complete(long generalProgress, Map<String, Long> targetProgress) {
        if (mode == RequirementMode.GENERAL) {
            return generalProgress >= amount;
        }
        return !targets.isEmpty() && targets.entrySet().stream()
                .allMatch(entry -> targetProgress.getOrDefault(entry.getKey(), 0L) >= entry.getValue());
    }

    public LevelRequirement withAmount(long newAmount) {
        return new LevelRequirement(mode, newAmount, targets);
    }

    public LevelRequirement withMode(RequirementMode newMode) {
        if (newMode == RequirementMode.GENERAL) {
            return general(Math.max(1L, requiredTotal()));
        }
        return specific(mode == RequirementMode.SPECIFIC ? targets : Map.of());
    }

    public LevelRequirement withTarget(String target, Long required) {
        Map<String, Long> updated = new LinkedHashMap<>(targets);
        String normalized = normalize(target);
        if (required == null || required <= 0L) {
            updated.remove(normalized);
        } else {
            updated.put(normalized, required);
        }
        return specific(updated);
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static long saturatingAdd(long first, long second) {
        if (Long.MAX_VALUE - first < second) {
            return Long.MAX_VALUE;
        }
        return first + second;
    }
}
