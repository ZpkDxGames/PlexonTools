package com.plexon.tools.util;

import com.plexon.tools.model.LevelRequirement;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RequirementProgressionTest {
    @Test
    void generalModeCarriesOverflowAcrossLevels() {
        TreeMap<Integer, LevelRequirement> requirements = new TreeMap<>();
        requirements.put(1, LevelRequirement.general(100L));
        requirements.put(2, LevelRequirement.general(200L));
        requirements.put(3, LevelRequirement.general(400L));

        RequirementProgression.Result result = RequirementProgression.advance(
                1, 90L, Map.of(), "STONE", 230L, requirements);

        assertEquals(3, result.level());
        assertEquals(20L, result.progress());
        assertEquals(Map.of(), result.targetProgress());
        assertEquals(2, result.levelsGained());
    }

    @Test
    void specificModeRequiresEveryQuota() {
        TreeMap<Integer, LevelRequirement> requirements = new TreeMap<>();
        requirements.put(1, LevelRequirement.specific(Map.of("STONE", 2L, "DEEPSLATE", 1L)));
        requirements.put(2, LevelRequirement.general(10L));

        RequirementProgression.Result stone = RequirementProgression.advance(
                1, 0L, Map.of(), "STONE", 2L, requirements);
        assertEquals(1, stone.level());
        assertEquals(Map.of("STONE", 2L), stone.targetProgress());

        RequirementProgression.Result complete = RequirementProgression.advance(
                stone.level(), stone.progress(), stone.targetProgress(), "DEEPSLATE", 1L, requirements);
        assertEquals(2, complete.level());
        assertEquals(0L, complete.progress());
        assertEquals(Map.of(), complete.targetProgress());
        assertEquals(1, complete.levelsGained());
    }

    @Test
    void unrelatedTargetsAreIgnored() {
        TreeMap<Integer, LevelRequirement> requirements = new TreeMap<>();
        requirements.put(1, LevelRequirement.specific(Map.of("STONE", 5L)));
        requirements.put(2, LevelRequirement.general(10L));

        RequirementProgression.Result result = RequirementProgression.advance(
                1, 0L, Map.of(), "DIRT", 50L, requirements);

        assertEquals(1, result.level());
        assertEquals(0L, result.progress());
        assertEquals(Map.of(), result.targetProgress());
    }

    @Test
    void perTargetOverflowCarriesIntoNextSpecificLevel() {
        TreeMap<Integer, LevelRequirement> requirements = new TreeMap<>();
        requirements.put(1, LevelRequirement.specific(Map.of("STONE", 2L, "DIRT", 1L)));
        requirements.put(2, LevelRequirement.specific(Map.of("STONE", 1L, "DIRT", 3L)));

        RequirementProgression.Result result = RequirementProgression.advance(
                1, 3L, Map.of("STONE", 3L), "DIRT", 2L, requirements);

        assertEquals(2, result.level());
        assertEquals(2L, result.progress());
        assertEquals(Map.of("STONE", 1L, "DIRT", 1L), result.targetProgress());
        assertEquals(1, result.levelsGained());
    }

    @Test
    void generalOverflowCanSatisfyFollowingSpecificQuota() {
        TreeMap<Integer, LevelRequirement> requirements = new TreeMap<>();
        requirements.put(1, LevelRequirement.general(2L));
        requirements.put(2, LevelRequirement.specific(Map.of("STONE", 1L)));
        requirements.put(3, LevelRequirement.general(10L));

        RequirementProgression.Result result = RequirementProgression.advance(
                1, 1L, Map.of(), "STONE", 3L, requirements);

        assertEquals(3, result.level());
        assertEquals(1L, result.progress());
        assertEquals(2, result.levelsGained());
    }
}
