package com.plexon.tools.storage;

import com.plexon.tools.config.PluginSettings;
import com.plexon.tools.item.ToolState;
import com.plexon.tools.model.LevelRequirement;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * Authoritative in-memory instance registry with asynchronous SQLite flushing.
 *
 * <p>Active progression mutates the already-loaded runtime record in place.
 * Immutable/detached {@link InstanceRecord} snapshots are materialized only for
 * consumers and persistence batches, rather than rebuilding a complete record
 * for every +1 mining increment.</p>
 */
public final class InstanceRegistry {
    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter
            .ofPattern("uuuuMMdd-HHmmss-SSS'Z'")
            .withZone(ZoneOffset.UTC);
    private final JavaPlugin plugin;
    private final PluginSettings settings;
    private final Path legacyFile;
    private final RegistryDatabase database;
    private final ConcurrentMap<UUID, InstanceRecord> records = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> recordRevisions = new ConcurrentHashMap<>();
    private final AtomicLong revision = new AtomicLong();
    private final AtomicBoolean saving = new AtomicBoolean();
    private final AtomicBoolean pressureFlushQueued = new AtomicBoolean();
    private final AtomicLong committedBatches = new AtomicLong();
    private final AtomicLong committedEntries = new AtomicLong();
    private final AtomicLong failedWriteBatches = new AtomicLong();
    private final AtomicLong pressureFlushCount = new AtomicLong();
    private final Object pendingLock = new Object();
    private final Object databaseLock = new Object();
    private final LinkedHashMap<UUID, Long> pendingWrites = new LinkedHashMap<>();
    private final LinkedHashMap<PlacedBlockPosition, PendingPlacedWrite> pendingPlacedBlockWrites =
            new LinkedHashMap<>();
    private volatile int pendingHighWaterMark;
    private boolean fullSnapshotPending;
    private boolean fullSnapshotInFlight;

    public InstanceRegistry(JavaPlugin plugin, PluginSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
        Path dataFolder = plugin.getDataFolder().toPath();
        legacyFile = dataFolder.resolve("data.yml");
        database = new RegistryDatabase(
                dataFolder.resolve(settings.databaseFile()),
                settings.databaseBusyTimeoutMillis(),
                settings.databaseWalAutoCheckpointPages(),
                settings.databaseIntegrityCheck());
    }

    public void load() throws IOException, InvalidConfigurationException, SQLException {
        records.clear();
        resetPendingState();

        LegacyYamlRegistry.Snapshot legacy = null;
        boolean databaseMissing = Files.notExists(database.file())
                || Files.size(database.file()) == 0L;
        if (databaseMissing && Files.exists(legacyFile)) {
            legacy = LegacyYamlRegistry.read(legacyFile);
        }

        synchronized (databaseLock) {
            database.open();
            Path schemaBackup = database.schemaMigrationBackup();
            if (schemaBackup != null) {
                plugin.getLogger().info("Backed up the pre-4.0 SQLite schema as "
                        + schemaBackup.getFileName() + ".");
            }
            if (!"wal".equals(database.journalMode())) {
                plugin.getLogger().warning("SQLite WAL mode is unavailable; using journal mode "
                        + database.journalMode() + ". Runtime writes remain asynchronous.");
            }

            boolean migrationCandidate = Files.exists(legacyFile)
                    && !database.migrationComplete()
                    && database.recordCount() == 0L;
            if (migrationCandidate) {
                if (legacy == null) {
                    legacy = LegacyYamlRegistry.read(legacyFile);
                }
                Path backup = LegacyYamlRegistry.createBackup(legacyFile);
                boolean migrated = database.importLegacy(
                        legacy.records(), legacy.schemaVersion(), legacy.sha256(),
                        backup.getFileName().toString());
                if (migrated) {
                    plugin.getLogger().info("Migrated " + legacy.records().size()
                            + " tool instances from data.yml to " + database.file().getFileName()
                            + "; preserved " + backup.getFileName() + ".");
                }
            } else if (Files.exists(legacyFile) && !database.migrationComplete()
                    && database.recordCount() > 0L) {
                plugin.getLogger().warning("Ignoring legacy data.yml because the SQLite database "
                        + "already contains tool instances. Back up and inspect both files before "
                        + "manually replacing either one.");
            }
            records.putAll(database.loadAll());
        }
    }

