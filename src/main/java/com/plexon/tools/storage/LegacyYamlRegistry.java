package com.plexon.tools.storage;

import com.plexon.tools.model.LevelRequirement;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class LegacyYamlRegistry {
    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter
            .ofPattern("uuuuMMdd-HHmmss-SSS'Z'")
            .withZone(ZoneOffset.UTC);

    private LegacyYamlRegistry() {
    }

    static Snapshot read(Path file) throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file.toFile());
        long rawSchemaVersion;
        try {
            rawSchemaVersion = integral(yaml.get("schema_version"),
                    "schema_version", 0L);
        } catch (IllegalArgumentException exception) {
            throw invalid("Invalid data.yml: " + exception.getMessage(), exception);
        }
        if (rawSchemaVersion > Integer.MAX_VALUE) {
            throw invalid("data.yml schema_version is too large.");
        }
        int schemaVersion = (int) rawSchemaVersion;
        if (schemaVersion < 3 || schemaVersion > 4) {
            throw invalid("data.yml must use registry schema 3 or 4 before SQLite migration.");
        }
        if (yaml.contains("instances") && !yaml.isConfigurationSection("instances")) {
            throw invalid("data.yml instances must be a YAML section.");
        }

        ConfigurationSection instances = yaml.getConfigurationSection("instances");
        List<InstanceRegistry.InstanceRecord> records = new ArrayList<>();
        if (instances != null) {
            for (String rawUuid : instances.getKeys(false)) {
                String root = "instances." + rawUuid;
                try {
                    if (!yaml.isConfigurationSection(root)) {
                        throw new IllegalArgumentException("entry must be a YAML section");
                    }
                    UUID instanceId = UUID.fromString(rawUuid);
                    UUID ownerId = UUID.fromString(requireString(yaml, root + ".owner"));
                    long rawLevel = integral(yaml.get(root + ".level"), root + ".level", 1L);
                    if (rawLevel > Integer.MAX_VALUE) {
                        throw new IllegalArgumentException("level is too large");
                    }
                    int level = (int) rawLevel;
                    long progress = integral(yaml.get(root + ".progress"),
                            root + ".progress", 0L);
                    long lifetime = integral(yaml.get(root + ".lifetime"),
                            root + ".lifetime", 0L);
                    long now = Instant.now().toEpochMilli();
                    long createdAt = integral(yaml.get(root + ".created_at"),
                            root + ".created_at", now);
                    long updatedAt = integral(yaml.get(root + ".updated_at"),
                            root + ".updated_at", createdAt);
                    if (level < 1 || progress < 0L || lifetime < 0L
                            || createdAt < 0L || updatedAt < 0L) {
                        throw new IllegalArgumentException("numeric values cannot be negative");
                    }
                    String targetPath = root + ".target_progress";
                    if (yaml.contains(targetPath) && !yaml.isConfigurationSection(targetPath)) {
                        throw new IllegalArgumentException(
                                "target_progress must be a YAML section");
                    }
                    records.add(new InstanceRegistry.InstanceRecord(
                            instanceId,
                            requireString(yaml, root + ".tool"),
                            optionalString(yaml, root + ".category", ""),
                            ownerId,
                            optionalString(yaml, root + ".owner_name", ownerId.toString()),
                            requireString(yaml, root + ".bound_world"),
                            level,
                            progress,
                            readTargetProgress(yaml.getConfigurationSection(targetPath)),
                            optionalBoolean(yaml, root + ".active", true),
                            optionalBoolean(yaml, root + ".menu_managed", false),
                            lifetime,
                            createdAt,
                            updatedAt
                    ));
                } catch (RuntimeException exception) {
                    throw invalid("Invalid tool registry entry '" + rawUuid + "': "
                            + exception.getMessage(), exception);
                }
            }
        }
        return new Snapshot(schemaVersion, List.copyOf(records), sha256(file));
    }

    static Path createBackup(Path source) throws IOException {
        String suffix = ".pre-sqlite-" + BACKUP_TIME.format(Instant.now()) + ".bak";
        Path backup = source.resolveSibling(source.getFileName() + suffix);
        int collision = 1;
        while (Files.exists(backup)) {
            backup = source.resolveSibling(source.getFileName() + suffix + "." + collision++);
        }
        return Files.copy(source, backup,
                StandardCopyOption.COPY_ATTRIBUTES);
    }

    private static Map<String, Long> readTargetProgress(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, Long> progress = new LinkedHashMap<>();
        for (String rawTarget : section.getKeys(false)) {
            String target = LevelRequirement.normalize(rawTarget);
            long value = integral(section.get(rawTarget),
                    section.getCurrentPath() + "." + rawTarget, -1L);
            if (target.isBlank() || value < 1L) {
                throw new IllegalArgumentException(
                        "target progress values must use a nonblank target and positive amount");
            }
            if (progress.putIfAbsent(target, value) != null) {
                throw new IllegalArgumentException("duplicate normalized target " + target);
            }
        }
        return Map.copyOf(progress);
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Java does not provide SHA-256.", exception);
        }
    }

    private static long integral(Object value, String path, long fallback) {
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long)) {
            throw new IllegalArgumentException(path + " must be a whole number");
        }
        return ((Number) value).longValue();
    }

    private static String requireString(ConfigurationSection config, String path) {
        String value = optionalString(config, path, null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing " + path);
        }
        return value;
    }

    private static String optionalString(
            ConfigurationSection config,
            String path,
            String fallback
    ) {
        Object value = config.get(path);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof String string)) {
            throw new IllegalArgumentException(path + " must be text");
        }
        return string;
    }

    private static boolean optionalBoolean(
            ConfigurationSection config,
            String path,
            boolean fallback
    ) {
        Object value = config.get(path);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Boolean bool)) {
            throw new IllegalArgumentException(path + " must be true or false");
        }
        return bool;
    }

    private static InvalidConfigurationException invalid(String message) {
        return new InvalidConfigurationException(message);
    }

    private static InvalidConfigurationException invalid(String message, Throwable cause) {
        InvalidConfigurationException exception = new InvalidConfigurationException(message);
        exception.initCause(cause);
        return exception;
    }

    record Snapshot(
            int schemaVersion,
            List<InstanceRegistry.InstanceRecord> records,
            String sha256
    ) {
    }
}
