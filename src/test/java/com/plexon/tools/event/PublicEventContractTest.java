package com.plexon.tools.event;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PublicEventContractTest {
    @Test
    void questsRequiredEventClassesAndAccessorsAreStable() throws Exception {
        Class<?> progress = Class.forName("com.plexon.tools.event.PlexonToolProgressEvent");
        Class<?> level = Class.forName("com.plexon.tools.event.PlexonToolLevelUpEvent");

        assertTrue(PlayerEvent.class.isAssignableFrom(progress));
        assertTrue(PlayerEvent.class.isAssignableFrom(level));
        assertFalse(Cancellable.class.isAssignableFrom(progress));
        assertFalse(Cancellable.class.isAssignableFrom(level));

        methods(progress,
                "getPlayer", "player", "toolId", "getToolId", "amount", "delta",
                "progressDelta", "getAmount", "level", "newLevel", "getLevel",
                "toolCategory", "getToolCategory", "category", "progressType",
                "getProgressType", "type", "material", "getMaterial", "eventId",
                "getEventId", "transactionId", "getTransactionId", "toolInstanceId",
                "getToolInstanceId", "getHandlers", "getHandlerList");
        methods(level,
                "getPlayer", "player", "toolId", "getToolId", "oldLevel", "getOldLevel",
                "newLevel", "getNewLevel", "toolCategory", "getToolCategory", "category",
                "eventId", "getEventId", "transactionId", "getTransactionId",
                "toolInstanceId", "getToolInstanceId", "world", "getWorld",
                "getHandlers", "getHandlerList");

        assertTrue(Modifier.isStatic(progress.getMethod("getHandlerList").getModifiers()));
        assertTrue(Modifier.isStatic(level.getMethod("getHandlerList").getModifiers()));
        assertTrue(HandlerList.class.isAssignableFrom(progress.getMethod("getHandlerList").getReturnType()));
        assertTrue(HandlerList.class.isAssignableFrom(level.getMethod("getHandlerList").getReturnType()));

        progress.getConstructor(Player.class, String.class, String.class, String.class,
                long.class, int.class, Material.class, String.class, String.class, UUID.class);
        level.getConstructor(Player.class, String.class, String.class, int.class, int.class,
                String.class, String.class, UUID.class, String.class);
    }

    private static void methods(Class<?> type, String... names) throws Exception {
        for (String name : names) {
            Method ignored = type.getMethod(name);
            assertTrue(Modifier.isPublic(ignored.getModifiers()), name + " must remain public");
        }
    }
}
