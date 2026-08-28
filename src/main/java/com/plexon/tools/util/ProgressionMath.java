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

    public static long cumulativeBefore(int level, NavigableMap<Integer, Long> requirements) {
        long total = 0L;
        for (long requirement : requirements.headMap(level, false).values()) {
            total = saturatingAdd(total, Math.max(0L, requirement));
        }
        return total;
    }

    public static long remaining(long progress, long requirement) {
        return Math.max(0L, Math.max(0L, requirement) - Math.max(0L, progress));
    }

    public static int percent(long progress, long requirement) {
        if (requirement <= 0L) {
            return 100;
        }
        double ratio = Math.max(0.0D, Math.min(1.0D, progress / (double) requirement));
        return (int) Math.floor(ratio * 100.0D);
    }

    public static long saturatingAdd(long first, long second) {
        if (Long.MAX_VALUE - first < second) {
            return Long.MAX_VALUE;
        }
        return first + second;
    }

    public record Result(int level, long progress, int levelsGained) {
    }
}
