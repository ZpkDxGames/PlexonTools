package com.plexon.tools.service;

import com.plexon.tools.storage.PlacedBlockPosition;
import com.plexon.tools.storage.PlacedBlockPosition.ChunkKey;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Main-thread in-memory provenance index. Chunks begin UNKNOWN while their
 * persisted player-placement data is loading, then become NATURAL by default.
 */
final class PlacedBlockIndex {
    private final Map<CacheChunkKey, ChunkState> chunks = new HashMap<>();
    private final ChunkLookup lookup = new ChunkLookup();

    boolean beginLoad(ChunkKey key) {
        return beginLoad(key.worldId(), key.x(), key.z());
    }

    boolean beginLoad(UUID worldId, int chunkX, int chunkZ) {
        if (find(worldId, chunkX, chunkZ) != null) {
            return false;
        }
        chunks.put(new CacheChunkKey(worldId, chunkX, chunkZ), new ChunkState());
        return true;
    }

    boolean contains(ChunkKey key) {
        return find(key.worldId(), key.x(), key.z()) != null;
    }

    void completeLoad(ChunkKey key, Collection<PlacedBlockPosition> persisted) {
        ChunkState state = find(key.worldId(), key.x(), key.z());
        if (state == null || state.ready) {
            return;
        }
        for (PlacedBlockPosition position : persisted) {
            if (!position.chunk().equals(key)) {
                throw new IllegalArgumentException("Placed block does not belong to requested chunk: " + position);
            }
            if (!state.cleared.get(position.x(), position.y(), position.z())) {
                state.placed.set(position.x(), position.y(), position.z());
            }
        }
        state.cleared.clear();
        state.ready = true;
    }

    void unload(ChunkKey key) {
        lookup.set(key.worldId(), key.x(), key.z());
        chunks.remove(lookup);
    }

    void clear() {
        chunks.clear();
    }

    int loadedChunkCount() {
        return chunks.size();
    }

    int unknownChunkCount() {
        int count = 0;
        for (ChunkState state : chunks.values()) {
            if (!state.ready) {
                count++;
            }
        }
        return count;
    }

    int trackedPlacedPositionCount() {
        int count = 0;
        for (ChunkState state : chunks.values()) {
            count += state.placed.cardinality();
        }
        return count;
    }

    void markPlaced(PlacedBlockPosition position) {
        markPlaced(position.worldId(), position.x(), position.y(), position.z());
    }

    void markPlaced(UUID worldId, int x, int y, int z) {
        ChunkState state = findOrCreate(worldId, chunk(x), chunk(z));
        state.placed.set(x, y, z);
        state.cleared.unset(x, y, z);
    }

    Origin peek(PlacedBlockPosition position) {
        return peek(position.worldId(), position.x(), position.y(), position.z());
    }

    Origin peek(UUID worldId, int x, int y, int z) {
        ChunkState state = find(worldId, chunk(x), chunk(z));
        if (state == null) {
            return Origin.UNKNOWN;
        }
        if (state.placed.get(x, y, z)) {
            return Origin.PLAYER_PLACED;
        }
        return state.ready ? Origin.NATURAL : Origin.UNKNOWN;
    }

    Origin consume(PlacedBlockPosition position) {
        return consume(position.worldId(), position.x(), position.y(), position.z());
    }

    /**
     * Batch form used by bulk block operations. The caller owns ordering; this
     * method keeps all provenance mutation in one main-thread service boundary.
     */
    List<Origin> consumeAll(List<PlacedBlockPosition> positions) {
        if (positions.isEmpty()) {
            return List.of();
        }
        List<Origin> origins = new ArrayList<>(positions.size());
        for (PlacedBlockPosition position : positions) {
            origins.add(consume(position));
        }
        return List.copyOf(origins);
    }

    Origin consume(UUID worldId, int x, int y, int z) {
        ChunkState state = findOrCreate(worldId, chunk(x), chunk(z));
        Origin origin = state.placed.get(x, y, z)
                ? Origin.PLAYER_PLACED
                : state.ready ? Origin.NATURAL : Origin.UNKNOWN;
        state.placed.unset(x, y, z);
        if (!state.ready) {
            // A break while DB loading must win over a stale persisted row.
            state.cleared.set(x, y, z);
        }
        return origin;
    }

    private ChunkState findOrCreate(UUID worldId, int chunkX, int chunkZ) {
        ChunkState existing = find(worldId, chunkX, chunkZ);
        if (existing != null) {
            return existing;
        }
        ChunkState created = new ChunkState();
        chunks.put(new CacheChunkKey(worldId, chunkX, chunkZ), created);
        return created;
    }

    private ChunkState find(UUID worldId, int chunkX, int chunkZ) {
        lookup.set(worldId, chunkX, chunkZ);
        return chunks.get(lookup);
    }

    private static int chunk(int coordinate) {
        return Math.floorDiv(coordinate, 16);
    }

    enum Origin { NATURAL, PLAYER_PLACED, UNKNOWN }

    private interface ChunkCoordinate {
        UUID worldId();
        int x();
        int z();
    }

    private record CacheChunkKey(UUID worldId, int x, int z) implements ChunkCoordinate {
        private CacheChunkKey {
            Objects.requireNonNull(worldId);
        }
    }

    /** Reusable lookup key so block checks do not allocate a ChunkKey. */
    private static final class ChunkLookup implements ChunkCoordinate {
        private UUID worldId;
        private int x;
        private int z;

        void set(UUID worldId, int x, int z) {
            this.worldId = Objects.requireNonNull(worldId);
            this.x = x;
            this.z = z;
        }

        @Override public UUID worldId() { return worldId; }
        @Override public int x() { return x; }
        @Override public int z() { return z; }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof ChunkCoordinate coordinate)) return false;
            return x == coordinate.x() && z == coordinate.z() && worldId.equals(coordinate.worldId());
        }

        @Override
        public int hashCode() {
            int result = worldId.hashCode();
            result = 31 * result + x;
            return 31 * result + z;
        }
    }

    private static final class ChunkState {
        final SectionBits placed = new SectionBits();
        final SectionBits cleared = new SectionBits();
        boolean ready;
    }

    private static final class SectionBits {
        private final Map<Integer, BitSet> sections = new HashMap<>();

        boolean get(int x, int y, int z) {
            BitSet bits = sections.get(section(y));
            return bits != null && bits.get(index(x, y, z));
        }

        void set(int x, int y, int z) {
            sections.computeIfAbsent(section(y), ignored -> new BitSet(4096))
                    .set(index(x, y, z));
        }

        void unset(int x, int y, int z) {
            int section = section(y);
            BitSet bits = sections.get(section);
            if (bits == null) return;
            bits.clear(index(x, y, z));
            if (bits.isEmpty()) sections.remove(section);
        }

        int cardinality() {
            int count = 0;
            for (BitSet bits : sections.values()) {
                count += bits.cardinality();
            }
            return count;
        }

        void clear() { sections.clear(); }

        private static int section(int y) { return Math.floorDiv(y, 16); }
        private static int index(int x, int y, int z) {
            return (Math.floorMod(y, 16) << 8)
                    | (Math.floorMod(z, 16) << 4)
                    | Math.floorMod(x, 16);
        }
    }
}
