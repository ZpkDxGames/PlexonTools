package com.plexon.tools.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

final class RegistryDatabase implements AutoCloseable {
    static final int SCHEMA_VERSION = 1;

    private static final String UPSERT_PLAYER = """
            INSERT INTO players(player_uuid, cached_name, first_seen_at, updated_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(player_uuid) DO UPDATE SET
                cached_name = CASE
                    WHEN excluded.updated_at >= players.updated_at THEN excluded.cached_name
                    ELSE players.cached_name
                END,
                first_seen_at = MIN(players.first_seen_at, excluded.first_seen_at),
                updated_at = MAX(players.updated_at, excluded.updated_at)
            """;
    private static final String UPSERT_INSTANCE = """
            INSERT INTO tool_instances(
                instance_uuid, tool_id, category_id, owner_uuid, bound_world,
                level, progress, active, menu_managed, lifetime, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(instance_uuid) DO UPDATE SET
                tool_id = excluded.tool_id,
                category_id = excluded.category_id,
                owner_uuid = excluded.owner_uuid,
                bound_world = excluded.bound_world,
                level = excluded.level,
                progress = excluded.progress,
                active = excluded.active,
                menu_managed = excluded.menu_managed,
                lifetime = excluded.lifetime,
                created_at = excluded.created_at,
                updated_at = excluded.updated_at
            """;
    private static final String DELETE_TARGETS =
            "DELETE FROM target_progress WHERE instance_uuid = ?";
    private static final String INSERT_TARGET = """
            INSERT INTO target_progress(instance_uuid, target, progress)
            VALUES (?, ?, ?)
            """;

    private final Path file;
    private final int busyTimeoutMillis;
    private final int walAutoCheckpointPages;
    private final boolean integrityCheck;
    private Connection connection;
    private String journalMode = "unknown";

    RegistryDatabase(
            Path file,
            int busyTimeoutMillis,
            int walAutoCheckpointPages,
            boolean integrityCheck
    ) {
        this.file = file.toAbsolutePath().normalize();
        this.busyTimeoutMillis = busyTimeoutMillis;
        this.walAutoCheckpointPages = walAutoCheckpointPages;
        this.integrityCheck = integrityCheck;
    }

    synchronized void open() throws SQLException, IOException {
        if (connection != null && !connection.isClosed()) {
            return;
        }
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException exception) {
            throw new SQLException("The bundled SQLite JDBC driver could not be loaded.", exception);
        }

