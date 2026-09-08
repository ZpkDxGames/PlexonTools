package com.plexon.tools.service;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProgressEventBatcherTest {
    @Test
    void coalescesCompatibleProgressWithoutChangingTotalAmount() {
        ProgressEventBatcher batcher = new ProgressEventBatcher();
        UUID playerId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();

        for (int i = 0; i < 10; i++) {
            batcher.add(playerId, instanceId, "legendary_pickaxe", "legendary",
                    "blocks_broken", Material.STONE, 7, 1L);
        }

        List<ProgressEventBatcher.PendingProgress> drained = batcher.drainAll();
        assertEquals(1, drained.size());
        assertEquals(10L, drained.getFirst().amount());
        assertEquals(Material.STONE, drained.getFirst().material());
        assertEquals(7, drained.getFirst().level());
        assertEquals(0, batcher.size());
    }

    @Test
    void keepsMixedMaterialsAndLevelsInSeparateGroups() {
        ProgressEventBatcher batcher = new ProgressEventBatcher();
        UUID playerId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();

        batcher.add(playerId, instanceId, "legendary_pickaxe", "legendary",
                "blocks_broken", Material.STONE, 7, 2L);
        batcher.add(playerId, instanceId, "legendary_pickaxe", "legendary",
                "blocks_broken", Material.DIAMOND_ORE, 7, 1L);
        batcher.add(playerId, instanceId, "legendary_pickaxe", "legendary",
                "blocks_broken", Material.STONE, 8, 1L);

        List<ProgressEventBatcher.PendingProgress> drained = batcher.drainInstance(instanceId);
        assertEquals(3, drained.size());
        assertEquals(4L, drained.stream().mapToLong(
                ProgressEventBatcher.PendingProgress::amount).sum());
        assertEquals(0, batcher.size());
    }

    @Test
    void drainsOnlyRequestedPlayer() {
        ProgressEventBatcher batcher = new ProgressEventBatcher();
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();

        batcher.add(firstPlayer, UUID.randomUUID(), "pickaxe", "legendary",
                "blocks_broken", Material.STONE, 1, 3L);
        batcher.add(secondPlayer, UUID.randomUUID(), "axe", "legendary",
                "blocks_broken", Material.OAK_LOG, 1, 2L);

        List<ProgressEventBatcher.PendingProgress> first = batcher.drainPlayer(firstPlayer);
        assertEquals(1, first.size());
        assertEquals(3L, first.getFirst().amount());
        assertEquals(1, batcher.size());
        assertTrue(batcher.drainPlayer(firstPlayer).isEmpty());
        assertEquals(2L, batcher.drainAll().getFirst().amount());
    }
}
