package com.plexon.tools.gui;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
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

    @Test
    void guardedExternalInventoryOpensAreBlockedAtHighestPriority()
            throws NoSuchMethodException {
        Method method = GuiManager.class.getDeclaredMethod(
                "onOpen", InventoryOpenEvent.class);
        EventHandler handler = method.getAnnotation(EventHandler.class);
        assertNotNull(handler, "onOpen must remain a Bukkit event handler");
        assertEquals(EventPriority.HIGHEST, handler.priority());
    }

    @Test
    void plexonGuiSessionGuardIsReleasedOnClose() throws NoSuchMethodException {
        Method method = GuiManager.class.getDeclaredMethod(
                "onClose", InventoryCloseEvent.class);
        assertNotNull(method.getAnnotation(EventHandler.class),
                "onClose must retain GUI-session cleanup");
    }

    @Test
    void plexonNavigationDoesNotUseTheGenericArrowMaterial() {
        assertEquals(Material.SPECTRAL_ARROW, GuiManager.navigationMaterial());
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
