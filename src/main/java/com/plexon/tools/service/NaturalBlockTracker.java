package com.plexon.tools.service;

import com.plexon.tools.config.PluginSettings;
import com.plexon.tools.service.PlacedBlockIndex.Origin;
import com.plexon.tools.storage.InstanceRegistry;
import com.plexon.tools.storage.PlacedBlockPosition;
import com.plexon.tools.storage.PlacedBlockPosition.ChunkKey;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Tracks player-placed block provenance so BLOCKS_BROKEN/harvest progression
 * can reject artificial blocks without synchronous database access.
 *
 * <p>All index and queue mutations happen on the server thread. Chunk rows are
 * fetched asynchronously, then committed to the in-memory index on the server
 * thread. A generation/token pair prevents stale async results from reviving
 * unloaded or reloaded chunks.</p>
 */
public final class NaturalBlockTracker implements Listener {
    private final JavaPlugin plugin;
    private final PluginSettings settings;
    private final InstanceRegistry registry;
    private final PlacedBlockIndex index = new PlacedBlockIndex();
    private final ArrayDeque<ChunkLoadRequest> loadQueue = new ArrayDeque<>();
    private final Set<ChunkKey> queuedLoads = new HashSet<>();
    private final Map<ChunkKey, Long> loadTokens = new HashMap<>();

    private boolean active;
    private boolean loadInFlight;
    private long generation;
    private long nextLoadToken;
    private long lastLoadFailureWarning;

    public NaturalBlockTracker(JavaPlugin plugin, PluginSettings settings, InstanceRegistry registry) {
        this.plugin = plugin;
        this.settings = settings;
        this.registry = registry;
    }

