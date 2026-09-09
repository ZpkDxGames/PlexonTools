package com.plexon.tools.performance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.IntSupplier;

/**
 * Allocation-conscious, command-driven profiler for the synchronous mining path.
 *
 * <p>The service is intentionally a near-no-op while disabled. Timing samples are
 * retained in fixed-size rings so a long profiling session cannot grow memory
 * without bound. All mutation is expected on the Bukkit server thread.</p>
 */
public final class MiningPerformanceProfiler {
    private static final int SAMPLE_CAPACITY = 4096;

    private final StageStats[] stages = Arrays.stream(Stage.values())
            .map(ignored -> new StageStats(SAMPLE_CAPACITY))
            .toArray(StageStats[]::new);
    private final long[] counters = new long[Counter.values().length];
    private final boolean[] isolations = new boolean[Isolation.values().length];

    private volatile boolean enabled;
    private int autoStopSamples;
    private IntSupplier activeContextSupplier = () -> -1;
    private WorstSample worstSample;

    public boolean enabled() {
        return enabled;
    }

    public void startSession(int sampleLimit) {
        resetMetrics();
        clearIsolation();
        autoStopSamples = Math.max(0, sampleLimit);
        enabled = true;
    }

    public void stopSession() {
        enabled = false;
        autoStopSamples = 0;
        clearIsolation();
    }

    public void reset() {
        resetMetrics();
    }

    public void setActiveContextSupplier(IntSupplier supplier) {
        activeContextSupplier = Objects.requireNonNull(supplier, "supplier");
    }

    public long begin() {
        return enabled ? System.nanoTime() : 0L;
    }

    public void record(Stage stage, long startedNanos) {
        record(stage, startedNanos, SampleContext.EMPTY);
    }

    public void record(Stage stage, long startedNanos, SampleContext context) {
        if (!enabled || startedNanos == 0L) {
            return;
        }
        recordElapsed(stage, Math.max(0L, System.nanoTime() - startedNanos), context);
    }

    public void recordElapsed(Stage stage, long elapsedNanos) {
        recordElapsed(stage, elapsedNanos, SampleContext.EMPTY);
    }

    public void recordElapsed(Stage stage, long elapsedNanos, SampleContext context) {
        if (!enabled) {
            return;
        }
        long normalized = Math.max(0L, elapsedNanos);
        stages[stage.ordinal()].add(normalized);
        int activeContexts = context.activeContexts() >= 0
                ? context.activeContexts() : activeContextSupplier.getAsInt();
        if (worstSample == null || normalized > worstSample.durationNanos()) {
            worstSample = new WorstSample(
                    stage,
                    normalized,
                    context.pendingVisuals(),
                    context.pendingEventGroups(),
                    context.dirtyInstances(),
                    activeContexts);
        }
    }

    public void count(Counter counter) {
        count(counter, 1L);
    }

    public void count(Counter counter, long amount) {
        if (!enabled || amount <= 0L) {
            return;
        }
        int index = counter.ordinal();
        counters[index] = saturatingAdd(counters[index], amount);
    }

    /** Completes one profiled PlexonTools single-block sample and handles auto-stop. */
    public void completeBlockSample() {
        if (!enabled) {
            return;
        }
        count(Counter.BLOCK_BREAKS);
        if (autoStopSamples > 0 && counters[Counter.BLOCK_BREAKS.ordinal()] >= autoStopSamples) {
            enabled = false;
            autoStopSamples = 0;
            clearIsolation();
        }
    }

    public boolean isolated(Isolation isolation) {
        return isolations[isolation.ordinal()];
    }

    public boolean setIsolation(Isolation isolation, boolean isolated) {
        boolean previous = isolations[isolation.ordinal()];
        isolations[isolation.ordinal()] = isolated;
        return previous != isolated;
    }

    public void clearIsolation() {
        Arrays.fill(isolations, false);
    }

    public int blockSamples() {
        return (int) Math.min(Integer.MAX_VALUE, counters[Counter.BLOCK_BREAKS.ordinal()]);
    }

    public long counter(Counter counter) {
        return counters[counter.ordinal()];
    }

    public int autoStopSamples() {
        return autoStopSamples;
    }

