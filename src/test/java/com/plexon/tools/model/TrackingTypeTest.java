package com.plexon.tools.model;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackingTypeTest {
    @Test
    void exposesAllSixV3TrackingTypes() {
        assertEquals(Set.of(
                "BLOCKS_BROKEN",
                "MOBS_KILLED",
                "ITEMS_FARMED",
                "FISH_CAUGHT",
                "DAMAGE_DEALT",
                "BLOCKS_PLACED"
        ), java.util.Arrays.stream(TrackingType.values())
                .map(Enum::name)
                .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void classifiesMaterialAndEntityTargets() {
        assertTrue(TrackingType.BLOCKS_BROKEN.usesMaterialTargets());
        assertTrue(TrackingType.ITEMS_FARMED.usesMaterialTargets());
        assertTrue(TrackingType.FISH_CAUGHT.usesMaterialTargets());
        assertTrue(TrackingType.MOBS_KILLED.usesEntityTargets());
        assertTrue(TrackingType.DAMAGE_DEALT.usesEntityTargets());
    }

    @Test
    void cyclesThroughEveryType() {
        TrackingType value = TrackingType.BLOCKS_BROKEN;
        for (int index = 0; index < TrackingType.values().length; index++) {
            value = value.next();
        }
        assertEquals(TrackingType.BLOCKS_BROKEN, value);
    }
}
