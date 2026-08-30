package com.plexon.tools.storage;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Process helper that deliberately exits without closing its SQLite connection. */
public final class AbruptDatabaseWriter {
    private AbruptDatabaseWriter() {
    }

    public static void main(String[] args) throws Exception {
        Path file = Path.of(args[0]);
        UUID instanceId = UUID.fromString(args[1]);
        UUID ownerId = UUID.fromString(args[2]);
        RegistryDatabase database = new RegistryDatabase(file, 5000, 1000, true);
        database.open();
        database.upsert(List.of(new InstanceRegistry.InstanceRecord(
                instanceId, "legendary_pickaxe", "mining", ownerId, "Tonim",
                "Survival_World", 7, 321L, Map.of("STONE", 321L),
                true, true, 654L, 100L, 200L)));
        Runtime.getRuntime().halt(0);
    }
}
