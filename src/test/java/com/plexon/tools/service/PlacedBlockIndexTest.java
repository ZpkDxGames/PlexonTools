package com.plexon.tools.service;

import com.plexon.tools.storage.PlacedBlockPosition;
import com.plexon.tools.storage.PlacedBlockPosition.ChunkKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PlacedBlockIndexTest {
    @Test
    void staysUnknownUntilPersistedChunkLoadCompletes() {
        UUID world = UUID.randomUUID();
        PlacedBlockIndex index = new PlacedBlockIndex();
        ChunkKey chunk = new ChunkKey(world, 0, 0);
        PlacedBlockPosition placed = new PlacedBlockPosition(world, 3, 64, 5);

        assertTrue(index.beginLoad(chunk));
        assertEquals(PlacedBlockIndex.Origin.UNKNOWN, index.peek(world, 1, 64, 1));
        index.completeLoad(chunk, List.of(placed));
        assertEquals(PlacedBlockIndex.Origin.PLAYER_PLACED, index.peek(placed));
        assertEquals(PlacedBlockIndex.Origin.NATURAL, index.peek(world, 1, 64, 1));
    }

    @Test
    void breakDuringLoadCannotBeResurrectedByStaleDatabaseRow() {
        UUID world = UUID.randomUUID();
        PlacedBlockIndex index = new PlacedBlockIndex();
        ChunkKey chunk = new ChunkKey(world, -1, -1);
        PlacedBlockPosition position = new PlacedBlockPosition(world, -1, 70, -1);

        assertTrue(index.beginLoad(chunk));
        assertEquals(PlacedBlockIndex.Origin.UNKNOWN, index.consume(position));
        index.completeLoad(chunk, List.of(position));
        assertEquals(PlacedBlockIndex.Origin.NATURAL, index.peek(position));
    }

    @Test
    void playerPlacementAndConsumptionAreSparseAndChunkScoped() {
        UUID world = UUID.randomUUID();
        PlacedBlockIndex index = new PlacedBlockIndex();
        ChunkKey chunk = new ChunkKey(world, 2, 3);
        index.beginLoad(chunk);
        index.completeLoad(chunk, List.of());
        PlacedBlockPosition position = new PlacedBlockPosition(world, 34, -12, 50);

        index.markPlaced(position);
        assertEquals(PlacedBlockIndex.Origin.PLAYER_PLACED, index.peek(position));
        assertEquals(PlacedBlockIndex.Origin.PLAYER_PLACED, index.consume(position));
        assertEquals(PlacedBlockIndex.Origin.NATURAL, index.peek(position));
        assertFalse(index.beginLoad(chunk));

        index.unload(chunk);
        assertTrue(index.beginLoad(chunk));
    }

    @Test
    void batchConsumePreservesOrderingAndClearsPlacedPositions() {
        UUID world = UUID.randomUUID();
        PlacedBlockIndex index = new PlacedBlockIndex();
        ChunkKey chunk = new ChunkKey(world, 0, 0);
        PlacedBlockPosition placed = new PlacedBlockPosition(world, 1, 64, 1);
        PlacedBlockPosition natural = new PlacedBlockPosition(world, 2, 64, 1);

        index.beginLoad(chunk);
        index.completeLoad(chunk, List.of(placed));

        assertEquals(List.of(
                        PlacedBlockIndex.Origin.PLAYER_PLACED,
                        PlacedBlockIndex.Origin.NATURAL),
                index.consumeAll(List.of(placed, natural)));
        assertEquals(PlacedBlockIndex.Origin.NATURAL, index.peek(placed));
    }

    @Test
    void diagnosticsCountLoadedUnknownAndPlacedState() {
        UUID world = UUID.randomUUID();
        PlacedBlockIndex index = new PlacedBlockIndex();
        ChunkKey ready = new ChunkKey(world, 0, 0);
        ChunkKey loading = new ChunkKey(world, 1, 0);
        PlacedBlockPosition first = new PlacedBlockPosition(world, 1, 64, 1);
        PlacedBlockPosition second = new PlacedBlockPosition(world, 2, 80, 2);

        index.beginLoad(ready);
        index.completeLoad(ready, List.of(first));
        index.markPlaced(second);
        index.beginLoad(loading);

        assertEquals(2, index.loadedChunkCount());
        assertEquals(1, index.unknownChunkCount());
        assertEquals(2, index.trackedPlacedPositionCount());

        index.consume(first);
        assertEquals(1, index.trackedPlacedPositionCount());
        index.unload(loading);
        assertEquals(1, index.loadedChunkCount());
        assertEquals(0, index.unknownChunkCount());
    }
}
