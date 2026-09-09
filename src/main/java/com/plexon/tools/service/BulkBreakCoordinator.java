package com.plexon.tools.service;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Bounded coordinator for secondary blocks produced by bulk mining abilities.
 *
 * <p>4.2 keeps {@link Mode#STRICT_EVENTS} as the effective production mode so
 * third-party protection/listener semantics remain authoritative. The service
 * owns deduplication and per-player tick budgets now, which prevents future
 * area-size changes from becoming an unbounded synchronous event fan-out and
 * provides a focused boundary for hook-based optimized processing later.</p>
 */
public final class BulkBreakCoordinator {
    private static final int DEFAULT_MAX_SECONDARY_BLOCKS = 8;
    private static final int DEFAULT_MAX_BLOCKS_PER_PLAYER_PER_TICK = 8;
    private static final int ABSOLUTE_MAX_SECONDARY_BLOCKS = 64;
    private static final int ABSOLUTE_MAX_BLOCKS_PER_PLAYER_PER_TICK = 128;

    private final JavaPlugin plugin;
    private final Map<UUID, TickBudget> playerBudgets = new HashMap<>();
    private Mode requestedMode = Mode.STRICT_EVENTS;
    private Mode effectiveMode = Mode.STRICT_EVENTS;
    private int maxSecondaryBlocks = DEFAULT_MAX_SECONDARY_BLOCKS;
    private int maxBlocksPerPlayerPerTick = DEFAULT_MAX_BLOCKS_PER_PLAYER_PER_TICK;
    private long activations;
    private long dispatchedBlocks;
    private long acceptedBlocks;
    private long budgetLimitedActivations;

    public BulkBreakCoordinator(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Reloads immutable runtime tuning once; gameplay never traverses YAML. */
    public void reload() {
        requestedMode = Mode.parse(plugin.getConfig().getString(
                "performance.area-mine.mode", Mode.STRICT_EVENTS.name()));
        // OPTIMIZED is deliberately not activated until explicit protection
        // integrations can prove equivalent cancellation/authorization safety.
        effectiveMode = Mode.STRICT_EVENTS;
        if (requestedMode == Mode.OPTIMIZED) {
            plugin.getLogger().warning("performance.area-mine.mode=OPTIMIZED requested, but no "
                    + "protection-safe optimized integration set is available; using STRICT_EVENTS.");
        }
        maxSecondaryBlocks = clamp(
                plugin.getConfig().getInt("performance.area-mine.max-secondary-blocks",
                        DEFAULT_MAX_SECONDARY_BLOCKS),
                1, ABSOLUTE_MAX_SECONDARY_BLOCKS);
        maxBlocksPerPlayerPerTick = clamp(
                plugin.getConfig().getInt("performance.area-mine.max-blocks-per-player-per-tick",
                        DEFAULT_MAX_BLOCKS_PER_PLAYER_PER_TICK),
                1, ABSOLUTE_MAX_BLOCKS_PER_PLAYER_PER_TICK);
        playerBudgets.clear();
    }

    public void clear() {
        playerBudgets.clear();
    }

    public void clearPlayer(UUID playerId) {
        playerBudgets.remove(playerId);
    }

    /**
     * Dispatches a bounded number of compatible secondary block events.
     * Eligibility is checked before consuming event budget. Accepted events are
     * handed to the caller for the actual block/drop/experience mutation.
     */
    public Result breakSecondaryBlocks(
            Player player,
            List<Block> candidates,
            Predicate<Block> eligible,
            Consumer<BlockBreakEvent> acceptedHandler
    ) {
        if (candidates.isEmpty()) {
            return Result.EMPTY;
        }
        activations++;
        UUID playerId = player.getUniqueId();
        UUID worldId = player.getWorld().getUID();
        long tick = player.getWorld().getGameTime();
        TickBudget previous = playerBudgets.get(playerId);
        int alreadyUsed = previous != null
                && previous.worldId().equals(worldId)
                && previous.tick() == tick
                ? previous.used() : 0;
        int tickRemaining = Math.max(0, maxBlocksPerPlayerPerTick - alreadyUsed);
        int activationLimit = Math.min(maxSecondaryBlocks, tickRemaining);
        if (activationLimit <= 0) {
            budgetLimitedActivations++;
            return new Result(0, 0, true);
        }

        Set<BlockPosition> seen = new HashSet<>(Math.min(candidates.size(), activationLimit) * 2);
        int dispatched = 0;
        int accepted = 0;
        boolean limited = false;
        for (Block block : candidates) {
            if (dispatched >= activationLimit) {
                limited = true;
                break;
            }
            if (!eligible.test(block)) {
                continue;
            }
            BlockPosition position = new BlockPosition(
                    block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
            if (!seen.add(position)) {
                continue;
            }

            BlockBreakEvent event = new BlockBreakEvent(block, player);
            Bukkit.getPluginManager().callEvent(event);
            dispatched++;
            if (event.isCancelled() || block.getType().isAir()) {
                continue;
            }
            acceptedHandler.accept(event);
            accepted++;
        }

        int nowUsed = alreadyUsed + dispatched;
        playerBudgets.put(playerId, new TickBudget(worldId, tick, nowUsed));
        dispatchedBlocks += dispatched;
        acceptedBlocks += accepted;
        if (limited || candidates.size() > maxSecondaryBlocks) {
            budgetLimitedActivations++;
            limited = true;
        }
        return new Result(dispatched, accepted, limited);
    }

    public Diagnostics diagnostics() {
        return new Diagnostics(
                requestedMode,
                effectiveMode,
                maxSecondaryBlocks,
                maxBlocksPerPlayerPerTick,
                playerBudgets.size(),
                activations,
                dispatchedBlocks,
                acceptedBlocks,
                budgetLimitedActivations);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public enum Mode {
        STRICT_EVENTS,
        OPTIMIZED;

        static Mode parse(String raw) {
            if (raw == null) {
                return STRICT_EVENTS;
            }
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException exception) {
                return STRICT_EVENTS;
            }
        }
    }

    public record Result(int dispatched, int accepted, boolean budgetLimited) {
        private static final Result EMPTY = new Result(0, 0, false);
    }

    public record Diagnostics(
            Mode requestedMode,
            Mode effectiveMode,
            int maxSecondaryBlocks,
            int maxBlocksPerPlayerPerTick,
            int trackedPlayerBudgets,
            long activations,
            long dispatchedBlocks,
            long acceptedBlocks,
            long budgetLimitedActivations
    ) {}

    private record TickBudget(UUID worldId, long tick, int used) {}
    private record BlockPosition(UUID worldId, int x, int y, int z) {}
}
