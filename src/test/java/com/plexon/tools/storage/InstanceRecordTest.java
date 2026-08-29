package com.plexon.tools.storage;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstanceRecordTest {
    @Test
    void reconstructsTheExactPersistentToolState() {
        UUID instanceId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        InstanceRegistry.InstanceRecord record = new InstanceRegistry.InstanceRecord(
                instanceId, "magma_breaker", "mining", ownerId, "Tonim", "world",
                2, 125L, Map.of("STONE", 80L), true, true, 500L, 10L, 20L);

        var state = record.state();
        assertTrue(record.active());
        assertTrue(record.menuManaged());
        assertEquals(instanceId, state.instanceId());
        assertEquals(ownerId, state.ownerId());
        assertEquals("magma_breaker", state.toolId());
        assertEquals("world", state.boundWorld());
        assertEquals(2, state.level());
        assertEquals(125L, state.progress());
        assertEquals(80L, state.targetProgress().get("STONE"));
    }
}
