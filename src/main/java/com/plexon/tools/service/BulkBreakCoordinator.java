package com.plexon.tools.service;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Safety boundary for secondary blocks produced by bulk mining abilities.
 *
 * <p>Phase 2 deliberately removes the legacy Tools-owned recursive
 * {@link BlockBreakEvent} fan-out. A direct secondary-break implementation must
 * not bypass protection plugins, provenance, Core observers, or downstream
 * gameplay listeners; until an explicit protection-equivalent direct contract
 * is available, Area Mine is fail-closed instead of synthesizing Bukkit block
 * events for every adjacent block.</p>
 *
 * <p>The legacy configuration values remain parseable so existing installations
 * do not fail to load. They are reported as requested mode only; the effective
 * mode is {@link Mode#DISABLED_SAFE}.</p>
 */
public final class BulkBreakCoordinator {
    private static final int DEFAULT_MAX_SECONDARY_BLOCKS = 8;
    private static final int DEFAULT_MAX_BLOCKS_PER_PLAYER_PER_TICK = 8;
    private static final int ABSOLUTE_MAX_SECONDARY_BLOCKS = 64;
    private static final int ABSOLUTE_MAX_BLOCKS_PER_PLAYER_PER_TICK = 128;

    private final JavaPlugin plugin;
    private Mode requestedMode = Mode.STRICT_EVENTS;
    private Mode effectiveMode = Mode.DISABLED_SAFE;
    private int maxSecondaryBlocks = DEFAULT_MAX_SECONDARY_BLOCKS;
    private int maxBlocksPerPlayerPerTick = DEFAULT_MAX_BLOCKS_PER_PLAYER_PER_TICK;
    private long activations;
    private long blockedActivations;

    public BulkBreakCoordinator(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Reloads immutable runtime tuning once; gameplay never traverses YAML. */
    public void reload() {
        requestedMode = Mode.parse(plugin.getConfig().getString(
                "performance.area-mine.mode", Mode.STRICT_EVENTS.name()));
        effectiveMode = Mode.DISABLED_SAFE;
        maxSecondaryBlocks = clamp(
                plugin.getConfig().getInt("performance.area-mine.max-secondary-blocks",
                        DEFAULT_MAX_SECONDARY_BLOCKS),
                1, ABSOLUTE_MAX_SECONDARY_BLOCKS);
        maxBlocksPerPlayerPerTick = clamp(
                plugin.getConfig().getInt("performance.area-mine.max-blocks-per-player-per-tick",
                        DEFAULT_MAX_BLOCKS_PER_PLAYER_PER_TICK),
                1, ABSOLUTE_MAX_BLOCKS_PER_PLAYER_PER_TICK);
        if (requestedMode != Mode.DISABLED_SAFE) {
            plugin.getLogger().warning("Area Mine secondary breaking is disabled by the Phase 2 "
                    + "safety gate. PlexonTools no longer synthesizes recursive BlockBreakEvent "
                    + "fan-out; a future direct mode must provide explicit protection/provenance "
                    + "equivalence before it can be enabled.");
        }
    }

    public void clear() {
        // No per-player execution state is retained while the safe gate is closed.
    }

    public void clearPlayer(UUID playerId) {
        // No per-player execution state is retained while the safe gate is closed.
    }

    /**
     * Returns whether secondary Area Mine mutation is currently certified safe.
     * This is intentionally false in the Phase 2 RC.
     */
    public boolean enabled() {
        return effectiveMode != Mode.DISABLED_SAFE;
    }

    /**
     * Compatibility seam retained for AbilityService while Phase 2 is runtime
     * certified. It never constructs or dispatches a secondary BlockBreakEvent.
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
        blockedActivations++;
        return new Result(0, 0, true);
    }

    public Diagnostics diagnostics() {
        return new Diagnostics(
                requestedMode,
                effectiveMode,
                maxSecondaryBlocks,
                maxBlocksPerPlayerPerTick,
                0,
                activations,
                0L,
                0L,
                blockedActivations);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public enum Mode {
        STRICT_EVENTS,
        OPTIMIZED,
        DISABLED_SAFE;

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
}