    public void start() {
        generation++;
        active = settings.naturalBlockProgressionEnabled();
        loadInFlight = false;
        loadQueue.clear();
        queuedLoads.clear();
        loadTokens.clear();
        nextLoadToken = 0L;
        index.clear();
        if (!active) return;

        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                prime(chunk, false);
            }
        }
        pumpLoads();
    }

    public void stop() {
        generation++;
        active = false;
        loadInFlight = false;
        loadQueue.clear();
        queuedLoads.clear();
        loadTokens.clear();
        index.clear();
    }

    public boolean allowsProgress(Block block) {
        if (!active) return true;
        Origin origin = consume(block);
        return origin == Origin.NATURAL
                || (origin == Origin.UNKNOWN && !settings.naturalBlockFailClosed());
    }

    public boolean isNatural(Block block) {
        if (!active) return true;
        UUID worldId = block.getWorld().getUID();
        int x = block.getX();
        int z = block.getZ();
        ensureTracked(worldId, Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        Origin origin = index.peek(worldId, x, block.getY(), z);
        return origin == Origin.NATURAL
                || (origin == Origin.UNKNOWN && !settings.naturalBlockFailClosed());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!active || !event.canBuild()) return;
        if (event instanceof BlockMultiPlaceEvent multi) {
            for (BlockState state : multi.getReplacedBlockStates()) {
                markPlaced(state.getBlock());
            }
            return;
        }
        markPlaced(event.getBlockPlaced());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreakCleanup(BlockBreakEvent event) {
        if (active) consume(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        move(event.getBlocks(), event.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        move(event.getBlocks(), event.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        forgetAll(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        forgetAll(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFallingBlockLand(EntityChangeBlockEvent event) {
        if (!active || !(event.getEntity() instanceof FallingBlock falling) || event.getTo().isAir()) {
            return;
        }
        PlacedBlockPosition from = fallingOrigin(falling);
        if (from == null) return;
        PlacedBlockPosition to = position(event.getBlock());

        ensureTracked(from.chunk());
        Origin origin = index.peek(from);
        consume(from);
        consume(to);
        if (origin != Origin.NATURAL) {
            ensureTracked(to.chunk());
            index.markPlaced(to);
            registry.queuePlacedBlock(to, true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFallingBlockDrop(EntityDropItemEvent event) {
        Entity entity = event.getEntity();
        if (!active || !(entity instanceof FallingBlock falling)) return;
        PlacedBlockPosition origin = fallingOrigin(falling);
        if (origin != null) consume(origin);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!active) return;
        prime(event.getChunk(), event.isNewChunk());
        pumpLoads();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (!active) return;
        ChunkKey key = chunkKey(event.getChunk());
        index.unload(key);
        loadTokens.remove(key);
        if (queuedLoads.remove(key)) {
            loadQueue.removeIf(request -> request.key().equals(key));
        }
    }

    private void markPlaced(Block block) {
        PlacedBlockPosition position = position(block);
        ensureTracked(position.chunk());
        index.markPlaced(position);
        registry.queuePlacedBlock(position, true);
    }

    private Origin consume(PlacedBlockPosition position) {
        ensureTracked(position.chunk());
        Origin origin = index.consume(position);
        if (origin != Origin.NATURAL) {
            registry.queuePlacedBlock(position, false);
        }
        return origin;
    }

    private Origin consume(Block block) {
        UUID worldId = block.getWorld().getUID();
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        ensureTracked(worldId, Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        Origin origin = index.consume(worldId, x, y, z);
        if (origin != Origin.NATURAL) {
            registry.queuePlacedBlock(new PlacedBlockPosition(worldId, x, y, z), false);
        }
        return origin;
    }

    private void move(List<Block> blocks, BlockFace direction) {
        if (!active || blocks.isEmpty()) return;
        List<BlockMove> moves = new ArrayList<>(blocks.size());
        for (Block block : blocks) {
            PlacedBlockPosition from = position(block);
            ensureTracked(from.chunk());
            moves.add(new BlockMove(from, position(block.getRelative(direction)), index.peek(from)));
        }
        // Clear all sources/destinations before replaying provenance so swaps and
        // multi-block piston movement cannot contaminate neighboring entries.
        for (BlockMove move : moves) {
            consume(move.from());
            consume(move.to());
        }
        for (BlockMove move : moves) {
            if (move.origin() != Origin.NATURAL) {
                ensureTracked(move.to().chunk());
                index.markPlaced(move.to());
                registry.queuePlacedBlock(move.to(), true);
            }
        }
    }

    private void forgetAll(Collection<Block> blocks) {
        if (!active) return;
        for (Block block : blocks) consume(block);
    }

    private void prime(Chunk chunk, boolean newChunk) {
        ChunkKey key = chunkKey(chunk);
        if (!index.beginLoad(key)) return;
        loadTokens.put(key, ++nextLoadToken);
        if (newChunk) {
            index.completeLoad(key, List.of());
        } else {
            queueLoad(key);
        }
    }

    private void ensureTracked(ChunkKey key) {
        if (index.beginLoad(key)) {
            loadTokens.put(key, ++nextLoadToken);
            queueLoad(key);
            pumpLoads();
        }
    }

    private void ensureTracked(UUID worldId, int chunkX, int chunkZ) {
        if (!index.beginLoad(worldId, chunkX, chunkZ)) return;
        ChunkKey key = new ChunkKey(worldId, chunkX, chunkZ);
        loadTokens.put(key, ++nextLoadToken);
        queueLoad(key);
        pumpLoads();
    }

    private void queueLoad(ChunkKey key) {
        Long token = loadTokens.get(key);
        if (token != null && queuedLoads.add(key)) {
            loadQueue.addLast(new ChunkLoadRequest(key, token));
        }
    }

    private void pumpLoads() {
        if (!active || loadInFlight) return;
        while (!loadQueue.isEmpty()) {
            List<ChunkLoadRequest> batch = new ArrayList<>(settings.naturalBlockChunkLoadBatchSize());
            while (!loadQueue.isEmpty() && batch.size() < settings.naturalBlockChunkLoadBatchSize()) {
                ChunkLoadRequest request = loadQueue.removeFirst();
                queuedLoads.remove(request.key());
                if (index.contains(request.key())
                        && loadTokens.getOrDefault(request.key(), -1L) == request.token()) {
                    batch.add(request);
                }
            }
            if (!batch.isEmpty()) {
                dispatchLoad(batch);
                return;
            }
        }
    }

    private void dispatchLoad(List<ChunkLoadRequest> requests) {
        long currentGeneration = generation;
        loadInFlight = true;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<ChunkKey, List<PlacedBlockPosition>> loaded = null;
            Exception failure = null;
            try {
                loaded = registry.loadPlacedBlocks(requests.stream().map(ChunkLoadRequest::key).toList());
            } catch (SQLException | RuntimeException exception) {
                failure = exception;
            }
            if (!plugin.isEnabled()) return;
            Map<ChunkKey, List<PlacedBlockPosition>> result = loaded;
            Exception error = failure;
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> finishLoad(currentGeneration, requests, result, error));
        });
    }

    private void finishLoad(
            long expectedGeneration,
            List<ChunkLoadRequest> requests,
            Map<ChunkKey, List<PlacedBlockPosition>> loaded,
            Exception failure
    ) {
        if (!active || expectedGeneration != generation) return;
        loadInFlight = false;
        if (failure == null) {
            for (ChunkLoadRequest request : requests) {
                if (loadTokens.getOrDefault(request.key(), -1L) == request.token()) {
                    index.completeLoad(request.key(), loaded.getOrDefault(request.key(), List.of()));
                }
            }
            pumpLoads();
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastLoadFailureWarning >= 10_000L) {
            lastLoadFailureWarning = now;
            plugin.getLogger().log(Level.WARNING,
                    "Could not load placed-block provenance; affected chunks remain fail-closed",
                    failure);
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!active || expectedGeneration != generation) return;
            for (ChunkLoadRequest request : requests) {
                if (index.contains(request.key())
                        && loadTokens.getOrDefault(request.key(), -1L) == request.token()) {
                    queueLoad(request.key());
                }
            }
            pumpLoads();
        }, settings.naturalBlockChunkLoadRetryTicks());
    }

    private static PlacedBlockPosition position(Block block) {
        return new PlacedBlockPosition(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    private static PlacedBlockPosition fallingOrigin(FallingBlock falling) {
        Location origin = falling.getOrigin();
        World world = origin.getWorld();
        return world == null ? null : new PlacedBlockPosition(
                world.getUID(), origin.getBlockX(), origin.getBlockY(), origin.getBlockZ());
    }

    private static ChunkKey chunkKey(Chunk chunk) {
        return new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
    }

    private record ChunkLoadRequest(ChunkKey key, long token) {}
    private record BlockMove(PlacedBlockPosition from, PlacedBlockPosition to, Origin origin) {}
}
