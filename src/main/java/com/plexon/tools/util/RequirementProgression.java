package com.plexon.tools.util;

import com.plexon.tools.model.LevelRequirement;
import com.plexon.tools.model.RequirementMode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;

public final class RequirementProgression {
    private RequirementProgression() {
    }

    public static Result advance(
            int currentLevel,
            long currentProgress,
            Map<String, Long> currentTargets,
            String eventTarget,
            long amount,
            NavigableMap<Integer, LevelRequirement> requirements
    ) {
        int level = currentLevel;
        long progress = Math.max(0L, currentProgress);
        long increment = Math.max(0L, amount);
        LevelRequirement requirement = requirements.get(level);
        if (requirement == null || increment == 0L) {
            return unchanged(level, progress, currentTargets);
        }

        // General block/item requirements are overwhelmingly the mining hot path.
        // Avoid cloning/normalizing a target-progress map that is never used.
        if (requirement.mode() == RequirementMode.GENERAL) {
            String target = LevelRequirement.normalize(eventTarget);
            if (!requirement.accepts(target)) {
                return unchanged(level, progress, currentTargets);
            }
            progress = ProgressionMath.saturatingAdd(progress, increment);
            Integer nextLevel = requirements.higherKey(level);
            if (nextLevel != null && progress >= requirement.amount()) {
                return new Result(nextLevel, 0L, Map.of(), 1);
            }
            return new Result(level, progress, Map.of(), 0);
        }

        String target = LevelRequirement.normalize(eventTarget);
        if (!requirement.accepts(target)) {
            return unchanged(level, progress, currentTargets);
        }

        Map<String, Long> targetProgress = normalize(currentTargets);
        targetProgress.merge(target, increment, ProgressionMath::saturatingAdd);
        progress = requirement.rawProgress(0L, targetProgress);

        Integer nextLevel = requirements.higherKey(level);
        if (nextLevel != null && requirement.complete(progress, targetProgress)) {
            level = nextLevel;
            progress = 0L;
            targetProgress = Map.of();
            return new Result(level, progress, targetProgress, 1);
        }

        return new Result(level, progress, targetProgress, 0);
    }

    private static Result unchanged(
            int level,
            long progress,
            Map<String, Long> currentTargets
    ) {
        if (currentTargets.isEmpty()) {
            return new Result(level, progress, Map.of(), 0);
        }
        return new Result(level, progress, currentTargets, 0);
    }

    private static Map<String, Long> normalize(Map<String, Long> progress) {
        if (progress.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, Long> normalized = new LinkedHashMap<>();
        progress.forEach((target, value) -> {
            if (value != null && value > 0L) {
                normalized.put(LevelRequirement.normalize(target), value);
            }
        });
        return normalized;
    }

    public record Result(
            int level,
            long progress,
            Map<String, Long> targetProgress,
            int levelsGained
    ) {
        public Result {
            targetProgress = targetProgress.isEmpty() ? Map.of() : Map.copyOf(targetProgress);
        }
    }
}
