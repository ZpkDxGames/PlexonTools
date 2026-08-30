package com.plexon.tools.util;

import org.junit.jupiter.api.Test;

import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProgressionMathTest {
    @Test
    void discardsOverflowAtTheNextLevel() {
        TreeMap<Integer, Long> requirements = new TreeMap<>();
        requirements.put(1, 100L);
        requirements.put(2, 200L);
        requirements.put(3, 400L);

        ProgressionMath.Result result = ProgressionMath.advance(1, 90L, 230L, requirements);

        assertEquals(2, result.level());
        assertEquals(0L, result.progress());
        assertEquals(1, result.levelsGained());
    }

    @Test
    void retainsProgressAtMaximumLevel() {
        TreeMap<Integer, Long> requirements = new TreeMap<>();
        requirements.put(1, 100L);
        requirements.put(2, 200L);

        ProgressionMath.Result result = ProgressionMath.advance(2, 50L, 75L, requirements);

        assertEquals(2, result.level());
        assertEquals(125L, result.progress());
        assertEquals(0, result.levelsGained());
    }

    @Test
    void calculatesCumulativeProgressAndDisplayValues() {
        TreeMap<Integer, Long> requirements = new TreeMap<>();
        requirements.put(1, 100L);
        requirements.put(2, 250L);
        requirements.put(3, 500L);

        assertEquals(350L, ProgressionMath.cumulativeBefore(3, requirements));
        assertEquals(175L, ProgressionMath.remaining(75L, 250L));
        assertEquals(30, ProgressionMath.percent(75L, 250L));
        assertEquals(100, ProgressionMath.percent(999L, 250L));
        assertEquals(100, ProgressionMath.percent(0L, 0L));
    }

    @Test
    void cumulativeProgressSaturatesInsteadOfOverflowing() {
        TreeMap<Integer, Long> requirements = new TreeMap<>();
        requirements.put(1, Long.MAX_VALUE);
        requirements.put(2, 50L);
        requirements.put(3, 1L);

        assertEquals(Long.MAX_VALUE, ProgressionMath.cumulativeBefore(3, requirements));
    }
}