    public List<String> reportLines() {
        List<String> lines = new ArrayList<>();
        lines.add("<gradient:#66BB6A:#42A5F5><bold>PlexonTools Mining Profile</bold></gradient>");
        lines.add("<gray>Status:</gray> <white>" + (enabled ? "RUNNING" : "STOPPED") + "</white>");
        lines.add("<gray>Samples:</gray> <white>" + counter(Counter.BLOCK_BREAKS) + "</white>");

        long hits = counter(Counter.CONTEXT_HITS);
        long misses = counter(Counter.CONTEXT_MISSES);
        long contextTotal = saturatingAdd(hits, misses);
        double hitRate = contextTotal == 0L ? 0.0D : (hits * 100.0D) / contextTotal;
        lines.add(String.format(Locale.ROOT,
                "<gray>Context hit rate:</gray> <white>%.2f%%</white> <dark_gray>(%d/%d)</dark_gray>",
                hitRate, hits, contextTotal));
        lines.add("<gray>PDC reads:</gray> <white>" + counter(Counter.PDC_IDENTITY_READS)
                + "</white> <dark_gray>•</dark_gray> <gray>UUID parses:</gray> <white>"
                + counter(Counter.UUID_PARSES) + "</white>");
        lines.add("<gray>Registry reads:</gray> <white>" + counter(Counter.REGISTRY_READS)
                + "</white> <dark_gray>•</dark_gray> <gray>mutations:</gray> <white>"
                + counter(Counter.REGISTRY_MUTATIONS) + "</white>");
        lines.add("<gray>Inventory scans:</gray> <white>" + counter(Counter.INVENTORY_SCANS)
                + "</white> <dark_gray>•</dark_gray> <gray>drop fallbacks:</gray> <white>"
                + counter(Counter.BLOCK_DROP_FALLBACKS) + "</white>");
        lines.add("<gray>Natural lookups:</gray> <white>" + counter(Counter.NATURAL_BLOCK_LOOKUPS)
                + "</white> <dark_gray>•</dark_gray> <gray>requirement calls:</gray> <white>"
                + counter(Counter.REQUIREMENT_PROGRESSION_CALLS) + "</white>");
        lines.add("<gray>Dirty first/repeat:</gray> <white>" + counter(Counter.DIRTY_QUEUE_FIRST_INSERTS)
                + "/" + counter(Counter.DIRTY_QUEUE_REPEATED_UPDATES) + "</white>");
        lines.add("<gray>Event groups created/merged:</gray> <white>"
                + counter(Counter.PROGRESS_BATCHES_CREATED) + "/"
                + counter(Counter.PROGRESS_BATCHES_MERGED) + "</white>");

        StageSnapshot block = snapshot(Stage.BLOCK_TOTAL);
        if (block.samples() > 0L) {
            lines.add("<dark_gray>────────────────────────────</dark_gray>");
            lines.add("<white><bold>Block total</bold></white> " + format(block));
        }

        List<StageSnapshot> activeStages = Arrays.stream(Stage.values())
                .map(this::snapshot)
                .filter(snapshot -> snapshot.samples() > 0L && snapshot.stage() != Stage.BLOCK_TOTAL)
                .sorted(Comparator.comparingLong(StageSnapshot::totalNanos).reversed())
                .limit(14)
                .toList();
        if (!activeStages.isEmpty()) {
            lines.add("<white><bold>Dominant stages</bold></white>");
            for (StageSnapshot stage : activeStages) {
                lines.add("<dark_gray>•</dark_gray> <gray>" + stage.stage().key()
                        + "</gray> <white>" + format(stage) + "</white>");
            }
        }

        if (worstSample != null) {
            lines.add("<dark_gray>────────────────────────────</dark_gray>");
            lines.add("<white><bold>Worst sample</bold></white> <gray>" + worstSample.stage().key()
                    + "</gray> <white>" + millis(worstSample.durationNanos()) + " ms</white>");
            lines.add("<gray>pending visuals/events:</gray> <white>"
                    + printable(worstSample.pendingVisuals()) + "/"
                    + printable(worstSample.pendingEventGroups()) + "</white>"
                    + " <dark_gray>•</dark_gray> <gray>dirty:</gray> <white>"
                    + printable(worstSample.dirtyInstances()) + "</white>"
                    + " <dark_gray>•</dark_gray> <gray>contexts:</gray> <white>"
                    + printable(worstSample.activeContexts()) + "</white>");
        }

        List<String> activeIsolation = Arrays.stream(Isolation.values())
                .filter(this::isolated)
                .map(Isolation::key)
                .toList();
        if (!activeIsolation.isEmpty()) {
            lines.add("<red><bold>DIAGNOSTIC ISOLATION ACTIVE:</bold></red> <yellow>"
                    + String.join(", ", activeIsolation) + "</yellow>");
        }
        return List.copyOf(lines);
    }

