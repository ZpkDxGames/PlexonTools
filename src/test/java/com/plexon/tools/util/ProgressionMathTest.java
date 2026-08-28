package com.plexon.tools.util;

import org.junit.jupiter.api.Test;

import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProgressionMathTest {
    @Test
    void carriesOverflowAcrossMultipleLevels() {
        TreeMap<Integer, Long> requirements = new TreeMap<>();
        requirements.put(1, 100L);
        requirements.put(2, 200L);
        requirements.put(3, 400L);

        ProgressionMath.Result result = ProgressionMath.advance(1, 90L, 230L, requirements);

        assertEquals(3, result.level());
        assertEquals(20L, result.progress());
        assertEquals(2, result.levelsGained());
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
}
