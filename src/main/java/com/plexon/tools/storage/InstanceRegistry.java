package com.plexon.tools.storage;

import com.plexon.tools.item.ToolState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

public final class InstanceRegistry {
    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, InstanceRecord> records = new LinkedHashMap<>();
    private boolean dirty;

    public InstanceRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() throws IOException, InvalidConfigurationException {
        records.clear();
        if (!file.exists()) {
            dirty = false;
            return;
        }

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file);
        ConfigurationSection instances = yaml.getConfigurationSection("instances");
        if (instances == null) {
            dirty = false;
            return;
        }

        for (String rawUuid : instances.getKeys(false)) {
            try {
                UUID instanceId = UUID.fromString(rawUuid);
                String root = "instances." + rawUuid;
                UUID ownerId = UUID.fromString(require(yaml.getString(root + ".owner"), "owner"));
                InstanceRecord record = new InstanceRecord(
                        instanceId,
                        require(yaml.getString(root + ".tool"), "tool"),
                        ownerId,
                        yaml.getString(root + ".owner_name", ownerId.toString()),
                        require(yaml.getString(root + ".bound_world"), "bound_world"),
                        Math.max(1, yaml.getInt(root + ".level", 1)),
                        Math.max(0L, yaml.getLong(root + ".progress", 0L)),
                        Math.max(0L, yaml.getLong(root + ".lifetime", 0L)),
                        yaml.getLong(root + ".created_at", Instant.now().toEpochMilli()),
                        yaml.getLong(root + ".updated_at", Instant.now().toEpochMilli())
                );
                records.put(instanceId, record);
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING,
                        "Skipping invalid tool registry entry '" + rawUuid + "': " + exception.getMessage());
            }
        }
        dirty = false;
    }

    public void register(ToolState state, String ownerName) {
        long now = Instant.now().toEpochMilli();
        records.putIfAbsent(state.instanceId(), new InstanceRecord(
                state.instanceId(), state.toolId(), state.ownerId(), ownerName, state.boundWorld(),
                state.level(), state.progress(), 0L, now, now));
        dirty = true;
    }

    public void update(ToolState state, long progressAdded, String ownerName) {
        long now = Instant.now().toEpochMilli();
        InstanceRecord existing = records.get(state.instanceId());
        if (existing == null) {
            register(state, ownerName);
            existing = records.get(state.instanceId());
        }
        long lifetime = saturatingAdd(existing.lifetime(), Math.max(0L, progressAdded));
        records.put(state.instanceId(), new InstanceRecord(
                state.instanceId(), state.toolId(), state.ownerId(), ownerName, state.boundWorld(),
                state.level(), state.progress(), lifetime, existing.createdAt(), now));
        dirty = true;
    }

    public Optional<InstanceRecord> find(UUID instanceId) {
        return Optional.ofNullable(records.get(instanceId));
    }

    public int size() {
        return records.size();
    }

    public void saveIfDirty() {
        if (!dirty) {
            return;
        }
        try {
            save();
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save data.yml", exception);
        }
    }

    public void save() throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        for (InstanceRecord record : records.values()) {
            String root = "instances." + record.instanceId();
            yaml.set(root + ".tool", record.toolId());
            yaml.set(root + ".owner", record.ownerId().toString());
            yaml.set(root + ".owner_name", record.ownerName());
            yaml.set(root + ".bound_world", record.boundWorld());
            yaml.set(root + ".level", record.level());
            yaml.set(root + ".progress", record.progress());
            yaml.set(root + ".lifetime", record.lifetime());
            yaml.set(root + ".created_at", record.createdAt());
            yaml.set(root + ".updated_at", record.updatedAt());
        }
        yaml.save(file);
        dirty = false;
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
            UUID ownerId,
            String ownerName,
            String boundWorld,
            int level,
            long progress,
            long lifetime,
            long createdAt,
            long updatedAt
    ) {
    }
}
