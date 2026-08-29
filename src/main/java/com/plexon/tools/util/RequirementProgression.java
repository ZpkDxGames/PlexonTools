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
        Map<String, Long> targetProgress = normalize(currentTargets);
        String target = LevelRequirement.normalize(eventTarget);
        long increment = Math.max(0L, amount);
        int levelsGained = 0;

        LevelRequirement requirement = requirements.get(level);
        if (requirement == null || increment == 0L || !requirement.accepts(target)) {
            return new Result(level, progress, targetProgress, 0);
        }

        if (requirement.mode() == RequirementMode.GENERAL) {
            progress = ProgressionMath.saturatingAdd(progress, increment);
            targetProgress = Map.of();
        } else {
            targetProgress.merge(target, increment, ProgressionMath::saturatingAdd);
            progress = requirement.rawProgress(0L, targetProgress);
        }

        while (requirements.higherKey(level) != null
                && requirement.complete(progress, targetProgress)) {
            Carry carry = overflow(requirement, progress, targetProgress, target);
            level = requirements.higherKey(level);
            levelsGained++;
            requirement = requirements.get(level);

            if (requirement.mode() == RequirementMode.GENERAL) {
                progress = carryForGeneral(requirement, carry);
                targetProgress = Map.of();
            } else {
                targetProgress = carryForSpecific(requirement, carry);
                progress = requirement.rawProgress(0L, targetProgress);
            }
        }

        return new Result(level, progress, targetProgress, levelsGained);
    }

    private static Carry overflow(
            LevelRequirement requirement,
            long progress,
            Map<String, Long> targetProgress,
            String eventTarget
    ) {
        if (requirement.mode() == RequirementMode.GENERAL) {
            long excess = Math.max(0L, progress - requirement.amount());
            return new Carry(excess, excess == 0L ? Map.of() : Map.of(eventTarget, excess));
        }
        Map<String, Long> excessTargets = new LinkedHashMap<>();
        long total = 0L;
        for (Map.Entry<String, Long> requirementTarget : requirement.targets().entrySet()) {
            String target = requirementTarget.getKey();
            long excess = Math.max(0L,
                    targetProgress.getOrDefault(target, 0L) - requirementTarget.getValue());
            if (excess > 0L) {
                excessTargets.put(target, excess);
                total = ProgressionMath.saturatingAdd(total, excess);
            }
        }
        return new Carry(total, excessTargets);
    }

    private static long carryForGeneral(LevelRequirement next, Carry carry) {
        if (next.targets().isEmpty()) {
            return carry.total();
        }
        long accepted = 0L;
        for (Map.Entry<String, Long> entry : carry.targets().entrySet()) {
            if (next.accepts(entry.getKey())) {
                accepted = ProgressionMath.saturatingAdd(accepted, entry.getValue());
            }
        }
        return accepted;
    }

    private static Map<String, Long> carryForSpecific(LevelRequirement next, Carry carry) {
        Map<String, Long> accepted = new LinkedHashMap<>();
        carry.targets().forEach((target, value) -> {
            if (next.accepts(target) && value > 0L) {
                accepted.put(target, value);
            }
        });
        return accepted;
    }

    private static Map<String, Long> normalize(Map<String, Long> progress) {
        Map<String, Long> normalized = new LinkedHashMap<>();
        progress.forEach((target, value) -> {
            if (value != null && value > 0L) {
                normalized.put(LevelRequirement.normalize(target), value);
            }
        });
        return normalized;
    }

    private record Carry(long total, Map<String, Long> targets) {
    }

    public record Result(
            int level,
            long progress,
            Map<String, Long> targetProgress,
            int levelsGained
    ) {
        public Result {
            targetProgress = Map.copyOf(targetProgress);
        }
    }
}