    public void register(ToolState state, String ownerName) {
        register(state, ownerName, true);
    }

    public void register(ToolState state, String ownerName, boolean active) {
        register(state, ownerName, active, false);
    }

    public void register(ToolState state, String ownerName, boolean active, boolean menuManaged) {
        long now = System.currentTimeMillis();
        InstanceRecord created = new InstanceRecord(
                state.instanceId(), state.toolId(), state.categoryId(), state.ownerId(), ownerName,
                state.boundWorld(), state.level(), state.progress(), state.targetProgress(),
                active, menuManaged, 0L, now, now);
        InstanceRecord previous = records.putIfAbsent(state.instanceId(), created);
        if (previous == null) {
            markDirty(state.instanceId());
        }
    }

    /**
     * Mutates the already-resident runtime record. This is the high-frequency
     * progression path and intentionally avoids ConcurrentHashMap.compute and
     * immutable InstanceRecord reconstruction for each block.
     */
    public void update(ToolState state, long progressAdded, String ownerName) {
        long now = System.currentTimeMillis();
        InstanceRecord record = records.get(state.instanceId());
        if (record == null) {
            InstanceRecord created = new InstanceRecord(
                    state.instanceId(), state.toolId(), state.categoryId(), state.ownerId(), ownerName,
                    state.boundWorld(), state.level(), state.progress(), state.targetProgress(),
                    true, false, Math.max(0L, progressAdded), now, now);
            InstanceRecord raced = records.putIfAbsent(state.instanceId(), created);
            record = raced == null ? created : raced;
            if (raced != null) {
                record.updateFrom(state, progressAdded, ownerName, now);
            }
        } else {
            record.updateFrom(state, progressAdded, ownerName, now);
        }
        markDirty(state.instanceId());
    }

    public Optional<InstanceRecord> find(UUID instanceId) {
        return Optional.ofNullable(findCached(instanceId));
    }

    /**
     * Returns a detached authoritative snapshot. Runtime mutation stays private
     * to the registry and is never exposed through the public API.
     */
    public InstanceRecord findCached(UUID instanceId) {
        InstanceRecord record = records.get(instanceId);
        return record == null ? null : record.snapshot();
    }

    public List<InstanceRecord> findOwned(UUID ownerId, String toolId, String boundWorld) {
        return findOwned(ownerId, toolId).stream()
                .filter(record -> record.boundWorld().equalsIgnoreCase(boundWorld))
                .toList();
    }

    public List<InstanceRecord> findOwned(UUID ownerId, String toolId) {
        return records.values().stream()
                .map(InstanceRecord::snapshot)
                .filter(record -> record.ownerId().equals(ownerId))
                .filter(record -> record.toolId().equalsIgnoreCase(toolId))
                .sorted(Comparator.comparingLong(InstanceRecord::updatedAt).reversed())
                .toList();
    }

    public List<InstanceRecord> findActive(UUID ownerId, String boundWorld) {
        return findActive(ownerId).stream()
                .filter(record -> record.boundWorld().equalsIgnoreCase(boundWorld))
                .toList();
    }

    public List<InstanceRecord> findActive(UUID ownerId) {
        return records.values().stream()
                .map(InstanceRecord::snapshot)
                .filter(InstanceRecord::active)
                .filter(record -> record.ownerId().equals(ownerId))
                .sorted(Comparator
                        .comparing(InstanceRecord::toolId, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Comparator.comparingLong(
                                InstanceRecord::updatedAt).reversed()))
                .toList();
    }

    public void setActive(UUID instanceId, boolean active) {
        InstanceRecord record = records.get(instanceId);
        if (record != null && record.setActive(active, System.currentTimeMillis())) {
            markDirty(instanceId);
        }
    }

