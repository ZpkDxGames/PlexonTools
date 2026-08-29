package com.plexon.tools.model;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldToolMenuTest {
    @Test
    void normalizesToolIdsAndResolvesSlots() {
        WorldToolMenu menu = new WorldToolMenu("world", "Tools", 4,
                Material.GRAY_STAINED_GLASS_PANE, " ", Map.of("Magma_Breaker", 10));

        assertTrue(menu.contains("magma_breaker"));
        assertEquals(10, menu.slot("MAGMA_BREAKER").orElseThrow());
        assertEquals(36, menu.size());
    }

    @Test
    void contentSlotsExcludeBordersAndNavigationRow() {
        assertTrue(WorldToolMenu.isContentSlot(4, 10));
        assertTrue(WorldToolMenu.isContentSlot(4, 25));
        assertFalse(WorldToolMenu.isContentSlot(4, 9));
        assertFalse(WorldToolMenu.isContentSlot(4, 17));
        assertFalse(WorldToolMenu.isContentSlot(4, 31));
    }

    @Test
    void rejectsDuplicateAndUnsafeSlots() {
        assertThrows(IllegalArgumentException.class, () -> new WorldToolMenu(
                "world", "Tools", 4, Material.STONE, " ",
                Map.of("first", 10, "second", 10)));
        assertThrows(IllegalArgumentException.class, () -> new WorldToolMenu(
                "world", "Tools", 4, Material.STONE, " ", Map.of("first", 31)));
    }
}
