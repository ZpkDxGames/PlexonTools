package com.plexon.tools.event;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

import java.util.Objects;
import java.util.UUID;

/** Fired once after an accepted PlexonTools progression mutation is committed. */
public final class PlexonToolProgressEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    private final String toolId;
    private final String toolCategory;
    private final String progressType;
    private final long amount;
    private final int level;
    private final Material material;
    private final String eventId;
    private final String transactionId;
    private final UUID toolInstanceId;

    public PlexonToolProgressEvent(
            Player player,
            String toolId,
            String toolCategory,
            String progressType,
            long amount,
            int level,
            Material material,
            String eventId,
            String transactionId,
            UUID toolInstanceId
    ) {
        super(Objects.requireNonNull(player, "player"));
        this.toolId = required(toolId, "toolId");
        this.toolCategory = required(toolCategory, "toolCategory");
        this.progressType = required(progressType, "progressType");
        if (amount <= 0L) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (level < 1) {
            throw new IllegalArgumentException("level must be positive");
        }
        this.amount = amount;
        this.level = level;
        this.material = material;
        this.eventId = required(eventId, "eventId");
        this.transactionId = required(transactionId, "transactionId");
        this.toolInstanceId = Objects.requireNonNull(toolInstanceId, "toolInstanceId");
    }

    public Player player() { return getPlayer(); }
    public String toolId() { return toolId; }
    public String getToolId() { return toolId; }
    public String toolCategory() { return toolCategory; }
    public String getToolCategory() { return toolCategory; }
    public String category() { return toolCategory; }
    public String progressType() { return progressType; }
    public String getProgressType() { return progressType; }
    public String type() { return progressType; }
    public long amount() { return amount; }
    public long delta() { return amount; }
    public long progressDelta() { return amount; }
    public long getAmount() { return amount; }
    public int level() { return level; }
    public int newLevel() { return level; }
    public int getLevel() { return level; }
    public Material material() { return material; }
    public Material getMaterial() { return material; }
    public String eventId() { return eventId; }
    public String getEventId() { return eventId; }
    public String transactionId() { return transactionId; }
    public String getTransactionId() { return transactionId; }
    public UUID toolInstanceId() { return toolInstanceId; }
    public UUID getToolInstanceId() { return toolInstanceId; }

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