    public void setMenuManaged(UUID instanceId, boolean menuManaged) {
        InstanceRecord record = records.get(instanceId);
        if (record != null && record.setMenuManaged(menuManaged, System.currentTimeMillis())) {
            markDirty(instanceId);
        }
    }

    public int size() {
        return records.size();
    }

    public Path databaseFile() {
        return database.file();
    }

    /** Cached SQLite journal mode, used by low-frequency diagnostics. */
    public String journalMode() {
        synchronized (databaseLock) {
            return database.journalMode();
        }
    }

    public int pendingWriteCount() {
        synchronized (pendingLock) {
            return pendingCountLocked();
        }
    }

    public int pendingPlacedBlockWriteCount() {
        synchronized (pendingLock) {
            return pendingPlacedBlockWrites.size();
        }
    }

    /**
     * Cheap snapshot-only persistence diagnostics. Hot gameplay mutation keeps
     * using the existing revision counter and already-computed queue sizes;
     * metrics do not create a task/future/record per block.
     */
    public PersistenceDiagnostics persistenceDiagnostics() {
        int pending;
        int placedPending;
        boolean snapshotPending;
        boolean snapshotInFlight;
        synchronized (pendingLock) {
            pending = pendingCountLocked();
            placedPending = pendingPlacedBlockWrites.size();
            snapshotPending = fullSnapshotPending;
            snapshotInFlight = fullSnapshotInFlight;
        }
        return new PersistenceDiagnostics(
                pending,
                placedPending,
                pendingHighWaterMark,
                revision.get(),
                committedBatches.get(),
                committedEntries.get(),
                pressureFlushCount.get(),
                failedWriteBatches.get(),
                saving.get(),
                pressureFlushQueued.get(),
                snapshotPending,
                snapshotInFlight);
    }

    public void queuePlacedBlock(PlacedBlockPosition position, boolean placed) {
        long currentRevision = revision.incrementAndGet();
        int pending;
        synchronized (pendingLock) {
            pendingPlacedBlockWrites.put(position, new PendingPlacedWrite(placed, currentRevision));
            pending = pendingCountLocked();
        }
        updatePendingHighWater(pending);
        requestPressureFlush(pending);
    }

    /**
     * Batch provenance enqueue used by bulk block operations. It preserves a
     * monotonic revision per position but acquires the pending-write lock once
     * and requests at most one pressure flush for the whole batch.
     */
    public void queuePlacedBlocks(Map<PlacedBlockPosition, Boolean> changes) {
        if (changes.isEmpty()) {
            return;
        }
        int pending;
        synchronized (pendingLock) {
            changes.forEach((position, placed) -> {
                Objects.requireNonNull(position, "Placed-block position is required.");
                Objects.requireNonNull(placed, "Placed-block state is required.");
                long currentRevision = revision.incrementAndGet();
                pendingPlacedBlockWrites.put(
                        position, new PendingPlacedWrite(placed, currentRevision));
            });
            pending = pendingCountLocked();
        }
        updatePendingHighWater(pending);
        requestPressureFlush(pending);
    }

    public Map<PlacedBlockPosition.ChunkKey, List<PlacedBlockPosition>> loadPlacedBlocks(
            java.util.Collection<PlacedBlockPosition.ChunkKey> chunks
    ) throws SQLException {
        if (chunks.isEmpty()) return Map.of();
        Set<PlacedBlockPosition.ChunkKey> requested = Set.copyOf(new HashSet<>(chunks));
        long barrierRevision = revision.get();
        synchronized (databaseLock) {
            PlacedBlockBatch pending;
            while ((pending = nextPlacedBlockBatch(requested, barrierRevision)) != null) {
                persist(pending);
            }
            return database.loadPlacedBlocks(requested);
        }
    }

