package com.plexon.tools.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class ToolProgressListenerTest {
    @Test
    void monitorHandlersAlwaysCleanUpCachedEventContexts() throws NoSuchMethodException {
        assertCleanupMonitor("onBlockBreakAbilities", BlockBreakEvent.class);
        assertCleanupMonitor("onDamageResolved", EntityDamageByEntityEvent.class);
    }

    private static void assertCleanupMonitor(String methodName, Class<?> eventType)
            throws NoSuchMethodException {
        Method method = ToolProgressListener.class.getDeclaredMethod(methodName, eventType);
        EventHandler handler = method.getAnnotation(EventHandler.class);
        assertNotNull(handler, () -> methodName + " must remain a Bukkit event handler");
        assertEquals(EventPriority.MONITOR, handler.priority());
        assertFalse(handler.ignoreCancelled(),
                () -> methodName + " must remove cached context even after another plugin cancels");
    }
}
