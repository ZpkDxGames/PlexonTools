package com.plexon.tools.storage;

import org.bukkit.configuration.InvalidConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RegistryDatabaseTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsWalSchemaAndPersistsExactToolState() throws Exception {
        Path file = temporaryDirectory.resolve("plexontools.db");
        UUID instanceId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        InstanceRegistry.InstanceRecord initial = record(
                instanceId, ownerId, 4, 125L, Map.of("STONE", 125L), true, 500L);

        try (RegistryDatabase database = database(file)) {
            database.open();
            assertEquals(RegistryDatabase.SCHEMA_VERSION, database.schemaVersion());
            assertEquals("wal", database.journalMode());
            database.upsert(List.of(initial));
            assertEquals(initial, database.loadAll().get(instanceId));

            InstanceRegistry.InstanceRecord updated = record(
                    instanceId, ownerId, 5, 0L, Map.of(), false, 625L);
            database.upsert(List.of(updated));
            assertEquals(updated, database.loadAll().get(instanceId));
        }

        try (RegistryDatabase reopened = database(file)) {
            reopened.open();
            assertEquals(1L, reopened.recordCount());
            assertEquals(5, reopened.loadAll().get(instanceId).level());
        }

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     SELECT COUNT(*) FROM sqlite_master
                     WHERE type = 'index' AND name IN (
                         'idx_instances_owner',
                         'idx_instances_tool',
                         'idx_instances_owner_tool_world',
                         'idx_instances_active_owner_world'
                     )
                     """)) {
            assertTrue(result.next());
            assertEquals(4, result.getInt(1));
        }
    }

    @Test
    void importsLegacyYamlOnceAndPreservesBackup() throws Exception {
        UUID instanceId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Path yaml = temporaryDirectory.resolve("data.yml");
        Files.writeString(yaml, """
                schema_version: 4
                instances:
                  %s:
                    tool: legendary_pickaxe
                    category: mining
                    owner: %s
                    owner_name: Tonim
                    bound_world: Survival_World
                    level: 9
                    progress: 42
                    target_progress:
                      STONE: 42
                    active: true
                    menu_managed: true
                    lifetime: 900
                    created_at: 100
                    updated_at: 200
                """.formatted(instanceId, ownerId));

        LegacyYamlRegistry.Snapshot snapshot = LegacyYamlRegistry.read(yaml);
        Path backup = LegacyYamlRegistry.createBackup(yaml);
        assertTrue(Files.exists(backup));
        assertEquals(Files.readString(yaml), Files.readString(backup));

        try (RegistryDatabase database = database(
                temporaryDirectory.resolve("plexontools.db"))) {
            database.open();
            assertTrue(database.importLegacy(snapshot.records(), snapshot.schemaVersion(),
                    snapshot.sha256(),
                    backup.getFileName().toString()));
            assertFalse(database.importLegacy(snapshot.records(), snapshot.schemaVersion(),
                    snapshot.sha256(),
                    backup.getFileName().toString()));
            assertTrue(database.migrationComplete());
            assertEquals(1L, database.recordCount());

            InstanceRegistry.InstanceRecord imported = database.loadAll().get(instanceId);
            assertEquals(ownerId, imported.ownerId());
            assertEquals(9, imported.level());
            assertEquals(42L, imported.progress());
            assertEquals(Map.of("STONE", 42L), imported.targetProgress());
            assertTrue(imported.active());
            assertTrue(imported.menuManaged());
        }
    }

    @Test
    void rejectsInvalidLegacyYamlWithoutCreatingDatabase() throws Exception {
        Path yaml = temporaryDirectory.resolve("data.yml");
        Files.writeString(yaml, """
                schema_version: 4
                instances:
                  not-a-uuid:
                    tool: legendary_pickaxe
                """);

        assertThrows(InvalidConfigurationException.class,
                () -> LegacyYamlRegistry.read(yaml));
        assertFalse(Files.exists(temporaryDirectory.resolve("plexontools.db")));
    }

    @Test
    void rejectsMalformedLegacyFieldTypes() throws Exception {
        Path yaml = temporaryDirectory.resolve("data.yml");
        Files.writeString(yaml, """
                schema_version: 4
                instances:
                  %s:
                    tool: legendary_pickaxe
                    owner: %s
                    bound_world: Survival_World
                    target_progress:
                      - STONE
                """.formatted(UUID.randomUUID(), UUID.randomUUID()));

        InvalidConfigurationException exception = assertThrows(
                InvalidConfigurationException.class, () -> LegacyYamlRegistry.read(yaml));
        assertTrue(exception.getMessage().contains(
                "target_progress must be a YAML section"));
    }

    @Test
    void createsCheckpointedDatabaseBackup() throws Exception {
        Path file = temporaryDirectory.resolve("plexontools.db");
        UUID instanceId = UUID.randomUUID();
        try (RegistryDatabase database = database(file)) {
            database.open();
            database.upsert(List.of(record(instanceId, UUID.randomUUID(),
                    2, 20L, Map.of("STONE", 20L), true, 20L)));
            Path backup = database.backup(
                    temporaryDirectory.resolve("backups").resolve("manual.db"));
            assertTrue(Files.exists(backup));
        }

        try (RegistryDatabase backup = database(
                temporaryDirectory.resolve("backups").resolve("manual.db"))) {
            backup.open();
            assertEquals(1L, backup.recordCount());
            assertTrue(backup.loadAll().containsKey(instanceId));
        }
    }

    @Test
    void rejectsDatabaseFromANewerSchema() throws Exception {
        Path file = temporaryDirectory.resolve("future.db");
        Class.forName("org.sqlite.JDBC");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA user_version = "
                    + (RegistryDatabase.SCHEMA_VERSION + 1));
        }

        try (RegistryDatabase database = database(file)) {
            SQLException exception = assertThrows(SQLException.class, database::open);
            assertTrue(exception.getMessage().contains("newer than supported"));
        }
    }

    @Test
    void rejectsForeignKeyCorruptionAtStartup() throws Exception {
        Path file = temporaryDirectory.resolve("corrupt.db");
        try (RegistryDatabase database = database(file)) {
            database.open();
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = OFF");
            statement.execute("""
                    INSERT INTO tool_instances(
                        instance_uuid, tool_id, category_id, owner_uuid, bound_world,
                        level, progress, active, menu_managed, lifetime, created_at, updated_at
                    ) VALUES (
                        '00000000-0000-0000-0000-000000000001', 'legendary_pickaxe',
                        'mining', '00000000-0000-0000-0000-000000000002', 'Survival_World',
                        1, 0, 1, 1, 0, 100, 200
                    )
                    """);
        }

        try (RegistryDatabase database = database(file)) {
            SQLException exception = assertThrows(SQLException.class, database::open);
            assertTrue(exception.getMessage().contains("foreign-key check failed"));
        }
    }

    @Test
    void recoversCommittedWalStateAfterAbruptProcessHalt() throws Exception {
        Path file = temporaryDirectory.resolve("abrupt.db");
        UUID instanceId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        String classpath = System.getProperty(
                "plexontools.test-classpath", System.getProperty("java.class.path"));
        String executable = System.getProperty("os.name", "")
                .toLowerCase(java.util.Locale.ROOT).contains("win") ? "java.exe" : "java";
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin", executable)
                .toString();
        Process process = new ProcessBuilder(
                javaExecutable,
                "-cp",
                classpath,
                AbruptDatabaseWriter.class.getName(),
                file.toString(),
                instanceId.toString(),
                ownerId.toString())
                .redirectErrorStream(true)
                .start();
        int exitCode = process.waitFor();
        String output = new String(process.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, exitCode, output);

        try (RegistryDatabase recovered = database(file)) {
            recovered.open();
            InstanceRegistry.InstanceRecord record = recovered.loadAll().get(instanceId);
            assertNotNull(record);
            assertEquals(ownerId, record.ownerId());
            assertEquals(7, record.level());
            assertEquals(321L, record.progress());
            assertEquals(Map.of("STONE", 321L), record.targetProgress());
            assertEquals(654L, record.lifetime());
        }
    }

    private static RegistryDatabase database(Path file) {
        return new RegistryDatabase(file, 5000, 1000, true);
    }

    private static InstanceRegistry.InstanceRecord record(
            UUID instanceId,
            UUID ownerId,
            int level,
            long progress,
            Map<String, Long> targets,
            boolean active,
            long lifetime
    ) {
        return new InstanceRegistry.InstanceRecord(
                instanceId, "legendary_pickaxe", "mining", ownerId, "Tonim",
                "Survival_World", level, progress, targets, active, true,
                lifetime, 100L, 200L);
    }
}
