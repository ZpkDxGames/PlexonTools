package com.plexon.tools.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldToolMenuTest {
    @Test
    void normalizesMenuAndToolIds() {
        assertEquals("magma_breaker", WorldToolMenu.normalize("  Magma_Breaker "));
        assertEquals("", WorldToolMenu.normalize(null));
    }

    @Test
    void contentSlotsExcludeBordersAndNavigationRow() {
        assertTrue(WorldToolMenu.isContentSlot(4, 10));
        assertTrue(WorldToolMenu.isContentSlot(4, 25));
        assertFalse(WorldToolMenu.isContentSlot(4, 9));
        assertFalse(WorldToolMenu.isContentSlot(4, 17));
        assertFalse(WorldToolMenu.isContentSlot(4, 31));
    }

}
