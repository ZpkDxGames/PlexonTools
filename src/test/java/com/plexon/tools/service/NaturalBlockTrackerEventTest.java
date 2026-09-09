package com.plexon.tools.service;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class NaturalBlockTrackerEventTest {
    @Test
    void cleanupRunsAtMonitorEvenForCancelledEvents() throws Exception {
        Method cleanup = NaturalBlockTracker.class.getDeclaredMethod(
                "onBreakCleanup", BlockBreakEvent.class);
        EventHandler cleanupHandler = cleanup.getAnnotation(EventHandler.class);
        assertNotNull(cleanupHandler);
        assertEquals(EventPriority.MONITOR, cleanupHandler.priority());
        assertFalse(cleanupHandler.ignoreCancelled(),
                "cleanup must remove a consumed event context after late cancellation");
    }
}
