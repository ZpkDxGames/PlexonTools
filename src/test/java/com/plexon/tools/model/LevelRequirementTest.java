package com.plexon.tools.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelRequirementTest {
    @Test
    void generalModeCreditsOneSharedTotal() {
        LevelRequirement requirement = LevelRequirement.general(500L);

        assertTrue(requirement.accepts("STONE"));
        assertEquals(125L, requirement.creditedProgress(125L, Map.of("STONE", 999L)));
        assertEquals(375L, requirement.remaining(125L, Map.of()));
        assertEquals(25, requirement.percentage(125L, Map.of()));
        assertFalse(requirement.complete(499L, Map.of()));
        assertTrue(requirement.complete(500L, Map.of()));
    }

    @Test
    void specificModeCapsEachTargetIndependently() {
        LevelRequirement requirement = LevelRequirement.specific(Map.of(
                "stone", 500L,
                "deepslate", 200L
        ));
        Map<String, Long> progress = Map.of("STONE", 700L, "DEEPSLATE", 100L, "DIRT", 900L);

        assertEquals(700L, requirement.requiredTotal());
        assertEquals(600L, requirement.creditedProgress(0L, progress));
        assertEquals(800L, requirement.rawProgress(0L, progress));
        assertEquals(100L, requirement.remaining(0L, progress));
        assertFalse(requirement.complete(0L, progress));
        assertTrue(requirement.complete(0L, Map.of("STONE", 500L, "DEEPSLATE", 200L)));
        assertFalse(requirement.accepts("DIRT"));
    }

    @Test
    void emptySpecificModeCannotComplete() {
        LevelRequirement requirement = LevelRequirement.specific(Map.of());

        assertEquals(0L, requirement.requiredTotal());
        assertEquals(0, requirement.percentage(0L, Map.of()));
        assertFalse(requirement.complete(Long.MAX_VALUE, Map.of("STONE", Long.MAX_VALUE)));
    }

    @Test
    void rejectsInvalidAmounts() {
        assertThrows(IllegalArgumentException.class, () -> LevelRequirement.general(0L));
        assertThrows(IllegalArgumentException.class,
                () -> LevelRequirement.specific(Map.of("STONE", 0L)));
    }
}
