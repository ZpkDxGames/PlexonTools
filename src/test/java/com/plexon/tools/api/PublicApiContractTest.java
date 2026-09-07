package com.plexon.tools.api;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PublicApiContractTest {
    @Test
    void minimumPublicApiMethodsRemainAvailable() throws Exception {
        Method stackLookup = PlexonToolsAPI.class.getMethod("tool", ItemStack.class);
        Method idLookup = PlexonToolsAPI.class.getMethod("tool", UUID.class);
        Method definition = PlexonToolsAPI.class.getMethod("definition", String.class);
        Method definitions = PlexonToolsAPI.class.getMethod("definitions");
        Method tagged = PlexonToolsAPI.class.getMethod("isPlexonTool", ItemStack.class);

        assertEquals(Optional.class, stackLookup.getReturnType());
        assertEquals(Optional.class, idLookup.getReturnType());
        assertEquals(Optional.class, definition.getReturnType());
        assertTrue(Collection.class.isAssignableFrom(definitions.getReturnType()));
        assertEquals(boolean.class, tagged.getReturnType());
    }

    @Test
    void publicViewsDefensivelyCopyCollections() {
        Map<String, Long> progress = new HashMap<>();
        progress.put("STONE", 7L);
        ToolView view = new ToolView(
                UUID.randomUUID(), "legendary_pickaxe", "mining", UUID.randomUUID(),
                "Survival_World", 12, 7L, progress, "blocks_broken", Material.IRON_PICKAXE);
        progress.put("DIAMOND_ORE", 99L);
        assertEquals(Map.of("STONE", 7L), view.targetProgress());
        assertThrows(UnsupportedOperationException.class,
                () -> view.targetProgress().put("DIRT", 1L));

        Set<String> worlds = new HashSet<>(Set.of("Survival_World"));
        ToolDefinitionView definition = new ToolDefinitionView(
                "legendary_pickaxe", true, "Legendary Pickaxe", Material.WOODEN_PICKAXE,
                worlds, "mining", "player", "Survival_World", "blocks_broken", 100);
        worlds.add("Other");
        assertEquals(Set.of("Survival_World"), definition.allowedWorlds());
        assertThrows(UnsupportedOperationException.class,
                () -> definition.allowedWorlds().add("Other"));
    }
}
