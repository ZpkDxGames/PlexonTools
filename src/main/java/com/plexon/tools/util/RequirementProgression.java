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
        String target = LevelRequirement.normalize(eventTarget);
        long increment = Math.max(0L, amount);
        int levelsGained = 0;

        LevelRequirement requirement = requirements.get(level);
        if (requirement == null || increment == 0L || !requirement.accepts(target)) {
            return new Result(level, progress, normalize(currentTargets), 0);
        }

        Map<String, Long> targetProgress;
        if (requirement.mode() == RequirementMode.GENERAL) {
            progress = ProgressionMath.saturatingAdd(progress, increment);
            targetProgress = Map.of();
        } else {
            targetProgress = normalize(currentTargets);
            targetProgress.merge(target, increment, ProgressionMath::saturatingAdd);
            progress = requirement.rawProgress(0L, targetProgress);
        }

        Integer nextLevel = requirements.higherKey(level);
        if (nextLevel != null && requirement.complete(progress, targetProgress)) {
            level = nextLevel;
            progress = 0L;
            targetProgress = Map.of();
            levelsGained = 1;
        }

        return new Result(level, progress, targetProgress, levelsGained);
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
