package com.plexon.tools.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class GuiIsolationTest {
    @Test
    void plexonInventoryEventsAreClaimedAtLowestPriority() throws NoSuchMethodException {
        assertLowest("onClick", InventoryClickEvent.class);
        assertLowest("onDrag", InventoryDragEvent.class);
    }

    private static void assertLowest(String methodName, Class<?> eventType)
            throws NoSuchMethodException {
        Method method = GuiManager.class.getDeclaredMethod(methodName, eventType);
        EventHandler handler = method.getAnnotation(EventHandler.class);
        assertNotNull(handler, () -> methodName + " must remain a Bukkit event handler");
        assertEquals(EventPriority.LOWEST, handler.priority(),
                () -> methodName + " must cancel PlexonTools GUI input before other plugins see it");
    }
}
