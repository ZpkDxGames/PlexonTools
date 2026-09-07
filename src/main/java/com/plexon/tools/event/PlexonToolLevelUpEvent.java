package com.plexon.tools.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

import java.util.Objects;
import java.util.UUID;

/** Fired once after a real PlexonTools level transition is committed. */
public final class PlexonToolLevelUpEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    private final String toolId;
    private final String toolCategory;
    private final int oldLevel;
    private final int newLevel;
    private final String eventId;
    private final String transactionId;
    private final UUID toolInstanceId;
    private final String world;

    public PlexonToolLevelUpEvent(
            Player player,
            String toolId,
            String toolCategory,
            int oldLevel,
            int newLevel,
            String eventId,
            String transactionId,
            UUID toolInstanceId,
            String world
    ) {
        super(Objects.requireNonNull(player, "player"));
        this.toolId = required(toolId, "toolId");
        this.toolCategory = required(toolCategory, "toolCategory");
        if (oldLevel < 1 || newLevel <= oldLevel) {
            throw new IllegalArgumentException("newLevel must be greater than oldLevel");
        }
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
        this.eventId = required(eventId, "eventId");
        this.transactionId = required(transactionId, "transactionId");
        this.toolInstanceId = Objects.requireNonNull(toolInstanceId, "toolInstanceId");
        this.world = world == null ? "" : world;
    }

    public Player player() { return getPlayer(); }
    public String toolId() { return toolId; }
    public String getToolId() { return toolId; }
    public String toolCategory() { return toolCategory; }
    public String getToolCategory() { return toolCategory; }
    public String category() { return toolCategory; }
    public int oldLevel() { return oldLevel; }
    public int getOldLevel() { return oldLevel; }
    public int newLevel() { return newLevel; }
    public int getNewLevel() { return newLevel; }
    public String eventId() { return eventId; }
    public String getEventId() { return eventId; }
    public String transactionId() { return transactionId; }
    public String getTransactionId() { return transactionId; }
    public UUID toolInstanceId() { return toolInstanceId; }
    public UUID getToolInstanceId() { return toolInstanceId; }
    public String world() { return world; }
    public String getWorld() { return world; }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
