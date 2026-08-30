package com.plexon.tools.service;

import com.plexon.tools.model.GlintMode;
import com.plexon.tools.model.LevelRequirement;
import com.plexon.tools.model.ProgressionScope;
import com.plexon.tools.model.RequirementMode;
import com.plexon.tools.model.ToolDefinition;
import com.plexon.tools.model.ToolLevel;
import com.plexon.tools.model.TrackingType;
import com.plexon.tools.storage.InstanceRegistry;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProgressionRecordSelectorTest {
    private static final String OVERWORLD = "Survival_World";
    private static final String NETHER = "Survival_World_nether";
    private static final String END = "Survival_World_the_end";

    @Test
    void configuredOverworldRecordWinsOverResetDimensionCopies() {
        ToolDefinition definition = definition(ProgressionScope.PLAYER);
        UUID owner = UUID.randomUUID();
        InstanceRegistry.InstanceRecord overworld = record(
                owner, OVERWORLD, 1, 425L, 10L, 10L);
        InstanceRegistry.InstanceRecord newerNetherCopy = record(
                owner, NETHER, 2, 490L, 9_000L, 9_000L);

        InstanceRegistry.InstanceRecord selected = ProgressionRecordSelector.canonical(
                definition, List.of(newerNetherCopy, overworld)).orElseThrow();

        assertEquals(overworld.instanceId(), selected.instanceId());
    }

    @Test
    void bestProgressedCopyIsUsedWhenNoAnchorRecordExistsYet() {
        ToolDefinition definition = definition(ProgressionScope.PLAYER);
        UUID owner = UUID.randomUUID();
        InstanceRegistry.InstanceRecord nether = record(
                owner, NETHER, 2, 20L, 50L, 50L);
        InstanceRegistry.InstanceRecord end = record(
                owner, END, 1, 499L, 500L, 500L);

        InstanceRegistry.InstanceRecord selected = ProgressionRecordSelector.canonical(
                definition, List.of(end, nether)).orElseThrow();

        assertEquals(nether.instanceId(), selected.instanceId());
    }

    @Test
    void sharedDefinitionsPersistEveryDimensionAgainstTheAnchor() {
        ToolDefinition shared = definition(ProgressionScope.PLAYER);
        ToolDefinition perWorld = definition(ProgressionScope.WORLD);

        assertEquals(OVERWORLD, shared.persistenceWorld(NETHER));
        assertEquals(NETHER, perWorld.persistenceWorld(NETHER));
    }

    private static ToolDefinition definition(ProgressionScope scope) {
        TreeMap<Integer, ToolLevel> levels = new TreeMap<>();
        levels.put(1, level(1, 500L));
        levels.put(2, level(2, 500L));
        return new ToolDefinition(
                "legendary_pickaxe",
                true,
                "Legendary Pickaxe",
                Material.DIAMOND_PICKAXE,
                new LinkedHashSet<>(List.of(OVERWORLD, NETHER, END)),
                "mining",
                scope,
                OVERWORLD,
                TrackingType.BLOCKS_BROKEN,
                RequirementMode.GENERAL,
                levels);
    }

    private static ToolLevel level(int number, long requirement) {
        return new ToolLevel(
                number,
                LevelRequirement.general(requirement),
                "Legendary Pickaxe",
                number == 1,
                Map.of(),
                Material.DIAMOND_PICKAXE,
                number == 1,
                List.of(),
                true,
                GlintMode.AUTO,
                true,
                true,
                null,
                Map.of());
    }

    private static InstanceRegistry.InstanceRecord record(
            UUID owner,
            String world,
            int level,
            long progress,
            long lifetime,
            long updatedAt
    ) {
        return new InstanceRegistry.InstanceRecord(
                UUID.randomUUID(),
                "legendary_pickaxe",
                "mining",
                owner,
                "Tonim",
                world,
                level,
                progress,
                Map.of(),
                true,
                true,
                lifetime,
                1L,
                updatedAt);
    }
}
