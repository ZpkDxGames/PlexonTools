package com.plexon.tools.util;

import java.util.NavigableMap;

public final class ProgressionMath {
    private ProgressionMath() {
    }

    public static Result advance(
            int currentLevel,
            long currentProgress,
            long amount,
            NavigableMap<Integer, Long> requirements
    ) {
        int level = currentLevel;
        long progress = saturatingAdd(Math.max(0L, currentProgress), Math.max(0L, amount));
        int levelsGained = 0;

        while (requirements.higherKey(level) != null) {
            long requirement = Math.max(1L, requirements.getOrDefault(level, Long.MAX_VALUE));
            if (progress < requirement) {
                break;
            }
            progress -= requirement;
            level = requirements.higherKey(level);
            levelsGained++;
        }

        return new Result(level, progress, levelsGained);
    }

    private static long saturatingAdd(long first, long second) {
        if (Long.MAX_VALUE - first < second) {
            return Long.MAX_VALUE;
        }
        return first + second;
    }

    public record Result(int level, long progress, int levelsGained) {
    }
}