    public List<String> isolationStatusLines() {
        List<String> lines = new ArrayList<>();
        for (Isolation isolation : Isolation.values()) {
            lines.add(isolation.key() + "=" + (isolated(isolation) ? "ON" : "OFF"));
        }
        return List.copyOf(lines);
    }

    private StageSnapshot snapshot(Stage stage) {
        return stages[stage.ordinal()].snapshot(stage);
    }

    private void resetMetrics() {
        for (StageStats stats : stages) {
            stats.reset();
        }
        Arrays.fill(counters, 0L);
        worstSample = null;
    }

    private static String format(StageSnapshot snapshot) {
        return "avg " + millis(snapshot.averageNanos())
                + " ms • p95 " + millis(snapshot.p95Nanos())
                + " • p99 " + millis(snapshot.p99Nanos())
                + " • max " + millis(snapshot.maxNanos());
    }

    private static String millis(long nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0D);
    }

    private static String printable(int value) {
        return value < 0 ? "n/a" : Integer.toString(value);
    }

    private static long saturatingAdd(long first, long second) {
        return Long.MAX_VALUE - first < second ? Long.MAX_VALUE : first + second;
    }

    public enum Stage {
        BLOCK_TOTAL("block.total"),
        BLOCK_HIGH_TOTAL("block.high.total"),
        BLOCK_HIGH_CONTEXT("block.high.context"),
        BLOCK_HIGH_IDENTITY("block.high.identity"),
        BLOCK_HIGH_DEFINITION("block.high.definition"),
        BLOCK_HIGH_VALIDATION("block.high.validation"),
        BLOCK_HIGH_EXP("block.high.exp"),
        BLOCK_MONITOR_TOTAL("block.monitor.total"),
        BLOCK_MONITOR_LATEST_STATE("block.monitor.latest_state"),
        BLOCK_MONITOR_TARGET("block.monitor.target"),
        BLOCK_MONITOR_NATURAL("block.monitor.natural"),
        BLOCK_MONITOR_REQUIREMENT("block.monitor.requirement"),
        BLOCK_MONITOR_STATE_MUTATION("block.monitor.state_mutation"),
        BLOCK_MONITOR_REGISTRY("block.monitor.registry"),
        BLOCK_MONITOR_EVENT_BATCH("block.monitor.event_batch"),
        BLOCK_MONITOR_VISUAL_QUEUE("block.monitor.visual_queue"),
        BLOCK_MONITOR_DROP_CONTEXT("block.monitor.drop_context"),
        BLOCK_MONITOR_ABILITIES("block.monitor.abilities"),
        DROP_TOTAL("drop.total"),
        DROP_CONTEXT("drop.context"),
        DROP_ABILITIES("drop.abilities"),
        VISUAL_TOTAL("visual.total"),
        VISUAL_LOCATE("visual.locate"),
        VISUAL_ITEM_REFRESH("visual.item_refresh"),
        VISUAL_ACTIONBAR("visual.actionbar"),
        PERSISTENCE_MARK_DIRTY("persistence.mark_dirty"),
        TASK_VISUAL_REFRESH("task.visual_refresh"),
        TASK_PROGRESS_EVENT_FLUSH("task.progress_event_flush"),
        TASK_PASSIVE_EFFECT_REFRESH("task.passive_effect_refresh"),
        TASK_OTHER("task.other");

        private final String key;

        Stage(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    public enum Counter {
        BLOCK_BREAKS,
        CONTEXT_HITS,
        CONTEXT_MISSES,
        PDC_IDENTITY_READS,
        UUID_PARSES,
        DEFINITION_LOOKUPS,
        REGISTRY_READS,
        REGISTRY_MUTATIONS,
        INSTANCE_RECORD_CREATIONS,
        REQUIREMENT_PROGRESSION_CALLS,
        NATURAL_BLOCK_LOOKUPS,
        NATURAL_BLOCK_CONSUMES,
        DIRTY_QUEUE_FIRST_INSERTS,
        DIRTY_QUEUE_REPEATED_UPDATES,
        PROGRESS_BATCHES_CREATED,
        PROGRESS_BATCHES_MERGED,
        VISUAL_REFRESHES,
        INVENTORY_SCANS,
        BLOCK_DROP_FALLBACKS
    }

    public enum Isolation {
        PROGRESSION("progression"),
        NATURAL_TRACKING("natural-tracking"),
        REGISTRY_MUTATION("registry-mutation"),
        VISUAL_REFRESH("visual-refresh"),
        ACTIONBAR("actionbar"),
        ABILITIES("abilities"),
        DROP_ABILITIES("drop-abilities"),
        PROGRESS_EVENTS("progress-events");

        private final String key;

        Isolation(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        public static Isolation parse(String input) {
            String normalized = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
            for (Isolation isolation : values()) {
                if (isolation.key.equals(normalized)) {
                    return isolation;
                }
            }
            return null;
        }
    }

    public record SampleContext(
            int pendingVisuals,
            int pendingEventGroups,
            int dirtyInstances,
            int activeContexts
    ) {
        public static final SampleContext EMPTY = new SampleContext(-1, -1, -1, -1);

        public static SampleContext queues(int pendingVisuals, int pendingEventGroups,
                                           int dirtyInstances) {
            return new SampleContext(pendingVisuals, pendingEventGroups, dirtyInstances, -1);
        }
    }

    public record StageSnapshot(
            Stage stage,
            long samples,
            long totalNanos,
            long averageNanos,
            long minNanos,
            long maxNanos,
            long p50Nanos,
            long p95Nanos,
            long p99Nanos
    ) {
    }

    public record WorstSample(
            Stage stage,
            long durationNanos,
            int pendingVisuals,
            int pendingEventGroups,
            int dirtyInstances,
            int activeContexts
    ) {
    }

    private static final class StageStats {
        private final long[] reservoir;
        private long samples;
        private long totalNanos;
        private long minNanos = Long.MAX_VALUE;
        private long maxNanos;
        private int reservoirSize;
        private int cursor;

        private StageStats(int capacity) {
            reservoir = new long[capacity];
        }

        private void add(long nanos) {
            samples++;
            totalNanos = saturatingAdd(totalNanos, nanos);
            minNanos = Math.min(minNanos, nanos);
            maxNanos = Math.max(maxNanos, nanos);
            reservoir[cursor] = nanos;
            cursor = (cursor + 1) % reservoir.length;
            if (reservoirSize < reservoir.length) {
                reservoirSize++;
            }
        }

        private StageSnapshot snapshot(Stage stage) {
            if (samples == 0L) {
                return new StageSnapshot(stage, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
            }
            long[] sorted = Arrays.copyOf(reservoir, reservoirSize);
            Arrays.sort(sorted);
            return new StageSnapshot(
                    stage,
                    samples,
                    totalNanos,
                    totalNanos / samples,
                    minNanos == Long.MAX_VALUE ? 0L : minNanos,
                    maxNanos,
                    percentile(sorted, 0.50D),
                    percentile(sorted, 0.95D),
                    percentile(sorted, 0.99D));
        }

        private void reset() {
            samples = 0L;
            totalNanos = 0L;
            minNanos = Long.MAX_VALUE;
            maxNanos = 0L;
            reservoirSize = 0;
            cursor = 0;
        }

        private static long percentile(long[] sorted, double percentile) {
            if (sorted.length == 0) {
                return 0L;
            }
            int index = (int) Math.ceil(percentile * sorted.length) - 1;
            return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
        }
    }
}
