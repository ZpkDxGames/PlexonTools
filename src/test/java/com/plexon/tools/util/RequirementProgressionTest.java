package com.plexon.tools.util;

import com.plexon.tools.model.LevelRequirement;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RequirementProgressionTest {
    @Test
    void generalModeDiscardsOverflowAtLevelBoundary() {
        TreeMap<Integer, LevelRequirement> requirements = new TreeMap<>();
        requirements.put(1, LevelRequirement.general(100L));
        requirements.put(2, LevelRequirement.general(200L));
        requirements.put(3, LevelRequirement.general(400L));

        RequirementProgression.Result result = RequirementProgression.advance(
                1, 90L, Map.of(), "STONE", 230L, requirements);

        assertEquals(2, result.level());
        assertEquals(0L, result.progress());
        assertEquals(Map.of(), result.targetProgress());
        assertEquals(1, result.levelsGained());
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
    void perTargetOverflowIsDiscardedAtLevelBoundary() {
        TreeMap<Integer, LevelRequirement> requirements = new TreeMap<>();
        requirements.put(1, LevelRequirement.specific(Map.of("STONE", 2L, "DIRT", 1L)));
        requirements.put(2, LevelRequirement.specific(Map.of("STONE", 1L, "DIRT", 3L)));

        RequirementProgression.Result result = RequirementProgression.advance(
                1, 3L, Map.of("STONE", 3L), "DIRT", 2L, requirements);

        assertEquals(2, result.level());
        assertEquals(0L, result.progress());
        assertEquals(Map.of(), result.targetProgress());
        assertEquals(1, result.levelsGained());
    }

    @Test
    void generalOverflowCannotSatisfyFollowingSpecificQuota() {
        TreeMap<Integer, LevelRequirement> requirements = new TreeMap<>();
        requirements.put(1, LevelRequirement.general(2L));
        requirements.put(2, LevelRequirement.specific(Map.of("STONE", 1L)));
        requirements.put(3, LevelRequirement.general(10L));

        RequirementProgression.Result result = RequirementProgression.advance(
                1, 1L, Map.of(), "STONE", 3L, requirements);

        assertEquals(2, result.level());
        assertEquals(0L, result.progress());
        assertEquals(Map.of(), result.targetProgress());
        assertEquals(1, result.levelsGained());
    }

    @Test
    void repeatedStoneQuotaMustBeCompletedOncePerLevel() {
        TreeMap<Integer, LevelRequirement> requirements = new TreeMap<>();
        requirements.put(1, LevelRequirement.specific(Map.of("STONE", 500L)));
        requirements.put(2, LevelRequirement.specific(Map.of("STONE", 500L)));
        requirements.put(3, LevelRequirement.specific(Map.of("STONE", 500L)));

        RequirementProgression.Result firstLevel = RequirementProgression.advance(
                1, 0L, Map.of(), "STONE", 500L, requirements);
        assertEquals(2, firstLevel.level());
        assertEquals(0L, firstLevel.progress());
        assertEquals(Map.of(), firstLevel.targetProgress());

        RequirementProgression.Result almostSecondLevel = RequirementProgression.advance(
                firstLevel.level(), firstLevel.progress(), firstLevel.targetProgress(),
                "STONE", 499L, requirements);
        assertEquals(2, almostSecondLevel.level());
        assertEquals(Map.of("STONE", 499L), almostSecondLevel.targetProgress());

        RequirementProgression.Result secondLevel = RequirementProgression.advance(
                almostSecondLevel.level(), almostSecondLevel.progress(),
                almostSecondLevel.targetProgress(), "STONE", 1L, requirements);
        assertEquals(3, secondLevel.level());
        assertEquals(0L, secondLevel.progress());
        assertEquals(Map.of(), secondLevel.targetProgress());
    }
}