    /**
     * Flushes coalesced record updates in bounded transactions. Call from an
     * asynchronous Bukkit task, never from a gameplay event handler.
     */
    public void flushAsync() {
        if (!saving.compareAndSet(false, true)) {
            return;
        }
        try {
            synchronized (databaseLock) {
                for (int batch = 0; batch < settings.databaseMaxBatchesPerFlush(); batch++) {
                    PendingBatch pending = nextBatch();
                    PlacedBlockBatch placed = nextPlacedBlockBatch(null, Long.MAX_VALUE);
                    if (pending == null && placed == null) break;
                    if (pending != null) persist(pending);
                    if (placed != null) persist(placed);
                }
            }
        } catch (SQLException | RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Could not flush PlexonTools SQLite updates; they remain queued", exception);
        } finally {
            saving.set(false);
        }
    }

    /**
     * Drains all queued updates, checkpoints WAL, and closes the database.
     * Call after gameplay handlers and the asynchronous flush task stop.
     */
    public void shutdown() {
        synchronized (databaseLock) {
            try {
                while (true) {
                    PendingBatch pending = nextBatch();
                    PlacedBlockBatch placed = nextPlacedBlockBatch(null, Long.MAX_VALUE);
                    if (pending == null && placed == null) break;
                    if (pending != null) persist(pending);
                    if (placed != null) persist(placed);
                }
                database.close();
            } catch (SQLException exception) {
                throw new IllegalStateException(
                        "Could not drain or close the PlexonTools SQLite registry", exception);
            }
        }
    }

    public Path createBackup() throws IOException, SQLException {
        synchronized (databaseLock) {
            while (true) {
                PendingBatch pending = nextBatch();
                PlacedBlockBatch placed = nextPlacedBlockBatch(null, Long.MAX_VALUE);
                if (pending == null && placed == null) break;
                if (pending != null) persist(pending);
                if (placed != null) persist(placed);
            }
            Path backups = database.file().resolveSibling("backups");
            String baseName = "plexontools-" + BACKUP_TIME.format(Instant.now());
            Path destination = backups.resolve(baseName + ".db");
            int collision = 1;
            while (Files.exists(destination)) {
                destination = backups.resolve(baseName + "-" + collision++ + ".db");
            }
            return database.backup(destination);
        }
    }

    /**
     * Marks an instance dirty once per pending persistence window while keeping
     * a separate latest revision for race-safe asynchronous acknowledgement.
     */
    private void markDirty(UUID instanceId) {
        long currentRevision = revision.incrementAndGet();
        recordRevisions.put(instanceId, currentRevision);
        int pending;
        synchronized (pendingLock) {
            if (fullSnapshotPending) {
                pending = pendingCountLocked();
            } else {
                Long previousRevision = pendingWrites.putIfAbsent(instanceId, currentRevision);
                if (previousRevision == null
                        && pendingWrites.size() > settings.databaseMaxPendingWrites()) {
                    pendingWrites.clear();
                    fullSnapshotPending = true;
                }
                pending = pendingCountLocked();
            }
        }
        updatePendingHighWater(pending);
        requestPressureFlush(pending);
    }

    private int pendingCountLocked() {
        int toolWrites = fullSnapshotPending || fullSnapshotInFlight
                ? records.size() : pendingWrites.size();
        return toolWrites + pendingPlacedBlockWrites.size();
    }

    private void updatePendingHighWater(int pendingCount) {
        if (pendingCount > pendingHighWaterMark) {
            pendingHighWaterMark = pendingCount;
        }
    }