        connection = DriverManager.getConnection("jdbc:sqlite:" + file);
        try {
            configureConnection();
            initializeSchema();
            if (integrityCheck) {
                verifyIntegrity();
            }
        } catch (SQLException | RuntimeException exception) {
            try {
                connection.close();
            } catch (SQLException closeException) {
                exception.addSuppressed(closeException);
            }
            connection = null;
            throw exception;
        }
    }

    synchronized Map<UUID, InstanceRegistry.InstanceRecord> loadAll() throws SQLException {
        requireOpen();
        Map<UUID, Map<String, Long>> targets = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT instance_uuid, target, progress
                FROM target_progress
                ORDER BY instance_uuid, target
                """);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                UUID instanceId = parseUuid(result.getString("instance_uuid"), "target instance UUID");
                String target = requireText(result.getString("target"), "target");
                long progress = result.getLong("progress");
                if (progress < 1L) {
                    throw new SQLException("Invalid target progress for " + instanceId + ": " + progress);
                }
                targets.computeIfAbsent(instanceId, ignored -> new LinkedHashMap<>())
                        .put(target, progress);
            }
        }

        Map<UUID, InstanceRegistry.InstanceRecord> loaded = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT i.instance_uuid, i.tool_id, i.category_id, i.owner_uuid,
                       p.cached_name, i.bound_world, i.level, i.progress, i.active,
                       i.menu_managed, i.lifetime, i.created_at, i.updated_at
                FROM tool_instances i
                LEFT JOIN players p ON p.player_uuid = i.owner_uuid
                ORDER BY i.instance_uuid
                """);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                UUID instanceId = parseUuid(result.getString("instance_uuid"), "instance UUID");
                UUID ownerId = parseUuid(result.getString("owner_uuid"), "owner UUID");
                int level = result.getInt("level");
                long progress = result.getLong("progress");
                long lifetime = result.getLong("lifetime");
                if (level < 1 || progress < 0L || lifetime < 0L) {
                    throw new SQLException("Invalid numeric state for tool instance " + instanceId);
                }
                InstanceRegistry.InstanceRecord record = new InstanceRegistry.InstanceRecord(
                        instanceId,
                        requireText(result.getString("tool_id"), "tool ID"),
                        result.getString("category_id"),
                        ownerId,
                        requireText(result.getString("cached_name"), "cached owner name"),
                        requireText(result.getString("bound_world"), "bound world"),
                        level,
                        progress,
                        targets.getOrDefault(instanceId, Map.of()),
                        result.getInt("active") == 1,
                        result.getInt("menu_managed") == 1,
                        lifetime,
                        result.getLong("created_at"),
                        result.getLong("updated_at")
                );
                if (loaded.put(instanceId, record) != null) {
                    throw new SQLException("Duplicate tool instance UUID in database: " + instanceId);
                }
            }
        }

        for (UUID instanceId : targets.keySet()) {
            if (!loaded.containsKey(instanceId)) {
                throw new SQLException("Orphaned target progress for tool instance " + instanceId);
            }
        }
        return Map.copyOf(loaded);
    }

    synchronized void upsert(Collection<InstanceRegistry.InstanceRecord> records)
            throws SQLException {
        requireOpen();
        if (records.isEmpty()) {
            return;
        }
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            writeRecords(records);
            putMetadata("last_flush_at", Long.toString(Instant.now().toEpochMilli()));
            connection.commit();
        } catch (SQLException | RuntimeException exception) {
            rollback(exception);
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    synchronized boolean importLegacy(
            Collection<InstanceRegistry.InstanceRecord> records,
            int sourceSchemaVersion,
            String sourceChecksum,
            String backupFileName
    ) throws SQLException {
        requireOpen();
        if (migrationComplete()) {
            return false;
        }
        if (recordCount() != 0L) {
            throw new SQLException("Refusing legacy YAML import because the database already contains instances.");
        }

        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            writeRecords(records);
            Map<UUID, InstanceRegistry.InstanceRecord> expected = new LinkedHashMap<>();
            for (InstanceRegistry.InstanceRecord record : records) {
                if (expected.put(record.instanceId(), record) != null) {
                    throw new SQLException("Duplicate legacy instance UUID: " + record.instanceId());
                }
            }
            Map<UUID, InstanceRegistry.InstanceRecord> actual = loadAll();
            if (actual.size() != expected.size()) {
                throw new SQLException("Legacy data verification failed; imported "
                        + actual.size() + " of " + expected.size() + " records.");
            }
            for (Map.Entry<UUID, InstanceRegistry.InstanceRecord> entry : expected.entrySet()) {
                InstanceRegistry.InstanceRecord imported = actual.get(entry.getKey());
                if (!samePersistentState(entry.getValue(), imported)) {
                    throw new SQLException("Legacy data verification failed for instance "
                            + entry.getKey() + ".");
                }
            }
            putMetadata("legacy_data_yml_migrated", "true");
            putMetadata("legacy_data_yml_schema_version",
                    Integer.toString(sourceSchemaVersion));
            putMetadata("legacy_data_yml_record_count",
                    Integer.toString(expected.size()));
            putMetadata("legacy_data_yml_sha256", sourceChecksum);
            putMetadata("legacy_data_yml_backup", backupFileName);
            putMetadata("legacy_data_yml_migrated_at", Long.toString(Instant.now().toEpochMilli()));
            connection.commit();
            return true;
        } catch (SQLException | RuntimeException exception) {
            rollback(exception);
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    synchronized long recordCount() throws SQLException {
        requireOpen();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM tool_instances")) {
            return result.next() ? result.getLong(1) : 0L;
        }
    }

    synchronized boolean migrationComplete() throws SQLException {
        return "true".equalsIgnoreCase(metadata("legacy_data_yml_migrated"));
    }

    synchronized Path backup(Path destination) throws SQLException, IOException {
        requireOpen();
        checkpoint(true);
        Files.createDirectories(destination.toAbsolutePath().normalize().getParent());
        return Files.copy(file, destination, StandardCopyOption.COPY_ATTRIBUTES);
    }

    synchronized void checkpoint(boolean truncate) throws SQLException {
        requireOpen();
        if (!"wal".equals(journalMode)) {
            return;
        }
        String mode = truncate ? "TRUNCATE" : "PASSIVE";
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA wal_checkpoint(" + mode + ")")) {
            if (result.next() && result.getInt(1) != 0) {
                throw new SQLException("SQLite WAL checkpoint remained busy.");
            }
        }
    }

    Path file() {
        return file;
    }

    synchronized String journalMode() {
        return journalMode;
    }

    synchronized int schemaVersion() throws SQLException {
        requireOpen();
        return queryInt("PRAGMA user_version");
    }

    @Override
    public synchronized void close() throws SQLException {
        if (connection == null) {
            return;
        }
        SQLException failure = null;
        try {
            if (!connection.isClosed()) {
                try {
                    checkpoint(true);
                } catch (SQLException exception) {
                    failure = exception;
                }
                try {
                    connection.close();
                } catch (SQLException exception) {
                    if (failure == null) {
                        failure = exception;
                    } else {
                        failure.addSuppressed(exception);
                    }
                }
            }
        } finally {
            connection = null;
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void configureConnection() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = " + busyTimeoutMillis);
            try (ResultSet result = statement.executeQuery("PRAGMA journal_mode = WAL")) {
                journalMode = result.next()
                        ? result.getString(1).toLowerCase(Locale.ROOT)
                        : "unknown";
            }
            statement.execute("PRAGMA synchronous = "
                    + ("wal".equals(journalMode) ? "NORMAL" : "FULL"));
            statement.execute("PRAGMA wal_autocheckpoint = " + walAutoCheckpointPages);
        }
    }

    private void initializeSchema() throws SQLException {
        int currentVersion = queryInt("PRAGMA user_version");
        if (currentVersion > SCHEMA_VERSION) {
            throw new SQLException("Database schema " + currentVersion
                    + " is newer than supported schema " + SCHEMA_VERSION + ".");
        }
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS schema_metadata (
                        metadata_key TEXT PRIMARY KEY,
                        metadata_value TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS players (
                        player_uuid TEXT PRIMARY KEY,
                        cached_name TEXT NOT NULL,
                        first_seen_at INTEGER NOT NULL CHECK(first_seen_at >= 0),
                        updated_at INTEGER NOT NULL CHECK(updated_at >= 0)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS tool_instances (
                        instance_uuid TEXT PRIMARY KEY,
                        tool_id TEXT NOT NULL COLLATE NOCASE,
                        category_id TEXT NOT NULL DEFAULT '',
                        owner_uuid TEXT NOT NULL,
                        bound_world TEXT NOT NULL COLLATE NOCASE,
                        level INTEGER NOT NULL CHECK(level >= 1),
                        progress INTEGER NOT NULL CHECK(progress >= 0),
                        active INTEGER NOT NULL CHECK(active IN (0, 1)),
                        menu_managed INTEGER NOT NULL CHECK(menu_managed IN (0, 1)),
                        lifetime INTEGER NOT NULL CHECK(lifetime >= 0),
                        created_at INTEGER NOT NULL CHECK(created_at >= 0),
                        updated_at INTEGER NOT NULL CHECK(updated_at >= 0),
                        FOREIGN KEY(owner_uuid) REFERENCES players(player_uuid)
                            ON UPDATE CASCADE ON DELETE RESTRICT
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS target_progress (
                        instance_uuid TEXT NOT NULL,
                        target TEXT NOT NULL COLLATE NOCASE,
                        progress INTEGER NOT NULL CHECK(progress >= 1),
                        PRIMARY KEY(instance_uuid, target),
                        FOREIGN KEY(instance_uuid) REFERENCES tool_instances(instance_uuid)
                            ON UPDATE CASCADE ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_instances_owner
                    ON tool_instances(owner_uuid)
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_instances_tool
                    ON tool_instances(tool_id)
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_instances_owner_tool_world
                    ON tool_instances(owner_uuid, tool_id, bound_world)
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_instances_active_owner_world
                    ON tool_instances(active, owner_uuid, bound_world)
                    """);
            statement.execute("PRAGMA user_version = " + SCHEMA_VERSION);
            putMetadata("schema_version", Integer.toString(SCHEMA_VERSION));
            if (metadata("created_at") == null) {
                putMetadata("created_at", Long.toString(Instant.now().toEpochMilli()));
            }
            connection.commit();
        } catch (SQLException | RuntimeException exception) {
            rollback(exception);
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private void verifyIntegrity() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA integrity_check")) {
            List<String> failures = new ArrayList<>();
            while (result.next()) {
                String message = result.getString(1);
                if (!"ok".equalsIgnoreCase(message)) {
                    failures.add(message);
                }
            }
            if (!failures.isEmpty()) {
                throw new SQLException("SQLite integrity check failed: " + String.join("; ", failures));
            }
        }
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA foreign_key_check")) {
            List<String> failures = new ArrayList<>();
            while (result.next()) {
                failures.add(result.getString("table") + " row " + result.getLong("rowid")
                        + " references missing parent " + result.getString("parent"));
            }
            if (!failures.isEmpty()) {
                throw new SQLException("SQLite foreign-key check failed: "
                        + String.join("; ", failures));
            }
        }
    }

    private void writeRecords(Collection<InstanceRegistry.InstanceRecord> records)
            throws SQLException {
        try (PreparedStatement players = connection.prepareStatement(UPSERT_PLAYER);
             PreparedStatement instances = connection.prepareStatement(UPSERT_INSTANCE);
             PreparedStatement deleteTargets = connection.prepareStatement(DELETE_TARGETS);
             PreparedStatement insertTargets = connection.prepareStatement(INSERT_TARGET)) {
            for (InstanceRegistry.InstanceRecord record : records) {
                players.setString(1, record.ownerId().toString());
                players.setString(2, record.ownerName());
                players.setLong(3, record.createdAt());
                players.setLong(4, record.updatedAt());
                players.addBatch();

                instances.setString(1, record.instanceId().toString());
                instances.setString(2, record.toolId());
                instances.setString(3, record.categoryId());
                instances.setString(4, record.ownerId().toString());
                instances.setString(5, record.boundWorld());
                instances.setInt(6, record.level());
                instances.setLong(7, record.progress());
                instances.setInt(8, record.active() ? 1 : 0);
                instances.setInt(9, record.menuManaged() ? 1 : 0);
                instances.setLong(10, record.lifetime());
                instances.setLong(11, record.createdAt());
                instances.setLong(12, record.updatedAt());
                instances.addBatch();

                deleteTargets.setString(1, record.instanceId().toString());
                deleteTargets.addBatch();
            }
            players.executeBatch();
            instances.executeBatch();
            deleteTargets.executeBatch();

            for (InstanceRegistry.InstanceRecord record : records) {
                for (Map.Entry<String, Long> target : record.targetProgress().entrySet()) {
                    insertTargets.setString(1, record.instanceId().toString());
                    insertTargets.setString(2, target.getKey());
                    insertTargets.setLong(3, target.getValue());
                    insertTargets.addBatch();
                }
            }
            insertTargets.executeBatch();
        }
    }

    private String metadata(String key) throws SQLException {
        requireOpen();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT metadata_value FROM schema_metadata WHERE metadata_key = ?
                """)) {
            statement.setString(1, key);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString(1) : null;
            }
        }
    }

    private void putMetadata(String key, String value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO schema_metadata(metadata_key, metadata_value)
                VALUES (?, ?)
                ON CONFLICT(metadata_key) DO UPDATE SET metadata_value = excluded.metadata_value
                """)) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
        }
    }

    private int queryInt(String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            if (!result.next()) {
                throw new SQLException("SQLite returned no result for: " + sql);
            }
            return result.getInt(1);
        }
    }

    private void rollback(Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            original.addSuppressed(rollbackException);
        }
    }

    private void requireOpen() throws SQLException {
        if (connection == null || connection.isClosed()) {
            throw new SQLException("The PlexonTools database is not open.");
        }
    }

    private static UUID parseUuid(String value, String field) throws SQLException {
        try {
            return UUID.fromString(requireText(value, field));
        } catch (IllegalArgumentException exception) {
            throw new SQLException("Invalid " + field + ": " + value, exception);
        }
    }

    private static String requireText(String value, String field) throws SQLException {
        if (value == null || value.isBlank()) {
            throw new SQLException("Missing " + field + " in PlexonTools database.");
        }
        return value;
    }

    private static boolean samePersistentState(
            InstanceRegistry.InstanceRecord expected,
            InstanceRegistry.InstanceRecord actual
    ) {
        return actual != null
                && expected.instanceId().equals(actual.instanceId())
                && expected.toolId().equals(actual.toolId())
                && expected.categoryId().equals(actual.categoryId())
                && expected.ownerId().equals(actual.ownerId())
                && expected.boundWorld().equals(actual.boundWorld())
                && expected.level() == actual.level()
                && expected.progress() == actual.progress()
                && expected.targetProgress().equals(actual.targetProgress())
                && expected.active() == actual.active()
                && expected.menuManaged() == actual.menuManaged()
                && expected.lifetime() == actual.lifetime()
                && expected.createdAt() == actual.createdAt()
                && expected.updatedAt() == actual.updatedAt();
    }
}
