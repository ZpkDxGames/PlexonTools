package com.plexon.tools.storage;

import java.util.Objects;
import java.util.UUID;

/** Persistent provenance coordinate for a player-placed block. */
public record PlacedBlockPosition(UUID worldId, int x, int y, int z) {
    public PlacedBlockPosition {
        Objects.requireNonNull(worldId, "World UUID is required.");
    }

    public ChunkKey chunk() {
        return new ChunkKey(worldId, Math.floorDiv(x, 16), Math.floorDiv(z, 16));
    }

    public record ChunkKey(UUID worldId, int x, int z) {
        public ChunkKey {
            Objects.requireNonNull(worldId, "World UUID is required.");
        }
    }
}
