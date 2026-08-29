package com.plexon.tools.storage;

import com.plexon.tools.item.ToolState;
import com.plexon.tools.model.LevelRequirement;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
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
    private final JavaPlugin plugin;
    private final File file;
    private final ConcurrentMap<UUID, InstanceRecord> records = new ConcurrentHashMap<>();
    private final AtomicLong revision = new AtomicLong();
    private final AtomicLong persistedRevision = new AtomicLong();
    private final AtomicBoolean saving = new AtomicBoolean();
    private final Object saveLock = new Object();

    public InstanceRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() throws IOException, InvalidConfigurationException {
        records.clear();
        if (!file.exists()) {
            revision.set(0L);
            persistedRevision.set(0L);
            return;
        }

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file);
        ConfigurationSection instances = yaml.getConfigurationSection("instances");
        if (instances != null) {
            for (String rawUuid : instances.getKeys(false)) {
                try {
                    UUID instanceId = UUID.fromString(rawUuid);
                    String root = "instances." + rawUuid;
                    UUID ownerId = UUID.fromString(require(yaml.getString(root + ".owner"), "owner"));
                    Map<String, Long> targetProgress = readTargetProgress(
                            yaml.getConfigurationSection(root + ".target_progress"));
                    InstanceRecord record = new InstanceRecord(
                            instanceId,
                            require(yaml.getString(root + ".tool"), "tool"),
                            yaml.getString(root + ".category", ""),
                            ownerId,
                            yaml.getString(root + ".owner_name", ownerId.toString()),
                            require(yaml.getString(root + ".bound_world"), "bound_world"),
                            Math.max(1, yaml.getInt(root + ".level", 1)),
                            Math.max(0L, yaml.getLong(root + ".progress", 0L)),
                            targetProgress,
                            Math.max(0L, yaml.getLong(root + ".lifetime", 0L)),
                            yaml.getLong(root + ".created_at", Instant.now().toEpochMilli()),
                            yaml.getLong(root + ".updated_at", Instant.now().toEpochMilli())
                    );
                    records.put(instanceId, record);
                } catch (RuntimeException exception) {
                    plugin.getLogger().log(Level.WARNING,
                            "Skipping invalid tool registry entry '" + rawUuid + "': "
                                    + exception.getMessage());
                }
            }
        }
        revision.set(0L);
        persistedRevision.set(0L);
    }

    public void register(ToolState state, String ownerName) {
        long now = Instant.now().toEpochMilli();
        records.putIfAbsent(state.instanceId(), new InstanceRecord(
                state.instanceId(), state.toolId(), state.categoryId(), state.ownerId(), ownerName,
                state.boundWorld(), state.level(), state.progress(), state.targetProgress(),
                0L, now, now));
        revision.incrementAndGet();
    }

    public void update(ToolState state, long progressAdded, String ownerName) {
        long now = Instant.now().toEpochMilli();
        records.compute(state.instanceId(), (instanceId, existing) -> {
            long createdAt = existing == null ? now : existing.createdAt();
            long lifetime = existing == null ? 0L : existing.lifetime();
            lifetime = saturatingAdd(lifetime, Math.max(0L, progressAdded));
            return new InstanceRecord(
                    state.instanceId(), state.toolId(), state.categoryId(), state.ownerId(), ownerName,
                    state.boundWorld(), state.level(), state.progress(), state.targetProgress(),
                    lifetime, createdAt, now);
        });
        revision.incrementAndGet();
    }

    public Optional<InstanceRecord> find(UUID instanceId) {
        return Optional.ofNullable(records.get(instanceId));
    }

    public int size() {
        return records.size();
    }

    /**
     * Flushes a stable registry snapshot. Call from an asynchronous Bukkit task,
     * never from a gameplay event handler.
     */
    public void flushAsync() {
        if (revision.get() <= persistedRevision.get() || !saving.compareAndSet(false, true)) {
            return;
        }
        try {
            flushSnapshot();
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save data.yml asynchronously", exception);
        } finally {
            saving.set(false);
        }
    }

    /**
     * Final shutdown checkpoint after gameplay handlers have stopped.
     */
    public void flushBlocking() {
        if (revision.get() <= persistedRevision.get()) {
            return;
        }
        try {
            flushSnapshot();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save data.yml", exception);
        }
    }

    private void flushSnapshot() throws IOException {
        synchronized (saveLock) {
            long snapshotRevision = revision.get();
            if (snapshotRevision <= persistedRevision.get()) {
                return;
            }
            List<InstanceRecord> snapshot = new ArrayList<>(records.values());
            snapshot.sort(java.util.Comparator.comparing(record -> record.instanceId().toString()));
            writeSnapshot(snapshot);
            persistedRevision.accumulateAndGet(snapshotRevision, Math::max);
        }
    }

    private void writeSnapshot(List<InstanceRecord> snapshot) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema_version", 3);
        for (InstanceRecord record : snapshot) {
            String root = "instances." + record.instanceId();
            yaml.set(root + ".tool", record.toolId());
            yaml.set(root + ".category", record.categoryId());
            yaml.set(root + ".owner", record.ownerId().toString());
            yaml.set(root + ".owner_name", record.ownerName());
            yaml.set(root + ".bound_world", record.boundWorld());
            yaml.set(root + ".level", record.level());
            yaml.set(root + ".progress", record.progress());
            yaml.set(root + ".target_progress",
                    record.targetProgress().isEmpty() ? null : record.targetProgress());
            yaml.set(root + ".lifetime", record.lifetime());
            yaml.set(root + ".created_at", record.createdAt());
            yaml.set(root + ".updated_at", record.updatedAt());
        }

        java.nio.file.Path destination = file.toPath();
        java.nio.file.Path temporary = destination.resolveSibling(file.getName() + ".tmp");
        Files.writeString(temporary, yaml.saveToString(), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, destination,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Map<String, Long> readTargetProgress(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, Long> progress = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            long value = section.getLong(key, 0L);
            if (value > 0L) {
                progress.put(LevelRequirement.normalize(key), value);
            }
        }
        return progress;
    }

    private static long saturatingAdd(long first, long second) {
        return Long.MAX_VALUE - first < second ? Long.MAX_VALUE : first + second;
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing " + field);
        }
        return value;
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
            long lifetime,
            long createdAt,
            long updatedAt
    ) {
        public InstanceRecord {
            categoryId = categoryId == null ? "" : categoryId;
            targetProgress = Map.copyOf(targetProgress);
        }
    }
}