    private void requestPressureFlush(int pendingCount) {
        if (pendingCount < settings.databasePressureFlushThreshold()
                || !plugin.isEnabled()
                || !pressureFlushQueued.compareAndSet(false, true)) {
            return;
        }
        pressureFlushCount.incrementAndGet();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                flushAsync();
            } finally {
                pressureFlushQueued.set(false);
            }
        });
    }

    private PendingBatch nextBatch() {
        synchronized (pendingLock) {
            if (fullSnapshotPending) {
                // Changes completed before this transition are represented by
                // the snapshot. Later changes enter pendingWrites as deltas.
                fullSnapshotPending = false;
                fullSnapshotInFlight = true;
            } else {
                if (pendingWrites.isEmpty()) {
                    return null;
                }
                Map<UUID, Long> revisions = new LinkedHashMap<>();
                List<InstanceRecord> batch = new ArrayList<>();
                for (Map.Entry<UUID, Long> entry : pendingWrites.entrySet()) {
                    InstanceRecord record = records.get(entry.getKey());
                    if (record != null) {
                        long latestRevision = recordRevisions.getOrDefault(
                                entry.getKey(), entry.getValue());
                        revisions.put(entry.getKey(), latestRevision);
                        batch.add(record.snapshot());
                    }
                    if (revisions.size() >= settings.databaseWriteBatchSize()) {
                        break;
                    }
                }
                if (revisions.isEmpty()) {
                    pendingWrites.clear();
                    return null;
                }
                return new PendingBatch(
                        List.copyOf(batch), Map.copyOf(revisions), false);
            }
        }
        // A full snapshot can be large. Copy it on the asynchronous caller
        // without holding the lock used by gameplay-thread markDirty calls.
        List<InstanceRecord> snapshot = records.values().stream()
                .map(InstanceRecord::snapshot)
                .sorted(Comparator.comparing(record -> record.instanceId().toString()))
                .toList();
        return new PendingBatch(snapshot, Map.of(), true);
    }

    private void persist(PendingBatch batch) throws SQLException {
        try {
            database.upsert(batch.records());
            committedBatches.incrementAndGet();
            committedEntries.addAndGet(batch.records().size());
            acknowledge(batch);
        } catch (SQLException | RuntimeException exception) {
            failedWriteBatches.incrementAndGet();
            reject(batch);
            throw exception;
        }
    }

    private PlacedBlockBatch nextPlacedBlockBatch(
            Set<PlacedBlockPosition.ChunkKey> chunks, long maxRevision
    ) {
        synchronized (pendingLock) {
            if (pendingPlacedBlockWrites.isEmpty()) return null;
            Map<PlacedBlockPosition, Boolean> changes = new LinkedHashMap<>();
            Map<PlacedBlockPosition, Long> revisions = new LinkedHashMap<>();
            for (Map.Entry<PlacedBlockPosition, PendingPlacedWrite> entry
                    : pendingPlacedBlockWrites.entrySet()) {
                PendingPlacedWrite write = entry.getValue();
                if (write.revision() > maxRevision) continue;
                if (chunks != null && !chunks.contains(entry.getKey().chunk())) continue;
                changes.put(entry.getKey(), write.placed());
                revisions.put(entry.getKey(), write.revision());
                if (changes.size() >= settings.databaseWriteBatchSize()) break;
            }
            return changes.isEmpty() ? null
                    : new PlacedBlockBatch(Map.copyOf(changes), Map.copyOf(revisions));
        }
    }

    private void persist(PlacedBlockBatch batch) throws SQLException {
        try {
            database.applyPlacedBlockChanges(batch.changes());
            committedBatches.incrementAndGet();
            committedEntries.addAndGet(batch.changes().size());
        } catch (SQLException | RuntimeException exception) {
            failedWriteBatches.incrementAndGet();
            throw exception;
        }
        synchronized (pendingLock) {
            batch.revisions().forEach((position, batchRevision) -> {
                PendingPlacedWrite current = pendingPlacedBlockWrites.get(position);
                if (current != null && current.revision() == batchRevision) {
                    pendingPlacedBlockWrites.remove(position);
                }
            });
        }
    }

    private void acknowledge(PendingBatch batch) {
        synchronized (pendingLock) {
            if (batch.fullSnapshot()) {
                fullSnapshotInFlight = false;
                return;
            }
            batch.revisions().forEach((instanceId, batchRevision) -> {
                Long latestRevision = recordRevisions.get(instanceId);
                if (latestRevision != null && latestRevision.equals(batchRevision)) {
                    pendingWrites.remove(instanceId);
                }
            });
        }
    }

    private void reject(PendingBatch batch) {
        if (!batch.fullSnapshot()) {
            return;
        }
        synchronized (pendingLock) {
            // A failed full write must be retried in full. Every current value
            // remains in records, so rebuilding the snapshot loses no state.
            fullSnapshotInFlight = false;
            fullSnapshotPending = true;
            pendingWrites.clear();
        }
    }

    private void resetPendingState() {
        revision.set(0L);
        saving.set(false);
        pressureFlushQueued.set(false);
        committedBatches.set(0L);
        committedEntries.set(0L);
        failedWriteBatches.set(0L);
        pressureFlushCount.set(0L);
        pendingHighWaterMark = 0;
        recordRevisions.clear();
        synchronized (pendingLock) {
            pendingWrites.clear();
            pendingPlacedBlockWrites.clear();
            fullSnapshotPending = false;
            fullSnapshotInFlight = false;
        }
    }

    private static long saturatingAdd(long first, long second) {
        return Long.MAX_VALUE - first < second ? Long.MAX_VALUE : first + second;
    }

    public record PersistenceDiagnostics(
            int pendingWrites,
            int pendingPlacedBlockWrites,
            int queueHighWaterMark,
            long dirtyMutations,
            long committedBatches,
            long committedEntries,
            long pressureFlushes,
            long failedWriteBatches,
            boolean flushRunning,
            boolean pressureFlushQueued,
            boolean fullSnapshotPending,
            boolean fullSnapshotInFlight
    ) {
        public double averageBatchSize() {
            return committedBatches == 0L
                    ? 0.0D : (double) committedEntries / (double) committedBatches;
        }
    }

    private record PendingBatch(
            List<InstanceRecord> records,
            Map<UUID, Long> revisions,
            boolean fullSnapshot
    ) {
    }

    private record PendingPlacedWrite(boolean placed, long revision) {}

    private record PlacedBlockBatch(
            Map<PlacedBlockPosition, Boolean> changes,
            Map<PlacedBlockPosition, Long> revisions
    ) {}

    /**
     * Mutable only inside InstanceRegistry. Public callers receive detached
     * snapshots from find/findCached/findOwned/findActive.
     */
    public static final class InstanceRecord {
        private final UUID instanceId;
        private volatile String toolId;
        private volatile String categoryId;
        private volatile UUID ownerId;
        private volatile String ownerName;
        private volatile String boundWorld;
        private volatile int level;
        private volatile long progress;
        private volatile Map<String, Long> targetProgress;
        private volatile boolean active;
        private volatile boolean menuManaged;
        private volatile long lifetime;
        private final long createdAt;
        private volatile long updatedAt;

        public InstanceRecord(
                UUID instanceId,
                String toolId,
                String categoryId,
                UUID ownerId,
                String ownerName,
                String boundWorld,
                int level,
                long progress,
                Map<String, Long> targetProgress,
                boolean active,
                boolean menuManaged,
                long lifetime,
                long createdAt,
                long updatedAt
        ) {
            if (instanceId == null || ownerId == null) {
                throw new IllegalArgumentException("Instance and owner UUIDs are required.");
            }
            if (toolId == null || toolId.isBlank() || ownerName == null || ownerName.isBlank()
                    || boundWorld == null || boundWorld.isBlank()) {
                throw new IllegalArgumentException(
                        "Tool ID, owner name, and bound world are required.");
            }
            if (level < 1 || progress < 0L || lifetime < 0L
                    || createdAt < 0L || updatedAt < 0L) {
                throw new IllegalArgumentException("Persistent numeric values cannot be negative.");
            }
            this.instanceId = instanceId;
            this.toolId = toolId;
            this.categoryId = categoryId == null ? "" : categoryId;
            this.ownerId = ownerId;
            this.ownerName = ownerName;
            this.boundWorld = boundWorld;
            this.level = level;
            this.progress = progress;
            this.targetProgress = normalizeTargets(targetProgress);
            this.active = active;
            this.menuManaged = menuManaged;
            this.lifetime = lifetime;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        private synchronized void updateFrom(
                ToolState state,
                long progressAdded,
                String updatedOwnerName,
                long now
        ) {
            toolId = state.toolId();
            categoryId = state.categoryId();
            ownerId = state.ownerId();
            ownerName = updatedOwnerName;
            boundWorld = state.boundWorld();
            level = state.level();
            progress = state.progress();
            targetProgress = state.targetProgress().isEmpty()
                    ? Map.of() : state.targetProgress();
            lifetime = saturatingAdd(lifetime, Math.max(0L, progressAdded));
            updatedAt = now;
        }

        private synchronized boolean setActive(boolean newActive, long now) {
            if (active == newActive) {
                return false;
            }
            active = newActive;
            updatedAt = now;
            return true;
        }

        private synchronized boolean setMenuManaged(boolean newMenuManaged, long now) {
            if (menuManaged == newMenuManaged) {
                return false;
            }
            menuManaged = newMenuManaged;
            updatedAt = now;
            return true;
        }

        public synchronized ToolState state() {
            return new ToolState(toolId, instanceId, level, progress, boundWorld, ownerId,
                    categoryId, targetProgress);
        }

        private synchronized InstanceRecord snapshot() {
            return new InstanceRecord(
                    instanceId, toolId, categoryId, ownerId, ownerName, boundWorld,
                    level, progress, targetProgress, active, menuManaged, lifetime,
                    createdAt, updatedAt);
        }

        public UUID instanceId() {
            return instanceId;
        }

        public String toolId() {
            return toolId;
        }

        public String categoryId() {
            return categoryId;
        }

        public UUID ownerId() {
            return ownerId;
        }

        public String ownerName() {
            return ownerName;
        }

        public String boundWorld() {
            return boundWorld;
        }

        public int level() {
            return level;
        }

        public long progress() {
            return progress;
        }

        public Map<String, Long> targetProgress() {
            return targetProgress;
        }

        public boolean active() {
            return active;
        }

        public boolean menuManaged() {
            return menuManaged;
        }

        public long lifetime() {
            return lifetime;
        }

        public long createdAt() {
            return createdAt;
        }

        public long updatedAt() {
            return updatedAt;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof InstanceRecord record)) {
                return false;
            }
            return level == record.level()
                    && progress == record.progress()
                    && active == record.active()
                    && menuManaged == record.menuManaged()
                    && lifetime == record.lifetime()
                    && createdAt == record.createdAt()
                    && updatedAt == record.updatedAt()
                    && instanceId.equals(record.instanceId())
                    && toolId.equals(record.toolId())
                    && categoryId.equals(record.categoryId())
                    && ownerId.equals(record.ownerId())
                    && ownerName.equals(record.ownerName())
                    && boundWorld.equals(record.boundWorld())
                    && targetProgress.equals(record.targetProgress());
        }

        @Override
        public int hashCode() {
            return Objects.hash(instanceId, toolId, categoryId, ownerId, ownerName, boundWorld,
                    level, progress, targetProgress, active, menuManaged, lifetime,
                    createdAt, updatedAt);
        }

        @Override
        public String toString() {
            return "InstanceRecord[instanceId=" + instanceId
                    + ", toolId=" + toolId
                    + ", categoryId=" + categoryId
                    + ", ownerId=" + ownerId
                    + ", ownerName=" + ownerName
                    + ", boundWorld=" + boundWorld
                    + ", level=" + level
                    + ", progress=" + progress
                    + ", targetProgress=" + targetProgress
                    + ", active=" + active
                    + ", menuManaged=" + menuManaged
                    + ", lifetime=" + lifetime
                    + ", createdAt=" + createdAt
                    + ", updatedAt=" + updatedAt + ']';
        }

        private static Map<String, Long> normalizeTargets(Map<String, Long> targets) {
            Objects.requireNonNull(targets, "Target progress is required.");
            if (targets.isEmpty()) {
                return Map.of();
            }
            Map<String, Long> normalizedTargets = new LinkedHashMap<>();
            targets.forEach((rawTarget, amount) -> {
                String target = LevelRequirement.normalize(rawTarget);
                if (target.isBlank() || amount == null || amount < 1L) {
                    throw new IllegalArgumentException(
                            "Target progress requires a nonblank target and positive amount.");
                }
                if (normalizedTargets.putIfAbsent(target, amount) != null) {
                    throw new IllegalArgumentException(
                            "Duplicate normalized target progress: " + target);
                }
            });
            return Map.copyOf(normalizedTargets);
        }
    }
}
