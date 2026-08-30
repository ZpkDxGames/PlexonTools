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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

public final class InstanceRegistry {
    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter
            .ofPattern("uuuuMMdd-HHmmss-SSS'Z'")
            .withZone(ZoneOffset.UTC);
    private static final int MAX_BATCHES_PER_ASYNC_FLUSH = 8;

    private final JavaPlugin plugin;
    private final PluginSettings settings;
    private final Path legacyFile;
    private final RegistryDatabase database;
    private final ConcurrentMap<UUID, InstanceRecord> records = new ConcurrentHashMap<>();
    private final AtomicLong revision = new AtomicLong();
    private final AtomicBoolean saving = new AtomicBoolean();
    private final Object pendingLock = new Object();
    private final Object databaseLock = new Object();
    private final LinkedHashMap<UUID, Long> pendingWrites = new LinkedHashMap<>();
    private boolean fullSnapshotPending;

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
        long now = Instant.now().toEpochMilli();
        InstanceRecord created = new InstanceRecord(
                state.instanceId(), state.toolId(), state.categoryId(), state.ownerId(), ownerName,
                state.boundWorld(), state.level(), state.progress(), state.targetProgress(),
                active, menuManaged, 0L, now, now);
        InstanceRecord previous = records.putIfAbsent(state.instanceId(), created);
        if (previous == null) {
            markDirty(state.instanceId());
        }
    }

    public void update(ToolState state, long progressAdded, String ownerName) {
        long now = Instant.now().toEpochMilli();
        records.compute(state.instanceId(), (instanceId, existing) -> {
            long createdAt = existing == null ? now : existing.createdAt();
            long lifetime = existing == null ? 0L : existing.lifetime();
            boolean active = existing == null || existing.active();
            boolean menuManaged = existing != null && existing.menuManaged();
            lifetime = saturatingAdd(lifetime, Math.max(0L, progressAdded));
            return new InstanceRecord(
                    state.instanceId(), state.toolId(), state.categoryId(), state.ownerId(), ownerName,
                    state.boundWorld(), state.level(), state.progress(), state.targetProgress(),
                    active, menuManaged, lifetime, createdAt, now);
        });
        markDirty(state.instanceId());
    }

    public Optional<InstanceRecord> find(UUID instanceId) {
        return Optional.ofNullable(records.get(instanceId));
    }

    public List<InstanceRecord> findOwned(UUID ownerId, String toolId, String boundWorld) {
        return findOwned(ownerId, toolId).stream()
                .filter(record -> record.boundWorld().equalsIgnoreCase(boundWorld))
                .toList();
    }

    public List<InstanceRecord> findOwned(UUID ownerId, String toolId) {
        return records.values().stream()
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
                .filter(InstanceRecord::active)
                .filter(record -> record.ownerId().equals(ownerId))
                .sorted(Comparator
                        .comparing(InstanceRecord::toolId, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Comparator.comparingLong(
                                InstanceRecord::updatedAt).reversed()))
                .toList();
    }

    public void setActive(UUID instanceId, boolean active) {
        AtomicBoolean changed = new AtomicBoolean();
        records.computeIfPresent(instanceId, (ignored, record) -> {
            if (record.active() == active) {
                return record;
            }
            changed.set(true);
            return new InstanceRecord(
                    record.instanceId(), record.toolId(), record.categoryId(), record.ownerId(),
                    record.ownerName(), record.boundWorld(), record.level(), record.progress(),
                    record.targetProgress(), active, record.menuManaged(), record.lifetime(),
                    record.createdAt(), Instant.now().toEpochMilli());
        });
        if (changed.get()) {
            markDirty(instanceId);
        }
    }

    public void setMenuManaged(UUID instanceId, boolean menuManaged) {
        AtomicBoolean changed = new AtomicBoolean();
        records.computeIfPresent(instanceId, (ignored, record) -> {
            if (record.menuManaged() == menuManaged) {
                return record;
            }
            changed.set(true);
            return new InstanceRecord(
                    record.instanceId(), record.toolId(), record.categoryId(), record.ownerId(),
                    record.ownerName(), record.boundWorld(), record.level(), record.progress(),
                    record.targetProgress(), record.active(), menuManaged, record.lifetime(),
                    record.createdAt(), Instant.now().toEpochMilli());
        });
        if (changed.get()) {
            markDirty(instanceId);
        }
    }

    public int size() {
        return records.size();
    }

    public Path databaseFile() {
        return database.file();
    }

    public int pendingWriteCount() {
        synchronized (pendingLock) {
            return fullSnapshotPending ? records.size() : pendingWrites.size();
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
                for (int batch = 0; batch < MAX_BATCHES_PER_ASYNC_FLUSH; batch++) {
                    PendingBatch pending = nextBatch();
                    if (pending == null) {
                        break;
                    }
                    database.upsert(pending.records());
                    acknowledge(pending);
                }
            }
        } catch (SQLException exception) {
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
                PendingBatch pending;
                while ((pending = nextBatch()) != null) {
                    database.upsert(pending.records());
                    acknowledge(pending);
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
            PendingBatch pending;
            while ((pending = nextBatch()) != null) {
                database.upsert(pending.records());
                acknowledge(pending);
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

    private void markDirty(UUID instanceId) {
        long currentRevision = revision.incrementAndGet();
        synchronized (pendingLock) {
            if (fullSnapshotPending) {
                return;
            }
            if (!pendingWrites.containsKey(instanceId)
                    && pendingWrites.size() >= settings.databaseMaxPendingWrites()) {
                pendingWrites.clear();
                fullSnapshotPending = true;
                return;
            }
            pendingWrites.put(instanceId, currentRevision);
        }
    }

    private PendingBatch nextBatch() {
        long fullSnapshotRevision = -1L;
        synchronized (pendingLock) {
            if (fullSnapshotPending) {
                fullSnapshotRevision = revision.get();
            } else {
                if (pendingWrites.isEmpty()) {
                    return null;
                }
                Map<UUID, Long> revisions = new LinkedHashMap<>();
                List<InstanceRecord> batch = new ArrayList<>();
                for (Map.Entry<UUID, Long> entry : pendingWrites.entrySet()) {
                    InstanceRecord record = records.get(entry.getKey());
                    if (record != null) {
                        revisions.put(entry.getKey(), entry.getValue());
                        batch.add(record);
                    }
                    if (revisions.size() >= settings.databaseWriteBatchSize()) {
                        break;
                    }
                }
                if (revisions.isEmpty()) {
                    pendingWrites.clear();
                    return null;
                }
                return new PendingBatch(List.copyOf(batch), Map.copyOf(revisions),
                        false, 0L);
            }
        }
        // A full snapshot can be large. Copy it on the asynchronous caller
        // without holding the lock used by gameplay-thread markDirty calls.
        List<InstanceRecord> snapshot = records.values().stream()
                .sorted(Comparator.comparing(record -> record.instanceId().toString()))
                .toList();
        return new PendingBatch(snapshot, Map.of(), true, fullSnapshotRevision);
    }

    private void acknowledge(PendingBatch batch) {
        synchronized (pendingLock) {
            if (batch.fullSnapshot()) {
                if (revision.get() == batch.snapshotRevision()) {
                    fullSnapshotPending = false;
                    pendingWrites.clear();
                }
                return;
            }
            batch.revisions().forEach((instanceId, batchRevision) -> {
                Long current = pendingWrites.get(instanceId);
                if (current != null && current.equals(batchRevision)) {
                    pendingWrites.remove(instanceId);
                }
            });
        }
    }

    private void resetPendingState() {
        revision.set(0L);
        saving.set(false);
        synchronized (pendingLock) {
            pendingWrites.clear();
            fullSnapshotPending = false;
        }
    }

    private static long saturatingAdd(long first, long second) {
        return Long.MAX_VALUE - first < second ? Long.MAX_VALUE : first + second;
    }

    private record PendingBatch(
            List<InstanceRecord> records,
            Map<UUID, Long> revisions,
            boolean fullSnapshot,
            long snapshotRevision
    ) {
    }

    public record InstanceRecord(
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
        public InstanceRecord {
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
            categoryId = categoryId == null ? "" : categoryId;
            Map<String, Long> normalizedTargets = new LinkedHashMap<>();
            targetProgress.forEach((rawTarget, amount) -> {
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
            targetProgress = Map.copyOf(normalizedTargets);
        }

        public ToolState state() {
            return new ToolState(toolId, instanceId, level, progress, boundWorld, ownerId,
                    categoryId, targetProgress);
        }
    }
}
